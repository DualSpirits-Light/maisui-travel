import test from 'node:test';
import assert from 'node:assert/strict';
import { Script } from 'node:vm';
import { adminAsset, formatLocalDateTime, script } from '../src/admin-page.js';
import { handleRequest } from '../src/index.js';

test('console serves public shell with restricted resources and no credential storage',async()=>{
  const response=await handleRequest(new Request('https://example.com/console'),{});
  assert.equal(response.status,200);
  assert.match(response.headers.get('content-security-policy'),/connect-src 'self'/);
  assert.match(response.headers.get('content-security-policy'),/frame-ancestors 'none'/);
  const html=await response.text();
  assert.match(html,/id="workspace" hidden/);
  assert.match(html,/\/console\/app.js/);
  assert.equal(adminAsset('/console/missing'),null);
  assert.doesNotMatch(script,/localStorage|sessionStorage|innerHTML/);
  assert.match(html,/查看归档/);
  assert.match(html,/新版离线天数/);
  assert.match(script,/永久删除/);
  assert.match(script,/unfreeze/);
  new Script(script);
});

test('console assets do not shadow authenticated admin routes',async()=>{
  assert.equal(adminAsset('/admin/licenses'),null);
  for(const path of ['/console/app.js','/console/style.css']) {
    const response=await handleRequest(new Request('https://example.com'+path),{});
    assert.equal(response.status,200);
    assert.equal(response.headers.get('cache-control'),'no-store');
  }
});

test('expiry editor formats the existing instant as local time without a UTC shift',()=>{
  const epochSeconds=1_788_707_645;
  const formatted=formatLocalDateTime(epochSeconds,'Asia/Shanghai');
  assert.equal(formatted,'2026-09-06T23:14:05');
  assert.equal(Date.parse(formatted+'+08:00')/1000,epochSeconds);
  assert.doesNotMatch(script,/toISOString\(\)\.slice/);
});
