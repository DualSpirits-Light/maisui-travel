import { constantTimeSecretEqual, sha256 } from './crypto.js';

const UPSTREAM = 'https://mcp-open-cater.meituan.com/v1/api/voyage/openapi/query';
const MAX_RESPONSE_BYTES = 262144;

function setting(env, key, fallback, max) {
  const n = Number(env[key]);
  return Number.isSafeInteger(n) && n > 0 && n <= max ? n : fallback;
}

async function boundedJson(response) {
  if (!response.ok || !response.body || Number(response.headers.get('content-length')) > MAX_RESPONSE_BYTES) throw new Error('upstream');
  const reader = response.body.getReader();
  const chunks = []; let size = 0;
  try {
    while (true) {
      const {done,value} = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > MAX_RESPONSE_BYTES) throw new Error('upstream');
      chunks.push(value);
    }
  } finally { await reader.cancel().catch(() => {}); }
  const bytes = new Uint8Array(size); let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
  return JSON.parse(new TextDecoder('utf-8', {fatal:true}).decode(bytes));
}

export function createTravelHandler({readJson, HttpError, json, rateLimit}) {
  const fail = (status, code, message) => { throw new HttpError(status, code, message); };
  return async function travelQuery(request, env) {
    // Route-local errors never expose upstream text, configuration, or request data.
    try {
      await rateLimit(request, env, 'travel', 10);
      let body;
      try { body = await readJson(request); }
      catch (e) { fail(e.status || 400, 'INVALID_REQUEST', '查询格式不正确或内容过长'); }
      const {licenseId,deviceId,deviceSecret} = body;
      if (typeof licenseId !== 'string' || !/^[A-Za-z0-9_-]{8,128}$/u.test(licenseId)
          || typeof deviceId !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/iu.test(deviceId)
          || typeof deviceSecret !== 'string' || !/^[A-Za-z0-9_-]{40,128}$/u.test(deviceSecret)) fail(401,'INVALID_CREDENTIAL','请重新验证应用授权');
      const city = typeof body.city === 'string' ? body.city.trim() : '';
      const query = typeof body.query === 'string' ? body.query.trim() : '';
      if (!city || city.length > 80 || !query || query.length > 1500 || /[\u0000-\u001f\u007f]/u.test(city)) fail(400,'INVALID_REQUEST','请填写城市和不超过1500字的查询');
      const [license, device] = await Promise.all([
        env.DB.prepare('SELECT * FROM licenses WHERE id = ?').bind(licenseId).first(),
        env.DB.prepare('SELECT secret_hash FROM devices WHERE license_id = ? AND device_id = ?').bind(licenseId,deviceId).first(),
      ]);
      const hash = await sha256(deviceSecret);
      if (!license || !device || !await constantTimeSecretEqual(hash,device.secret_hash)) fail(401,'INVALID_CREDENTIAL','请重新验证应用授权');
      const now = Math.floor(Date.now()/1000);
      if (license.revoked_at != null || license.archived_at != null) fail(403,'LICENSE_REVOKED','当前授权不可用');
      if (license.frozen_at != null) fail(403,'LICENSE_FROZEN','当前授权已冻结');
      if (license.expires_at != null && license.expires_at <= now) fail(403,'LICENSE_EXPIRED','当前授权已到期');
      if (typeof env.MEITUAN_TRAVEL_TOKEN !== 'string' || !env.MEITUAN_TRAVEL_TOKEN.trim()) fail(503,'TRAVEL_UNAVAILABLE','旅行查询暂不可用，请稍后重试');
      // UTC daily buckets. Keep past two days so retention never changes today's budget.
      const day = Math.floor(now/86400);
      await env.DB.prepare('DELETE FROM travel_requests WHERE day < ? AND active_until < ?').bind(day-2,now).run();
      const id = crypto.randomUUID();
      const reserved = await env.DB.prepare(`INSERT INTO travel_requests(id,license_id,day,active_until)
        SELECT ?, l.id, ?, ? FROM licenses l JOIN devices d ON d.license_id=l.id
        WHERE l.id=? AND d.device_id=? AND d.secret_hash=?
          AND l.revoked_at IS NULL AND l.archived_at IS NULL AND l.frozen_at IS NULL AND (l.expires_at IS NULL OR l.expires_at>?)
          AND (SELECT count(*) FROM travel_requests WHERE day=?) < ?
          AND (SELECT count(*) FROM travel_requests WHERE day=? AND license_id=?) < ?
          AND (SELECT count(*) FROM travel_requests WHERE active_until>?) < ?
          AND NOT EXISTS (SELECT 1 FROM travel_requests WHERE license_id=? AND active_until>?)
        RETURNING id`).bind(id,day,now+150,licenseId,deviceId,hash,now,
          day,setting(env,'TRAVEL_GLOBAL_DAILY_LIMIT',100,10000),
          day,licenseId,setting(env,'TRAVEL_LICENSE_DAILY_LIMIT',10,1000),
          now,setting(env,'TRAVEL_GLOBAL_CONCURRENCY',4,20),licenseId,now).first();
      if (!reserved) fail(429,'TRAVEL_LIMIT_REACHED','查询较多或今日额度已用完，请稍后再试');
      const controller = new AbortController();
      let timer;
      try {
        const deadline = new Promise((_,reject) => { timer = setTimeout(() => { controller.abort(); reject(new Error('timeout')); },setting(env,'TRAVEL_TIMEOUT_MS',110000,115000)); });
        const result = await Promise.race([ (async () => {
          const response = await fetch(UPSTREAM,{method:'POST',redirect:'error',signal:controller.signal,
            headers:{Authorization:env.MEITUAN_TRAVEL_TOKEN,'Content-Type':'application/json'},
            body:JSON.stringify({city,query,originQuery:query,channel:'meituan-developer'})});
          const value = await boundedJson(response);
          if (value.code !== 0 || typeof value.data !== 'string' || !value.data.trim()) throw new Error('upstream');
          return value.data;
        })(), deadline]);
        return json(200,{content:result,source:'美团旅行'});
      } catch {
        if (controller.signal.aborted) fail(504,'TRAVEL_TIMEOUT','旅行查询超时，请稍后重试');
        fail(502,'TRAVEL_UNAVAILABLE','旅行查询暂不可用，请稍后重试');
      } finally {
        clearTimeout(timer);
        await env.DB.prepare('UPDATE travel_requests SET active_until=0 WHERE id=?').bind(id).run();
      }
    } catch (e) {
      if (e instanceof HttpError) return json(e.status,{error:{code:e.code,message:e.code==='RATE_LIMITED'?'查询过于频繁，请稍后再试':e.message}},e.status===429?{'Retry-After':'60'}:{});
      return json(503,{error:{code:'TRAVEL_UNAVAILABLE',message:'旅行查询暂不可用，请稍后重试'}});
    }
  };
}
