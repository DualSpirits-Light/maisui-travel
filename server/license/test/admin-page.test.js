import test from 'node:test';
import assert from 'node:assert/strict';
import { Script } from 'node:vm';
import { adminAsset, formatLocalDateTime, script } from '../src/admin-page.js';
import { handleRequest } from '../src/index.js';
import { runInNewContext } from 'node:vm';

test('portal password login uses protected cookie requests and account navigation changes panels', async () => {
  const elements = new Map();
  function element(key) { if (!elements.has(key)) elements.set(key, { value: '', hidden: true, textContent: '', elements: { namedItem: element }, classList: { toggle() {} }, setAttribute() {}, replaceChildren() {}, append() {}, close() {}, reset() {} }); return elements.get(key); }
  const calls = [];
  const context = { document: { querySelector: element, createElement: () => element(Symbol()) }, window: { addEventListener() {} }, location: { origin: 'https://example.com' }, fetch: async (url, options) => { calls.push({ url, options }); return { ok: true, json: async () => url.endsWith('licenses') ? { licenses: [] } : { authenticated: true } }; }, setTimeout, URL, Blob, Date, FormData, console };
  runInNewContext(script, context);
  await new Promise(resolve => setTimeout(resolve, 0));
  element('#password').value = 'a secure admin password';
  element('#login-form').onsubmit({ preventDefault() {}, submitter: element('#login-submit') });
  await new Promise(resolve => setTimeout(resolve, 0));
  const login = calls.find(c => c.url.endsWith('auth/login'));
  assert.ok(login, 'password form must call the login endpoint');
  assert.equal(JSON.parse(login.options.body).password, 'a secure admin password');
  assert.equal(login.options.credentials, 'same-origin');
  assert.equal(element('#password').value, '');
  element('#nav-account').onclick();
  assert.equal(element('#account-panel').hidden, false);
  assert.equal(element('#workspace').hidden, true);
});

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
  assert.match(html,/离线天数/);
  assert.doesNotMatch(html,/旧版离线天数/);
  assert.match(script,/永久删除/);
  assert.match(script,/unfreeze/);
  new Script(script);
});

test('donation form sends explicit privacy flag and refreshes the list after saving', async () => {
  const elements = new Map(), calls = [];
  function element(key) { if (!elements.has(key)) elements.set(key, { value: '', hidden: true, textContent: '', elements: { namedItem: element }, classList: { toggle() {} }, setAttribute() {}, replaceChildren() {}, append() {}, close() {}, reset() {} }); return elements.get(key); }
  const values = new Map(Object.entries({ platform: '支付宝', name: '支持者', amount: '12.50', currency: 'CNY', date: '2026-09-21', message: '感谢', isPublic: 'on' }));
  const context = { document: { querySelector: element, createElement: () => element(Symbol()) }, window: { addEventListener() {} }, location: { origin: 'https://example.com' }, fetch: async (url, options) => { calls.push({ url, options }); return { ok: true, json: async () => url.endsWith('licenses') ? { licenses: [] } : url.endsWith('donations') ? { donations: [] } : { authenticated: true } }; }, setTimeout, URL, Blob, Date, FormData: class { get(key) { return values.get(key) ?? null; } }, console };
  runInNewContext(script, context);
  assert.equal(typeof element('#donation-form').onsubmit, 'function');
  element('#donation-form').onsubmit({ preventDefault() {}, target: element('#donation-form'), submitter: element('#save') });
  await new Promise(resolve => setTimeout(resolve, 0));
  const saved = calls.find(c => c.url === '/admin/donations' && c.options.method === 'POST');
  assert.deepEqual(JSON.parse(saved.options.body), { platform: '支付宝', name: '支持者', amount: '12.50', currency: 'CNY', date: '2026-09-21', message: '感谢', isPublic: true });
  assert.ok(calls.some(c => c.url === '/admin/donations' && c.options.method === 'GET'));
});

test('console assets do not shadow authenticated admin routes',async()=>{
  assert.equal(adminAsset('/admin/licenses'),null);
  for(const path of ['/console/app.js','/console/style.css']) {
    const response=await handleRequest(new Request('https://example.com'+path),{});
    assert.equal(response.status,200);
    assert.equal(response.headers.get('cache-control'),'no-store');
  }
});

test('a delayed list response cannot reopen the workspace after logout', async () => {
  const elements = new Map(); let release;
  function element(key) { if (!elements.has(key)) elements.set(key, { value: '', hidden: true, elements: { namedItem: element }, classList: { toggle() {} }, setAttribute() {}, replaceChildren() {}, append() {}, close() {}, reset() {} }); return elements.get(key); }
  const context = { document: { querySelector: element, createElement: () => element(Symbol()) }, window: { addEventListener() {} }, fetch: async url => url.endsWith('licenses') ? new Promise(resolve => { release = resolve; }) : { ok: true, json: async () => ({ authenticated: true }) }, setTimeout, URL, Blob, Date, FormData, console };
  runInNewContext(script, context);
  await new Promise(resolve => setTimeout(resolve, 0));
  element('#logout').onclick({ target: element('#logout') });
  await new Promise(resolve => setTimeout(resolve, 0));
  release({ ok: true, json: async () => ({ licenses: [] }) });
  await new Promise(resolve => setTimeout(resolve, 0));
  assert.equal(element('#workspace').hidden, true);
  assert.equal(element('#login').hidden, false);
});

test('an expired admin session still clears the private workspace when logout returns 401', async () => {
  const elements = new Map();
  const calls = [];
  function element(key) { if (!elements.has(key)) elements.set(key, { value: '', hidden: true, textContent: '', elements: { namedItem: element }, classList: { toggle() {} }, setAttribute() {}, replaceChildren() {}, append() {}, close() {}, reset() {} }); return elements.get(key); }
  const context = {
    document: { querySelector: element, createElement: () => element(Symbol()) },
    window: { addEventListener() {} },
    location: { origin: 'https://example.com' },
    fetch: async (url, options) => {
      calls.push({ url, options });
      if (url.endsWith('auth/session')) return { ok: true, status: 200, json: async () => ({ authenticated: true }) };
      if (url.endsWith('licenses')) return { ok: true, status: 200, json: async () => ({ licenses: [] }) };
      if (url.endsWith('auth/logout')) return { ok: false, status: 401, json: async () => ({ error: { code: 'UNAUTHORIZED' } }) };
      throw new Error(`unexpected request: ${url}`);
    },
    setTimeout, URL, Blob, Date, FormData, console,
  };
  runInNewContext(script, context);
  await new Promise(resolve => setTimeout(resolve, 0));
  assert.equal(element('#workspace').hidden, false);
  element('#logout').onclick({ target: element('#logout') });
  await new Promise(resolve => setTimeout(resolve, 0));
  assert.ok(calls.some(c => c.url.endsWith('auth/logout')));
  assert.equal(element('#workspace').hidden, true);
  assert.equal(element('#login').hidden, false);
});

test('expiry editor formats the existing instant as local time without a UTC shift',()=>{
  const epochSeconds=1_788_707_645;
  const formatted=formatLocalDateTime(epochSeconds,'Asia/Shanghai');
  assert.equal(formatted,'2026-09-06T23:14:05');
  assert.equal(Date.parse(formatted+'+08:00')/1000,epochSeconds);
  assert.doesNotMatch(script,/toISOString\(\)\.slice/);
});

test('license workspace exposes state-aware recovery, expiry dialog, and audited code flows', async () => {
  const response = adminAsset('/console');
  const html = await response.text();
  assert.match(html, /id="expiry-dialog"/);
  assert.match(html, /常用时长/);
  assert.match(html, /续期/);
  assert.match(script, /restore-use/);
  assert.match(script, /licenses\/'.*'\/code/);
  assert.match(script, /\/reissue/);
  assert.match(script, /历史授权码只保留了不可逆摘要/);
  assert.match(script, /原授权和已绑定设备不会失效/);
  assert.doesNotMatch(script, /prompt\(/);
});

test('archived revoked license can be unarchived without reactivating or restored directly', () => {
  const elements = new Map();
  function node(key) {
    if (!elements.has(key)) elements.set(key, {
      textContent: '', value: '', disabled: false, children: [],
      append(...items) { this.children.push(...items); },
      replaceChildren(...items) { this.children = items; },
      setAttribute() {}, classList: { toggle() {} },
      elements: { namedItem: node }, close() {}, reset() {},
    });
    return elements.get(key);
  }
  const context = {
    document: { querySelector: node, createElement: () => node(Symbol()) },
    window: { addEventListener() {} },
    fetch: () => new Promise(() => {}),
    Date, FormData, setTimeout, URL, Blob, console,
  };
  runInNewContext(script + `\nlicenses=[{id:'license-1',subject:'历史授权',revokedAt:1,archivedAt:2,expiresAt:null,offlineSeconds:0,deviceCount:0,maxDevices:1,codeStatus:'legacy-unavailable'}];archived=true;render();`, context);
  const card = node('#licenses').children[0];
  const actions = card.children.at(-1);
  const buttons = actions.children;
  const restoreUse = buttons.find(button => button.textContent === '恢复使用');
  const restoreDisplay = buttons.find(button => button.textContent === '恢复显示');
  assert.ok(restoreUse);
  assert.equal(restoreUse.disabled, false);
  assert.ok(restoreDisplay);
  assert.equal(buttons.find(button => button.textContent === '编辑期限').disabled, true);
});
