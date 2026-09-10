# 授权管理

服务地址：https://license.zjm0929.cn 。运行于 Cloudflare Workers，绑定 D1 数据库 `maisui-license`。

默认永久授权、一个授权码绑定一个安装设备、离线凭证最多七天。应用在有网络时每日尝试复核；断网可使用尚未过期的凭证。解绑或吊销后，联网复核立即拒绝，完全离线的旧凭证最迟七天失效。

## 网页后台（推荐）

打开 https://license.zjm0929.cn/console ，选择本机 `admin-config.json` 管理员配置文件即可登录。也可展开“使用管理员令牌登录”，手动粘贴令牌。凭据仅保存在页面内存，刷新或关闭页面后重新登录。

页面支持生成授权码、复制或下载授权文件、按备注或编号搜索最近 500 份授权、查看绑定设备、解绑和吊销。新授权码只返回一次，生成后请及时保存；已有授权码无法从后台找回。解绑和吊销均需再次确认。

## 开发者操作

需要 Node.js 22 或更新版本。在仓库目录执行下列命令，将 `私有配置.json` 替换为本机管理员配置路径。配置包含 `endpoint` 与 `adminToken`，不得发给客户或提交至 Git。

生成授权码（只返回一次，保存好输出文件）：

```powershell
node tools/license-admin.mjs --config 私有配置.json create --subject 客户备注 --output 新授权码.json
```

把输出文件中的 `code` 单独发给使用者。使用者在应用“配置 → 授权与激活 → 输入授权码”激活。授权码不要公开发布。

查看授权及绑定设备：

```powershell
node tools/license-admin.mjs --config 私有配置.json list
node tools/license-admin.mjs --config 私有配置.json devices 授权编号
```

换机先解绑旧设备，再让使用者在新设备输入原码：

```powershell
node tools/license-admin.mjs --config 私有配置.json unbind 授权编号 设备编号
```

永久吊销（无法恢复，应确认授权编号）：

```powershell
node tools/license-admin.mjs --config 私有配置.json revoke 授权编号
```

可选 `--devices 2` 调整设备数，或 `--expires 2027-12-31T23:59:59+08:00` 设置到期时间。未指定则一台设备、永久有效。卸载应用或清除数据会生成新设备编号，需要解绑原设备。

## 保存与恢复

签名私钥、授权码哈希密钥、管理员令牌已作为 Worker Secrets 部署。保管本机私有凭据的安全副本；仅拥有 D1 数据库无法恢复明文授权码。公钥可公开，私钥不能进入 APK、源码压缩包或公开仓库。

旅行备份不包含授权凭据。旧版离线签名授权保持兼容；新签发的云授权适用以上规则。客户端完全受使用者控制，签名授权不能保证抵抗修改过的应用。

服务接口、数据库迁移和测试说明见 [服务文档](server/license/README.md)。

重复激活时若网络中断、响应丢失，后续验证可能提示设备凭据无效。请在原设备重新输入原授权码恢复。
