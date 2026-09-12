import { hmacSha256 } from './crypto.js';
const MAX_SIZE=32*1024*1024, MAX_STORAGE=2*1024*1024*1024, TTL=72*3600;
const reply=(status,body)=>Response.json(body,{status,headers:{'Cache-Control':'no-store'}});
async function quota(env,key,max,expiry){const r=await env.DB.prepare('INSERT INTO share_limits(bucket,count,expires_at) VALUES (?,1,?) ON CONFLICT(bucket) DO UPDATE SET count=count+1 WHERE count < ? RETURNING count').bind(key,expiry,max).first();return !!r;}
export async function cleanupShares(env,now=Math.floor(Date.now()/1000)) {
 if(!env.SHARES)return;
 const rows=await env.DB.prepare('SELECT id FROM shares WHERE expires_at <= ? ORDER BY expires_at LIMIT 100').bind(now).all();
 for(const row of rows.results||[]){await env.SHARES.delete(row.id);await env.DB.prepare('DELETE FROM shares WHERE id = ? AND expires_at <= ?').bind(row.id,now).run();}
 await env.DB.prepare('DELETE FROM share_limits WHERE expires_at < ?').bind(now).run();
}
export async function shareRequest(request,env,ctx){
 const path=new URL(request.url).pathname;if(!path.startsWith('/v1/shares'))return null;
 if(!env.SHARES)return reply(503,{error:'分享存储暂未开通'});
 const now=Math.floor(Date.now()/1000),month=new Date(now*1000).toISOString().slice(0,7),expiry=now+35*86400;
 if(request.method==='POST'&&path==='/v1/shares'){
  const size=Number(request.headers.get('content-length'));
  if(!Number.isSafeInteger(size)||size<1||size>MAX_SIZE||request.headers.get('content-type')!=='application/zip')return reply(413,{error:'口令文件限 32 MB，请减少照片或使用本地分享'});
  const ip=await hmacSha256(env.CODE_PEPPER,request.headers.get('CF-Connecting-IP')||'local');const day=Math.floor(now/86400);
  if(!await quota(env,'upload:'+ip+':'+day,5,(day+2)*86400)||!await quota(env,'uploads:'+day,100,(day+2)*86400)||!await quota(env,'writes:'+month,5000,expiry))return reply(429,{error:'今日分享次数已达上限，请使用本地分享'});
  const id=crypto.randomUUID().replaceAll('-','');
  const reserved=await env.DB.prepare('INSERT INTO shares(id,size,created_at,expires_at,ready) SELECT ?,?,?,?,0 WHERE (SELECT COALESCE(SUM(size),0) FROM shares)+? <= ?').bind(id,size,now,now+TTL,size,MAX_STORAGE).run();
  if(reserved.meta?.changes!==1)return reply(507,{error:'免费分享空间暂满，请稍后重试或使用本地分享'});
  try {const object=await env.SHARES.put(id,request.body,{httpMetadata:{contentType:'application/zip'}});if(!object||object.size!==size)throw Error('size mismatch');await env.DB.prepare('UPDATE shares SET ready=1 WHERE id=?').bind(id).run();return reply(201,{code:'MS31-'+id,expiresAt:now+TTL,size});}
  catch(e){try{await env.SHARES.delete(id);await env.DB.prepare('DELETE FROM shares WHERE id=?').bind(id).run();}catch{}return reply(503,{error:'上传未完成，请重新生成口令'});}
 }
 const match=path.match(/^\/v1\/shares\/(?:MS31-)?([a-f0-9]{32})$/);
 if(request.method==='GET'&&match){
  // Count all attempts before querying storage, including guessed or expired codes.
  if(!await quota(env,'reads:'+month,100000,expiry))return reply(429,{error:'本月免费分享读取额度已达上限'});
  const row=await env.DB.prepare('SELECT * FROM shares WHERE id=?').bind(match[1]).first();
  if(!row||!row.ready||row.expires_at<=now){if(row&&row.expires_at<=now&&ctx?.waitUntil)ctx.waitUntil(cleanupShares(env,now));return reply(410,{error:'口令不存在或已过期（有效期为 3 天）'});}
  const object=await env.SHARES.get(row.id);if(!object)return reply(410,{error:'分享文件已清理，请重新生成口令'});
  return new Response(object.body,{headers:{'Content-Type':'application/zip','Content-Length':String(row.size),'Cache-Control':'no-store','X-Content-Type-Options':'nosniff','X-Share-Expires':String(row.expires_at)}});
 }
 return reply(404,{error:'分享接口不存在'});
}
