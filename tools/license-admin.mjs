#!/usr/bin/env node
import fs from 'node:fs/promises';
import path from 'node:path';
import {parseArgs} from 'node:util';

const {values,positionals}=parseArgs({allowPositionals:true,options:{
  config:{type:'string'},subject:{type:'string'},output:{type:'string'},
  expires:{type:'string'},devices:{type:'string'},help:{type:'boolean'}
}});
if(values.help||!positionals.length){
  console.log(`麦穗旅序授权管理（Node.js 22+）
  node tools/license-admin.mjs --config 私有配置.json list
  node tools/license-admin.mjs --config 私有配置.json create --subject 客户备注 --output 授权码.json
  node tools/license-admin.mjs --config 私有配置.json devices 授权编号
  node tools/license-admin.mjs --config 私有配置.json unbind 授权编号 设备编号
  node tools/license-admin.mjs --config 私有配置.json revoke 授权编号

私有配置包含 endpoint 和 adminToken。生成的授权码只保存到指定文件，服务端无法找回明文。
默认永久有效、一台设备、七天离线；可用 --expires ISO日期 和 --devices 数量调整单份授权。
请勿把私有配置或生成的授权码提交到公开仓库。`);
  process.exit(0);
}

try{
  if(!values.config)throw new Error('请用 --config 指定私有配置文件');
  const config=JSON.parse(await fs.readFile(values.config,'utf8'));
  const base=new URL(config.endpoint);
  if(base.protocol!=='https:'||base.username||base.password||base.search||base.hash||base.pathname!=='/')throw new Error('服务地址必须为 HTTPS 根地址');
  if(typeof config.adminToken!=='string'||config.adminToken.length<32)throw new Error('管理员凭据缺失');
  const [command,id,deviceId]=positionals;
  const enc=value=>{if(!value||!/^[A-Za-z0-9_-]{8,128}$/.test(value))throw new Error('授权或设备编号无效');return encodeURIComponent(value);};
  let method='GET',route,body,outputHandle;
  if(command==='list')route='/admin/licenses';
  else if(command==='devices')route=`/admin/licenses/${enc(id)}/devices`;
  else if(command==='revoke'){method='POST';route=`/admin/licenses/${enc(id)}/revoke`;}
  else if(command==='unbind'){method='POST';route=`/admin/licenses/${enc(id)}/devices/${enc(deviceId)}/unbind`;}
  else if(command==='create'){
    if(!values.subject?.trim()||!values.output)throw new Error('生成授权需要 --subject 和 --output');
    method='POST';route='/admin/licenses';
    const expiresAt=values.expires?Math.floor(Date.parse(values.expires)/1000):null;
    if(expiresAt!==null&&(!Number.isSafeInteger(expiresAt)||expiresAt<=Date.now()/1000))throw new Error('到期时间必须是有效的未来日期');
    const maxDevices=Number(values.devices??1);
    if(!Number.isInteger(maxDevices)||maxDevices<1||maxDevices>100)throw new Error('设备数必须为 1–100');
    body={subject:values.subject.trim(),expiresAt,maxDevices,leaseDays:7};
    // Reserve the destination before issuing to avoid losing a one-time code to an unwritable path.
    outputHandle=await fs.open(path.resolve(values.output),'wx',0o600);
  }else throw new Error('未知管理命令；使用 --help 查看说明');
  try{
    const response=await fetch(new URL(route,base),{method,redirect:'error',signal:AbortSignal.timeout(30000),
      headers:{Authorization:`Bearer ${config.adminToken}`,'Content-Type':'application/json'},
      ...(body?{body:JSON.stringify(body)}:{})});
    const result=await response.json();
    if(!response.ok)throw new Error(`管理操作失败（${response.status} / ${result.error?.code??'UNKNOWN'}）`);
    if(outputHandle){await outputHandle.writeFile(JSON.stringify(result,null,2)+'\n');console.log(`授权码已保存：${path.resolve(values.output)}`);}
    else if(command==='list')console.table(result.licenses);
    else if(command==='devices')console.table(result.devices);
    else console.log(command==='revoke'?'授权已吊销；已签发的离线凭证最迟七天后失效。':'设备已解绑，可在新设备使用原授权码激活；旧设备最迟七天后失效。');
  }finally{await outputHandle?.close();}
}catch(error){
  // Do not print request headers, configuration, raw response bodies, or credentials.
  console.error(error?.message??'管理操作失败');process.exitCode=1;
}
