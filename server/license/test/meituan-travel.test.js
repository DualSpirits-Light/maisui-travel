import assert from 'node:assert/strict';
import test from 'node:test';
import { DatabaseSync } from 'node:sqlite';
import { readFileSync, readdirSync } from 'node:fs';
import worker from '../src/index.js';
import { sha256 } from '../src/crypto.js';

const credentials = { licenseId: 'license_test_1', deviceId: '12345678-1234-4234-8234-123456789abc', deviceSecret: 'a'.repeat(43) };
async function setup(t) {
  const db = new DatabaseSync(':memory:');
  for (const f of readdirSync(new URL('../migrations/', import.meta.url)).filter(f => f.endsWith('.sql')).sort()) db.exec(readFileSync(new URL('../migrations/' + f, import.meta.url), 'utf8'));
  db.prepare('INSERT INTO licenses(id,code_hmac,subject,max_devices,lease_days,created_at) VALUES(?,?,?,1,7,0)').run(credentials.licenseId, 'hash', 'test');
  db.prepare('INSERT INTO devices VALUES(?,?,?,0,0)').run(credentials.licenseId, credentials.deviceId, await sha256(credentials.deviceSecret));
  const env = { CODE_PEPPER: 'test-pepper', SIGNING_PRIVATE_KEY_PKCS8: 'unused', MEITUAN_TRAVEL_TOKEN: 'server-only-secret', DB: { prepare(sql) { return { bind(...args) { return { first: async () => db.prepare(sql).get(...args) ?? null, run: async () => ({success:true,meta:{changes:db.prepare(sql).run(...args).changes}}) }; } }; } } };
  const original = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url, options) => { calls.push({url,options}); return Response.json({code:0,data:'杭州旅行建议'}); };
  t.after(() => { globalThis.fetch = original; db.close(); });
  const request = (body = {}, headers = {}) => worker.fetch(new Request('https://example.com/v1/travel/query', {method:'POST',headers:{'content-type':'application/json',...headers},body:JSON.stringify({...credentials,city:'杭州',query:'推荐一日游',...body})}),env);
  return {env,db,calls,request};
}
test('authenticated query forwards only the fixed official contract and no device credentials', async t => {
  const {request,calls} = await setup(t); const r = await request();
  assert.equal(r.status,200); assert.deepEqual(await r.json(),{content:'杭州旅行建议',source:'美团旅行'});
  assert.equal(calls[0].url,'https://mcp-open-cater.meituan.com/v1/api/voyage/openapi/query');
  assert.equal(calls[0].options.headers.Authorization,'server-only-secret');
  assert.equal(calls[0].options.redirect,'error');
  assert.deepEqual(JSON.parse(calls[0].options.body),{city:'杭州',query:'推荐一日游',originQuery:'推荐一日游',channel:'meituan-developer'});
});
for (const state of ['revoked_at','frozen_at','expires_at']) test('denies '+state,async t=>{
  const {db,request,calls}=await setup(t);db.exec(`UPDATE licenses SET ${state}=1`);
  assert.equal((await request()).status,403);assert.equal(calls.length,0);
});
test('rejects bad credentials and malformed/oversized input before upstream',async t=>{
  const {request,calls}=await setup(t);
  assert.equal((await request({deviceSecret:'b'.repeat(43)})).status,401);
  assert.equal((await request({city:''})).status,400);
  assert.equal((await request({query:'a'.repeat(1501)})).status,400);
  assert.equal((await request({query:'a'.repeat(5000)})).status,413);
  assert.equal(calls.length,0);
});
test('per-license daily quota includes failed attempts',async t=>{
  const {env,request,calls}=await setup(t);env.TRAVEL_LICENSE_DAILY_LIMIT='1';
  assert.equal((await request()).status,200);assert.equal((await request()).status,429);assert.equal(calls.length,1);
});
test('global daily quota is shared across licenses',async t=>{
  const {env,db,request,calls}=await setup(t);env.TRAVEL_GLOBAL_DAILY_LIMIT='1';
  assert.equal((await request()).status,200);
  db.prepare('INSERT INTO licenses(id,code_hmac,subject,max_devices,lease_days,created_at) VALUES(?,?,?,1,7,0)').run('license_test_2','hash2','test');
  db.prepare('INSERT INTO devices VALUES(?,?,?,0,0)').run('license_test_2',credentials.deviceId,await sha256(credentials.deviceSecret));
  assert.equal((await request({licenseId:'license_test_2'})).status,429);assert.equal(calls.length,1);
});
test('simultaneous same-license query cannot reach upstream twice',async t=>{
  const {request}=await setup(t);let release,entered;const started=new Promise(r=>entered=r);
  globalThis.fetch=async()=>{entered();await new Promise(r=>release=r);return Response.json({code:0,data:'ok'});};
  const first=request();await started;assert.equal((await request()).status,429);release();assert.equal((await first).status,200);
});
test('upstream failures are generic, bounded, consume quota and release lock',async t=>{
  const {request,db}=await setup(t);
  for(const response of [Response.json({code:7,message:'server-only-secret'}),new Response('secret',{status:302}),Response.json({code:0,data:'a'.repeat(270000)})]) {
    globalThis.fetch=async()=>response;const r=await request();assert.equal(r.status,502);assert.doesNotMatch(await r.text(),/server-only-secret/);
    assert.equal(db.prepare('SELECT count(*) AS n FROM travel_requests WHERE active_until>0').get().n,0);
  }
});
test('missing secret fails closed without upstream request',async t=>{
  const {env,request,calls}=await setup(t);delete env.MEITUAN_TRAVEL_TOKEN;assert.equal((await request()).status,503);assert.equal(calls.length,0);
});
test('timeout aborts request without retry and releases concurrency slot',async t=>{
  const {env,request,db}=await setup(t);env.TRAVEL_TIMEOUT_MS='5';let attempts=0,signal;
  globalThis.fetch=async(_url,options)=>{attempts++;signal=options.signal;return new Promise(()=>{});};
  const r=await request();assert.equal(r.status,504);assert.equal((await r.json()).error.code,'TRAVEL_TIMEOUT');
  assert.equal(attempts,1);assert.equal(signal.aborted,true);
  assert.equal(db.prepare('SELECT count(*) AS n FROM travel_requests WHERE active_until>0').get().n,0);
});
test('license revoked after initial lookup still cannot reserve an upstream call',async t=>{
  const {env,db,request,calls}=await setup(t);const original=env.DB.prepare;
  env.DB.prepare=sql=>{if(sql.startsWith('INSERT INTO travel_requests'))db.exec('UPDATE licenses SET revoked_at=1');return original(sql);};
  assert.equal((await request()).status,429);assert.equal(calls.length,0);
});
test('global concurrency slot blocks another license and expires after abandoned request',async t=>{
  const {env,db,request,calls}=await setup(t);env.TRAVEL_GLOBAL_CONCURRENCY='1';
  db.prepare('INSERT INTO travel_requests VALUES(?,?,?,?)').run('abandoned','other-license',Math.floor(Date.now()/86400000),Math.floor(Date.now()/1000)+150);
  assert.equal((await request()).status,429);assert.equal(calls.length,0);
  db.exec('UPDATE travel_requests SET active_until=1');assert.equal((await request()).status,200);
});
test('unsuccessful upstream attempts cannot evade daily budget',async t=>{
  const {env,request}=await setup(t);env.TRAVEL_LICENSE_DAILY_LIMIT='1';let calls=0;
  globalThis.fetch=async()=>{calls++;throw new Error('secret failure details');};
  assert.equal((await request()).status,502);assert.equal((await request()).status,429);assert.equal(calls,1);
});
