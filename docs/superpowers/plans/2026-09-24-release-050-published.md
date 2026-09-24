# 0.5.0 正式发布记录

状态：用户明确批准后已发布，日期 2026-09-24。发布代码提交 `90e9335`，APK 仍是第十阶段验收的原签名版本 11 / 0.5.0。

## 公开交付

- GitHub Release：https://github.com/DualSpirits-Light/maisui-travel/releases/tag/v0.5.0
- GitHub APK：https://github.com/DualSpirits-Light/maisui-travel/releases/download/v0.5.0/maisui-travel-0.5.0.apk
- Cloudflare APK：https://license.zjm0929.cn/updates/apk/11.apk
- 管理门户：https://license.zjm0929.cn/console
- 更新清单：https://license.zjm0929.cn/updates/latest.json
- Release 包含同一 APK、源码 ZIP 和 SHA256SUMS.txt，三个附件上传后大小与 GitHub 服务端摘要均核验一致。没有恢复此前删除的旧 Releases。

APK：54,145,483 字节；SHA-256 `c407d7c8b3e56c8ddc0e7d2c4b60417fcfb06b462512a0fbc12397ca4f4c0316`。

## 生产执行与验证

- 发布前将 D1 SQL、原 Worker 内容和配置备份到仓库外受限目录 `../../work/license-private/release-050`。保留旧 Worker 版本 `667cb7eb-1c58-420e-904d-8bce2e8d1c3d` 的记录。
- 应用生产迁移 0004–0006；配置独立授权码加密密钥，初始化密码管理员。原 CODE_PEPPER 与签名私钥保留，原有 3 条授权、1 台设备不变。
- 密码初始化后移除了线上旧 ADMIN_TOKEN 与已使用的 ADMIN_RECOVERY_TOKEN。管理员登录信息保存在上述受限目录 `admin-login.json`，不写入仓库、发布附件或普通日志。忘记密码时可按 ADMIN-ACCOUNTS.md 设置新的恢复凭据。
- 真实 HTTPS 19 项检查通过：匿名拒绝、密码登录、原记录读取、新码加密查看、激活与刷新、吊销恢复、私有捐赠隔离、注销。新增验收记录已删除，生产仍为 3 条授权 / 1 台设备 / 0 条捐赠。审计记录保留。
- 生产验收发现 D1 的级联删除计数导致误报 409；使用 DELETE RETURNING 判断授权主行。测试先失败后通过，55 项后端测试通过；生产复验通过。
- 建立 UPDATES R2 桶，上传同一 APK，版本 11 与 SHA-256 自定义元数据正确。为写入元数据使用的临时受保护发布 Worker 已删除，临时路径返回 404。未留下公开上传接口。
- 最终 Worker 版本 `ada069fe-44e1-415a-9255-ab6e1bc4bd67`，域名保持 license.zjm0929.cn，每分钟分享清理任务保持启用。
- 更新清单返回 0.5.0、versionCode 11、GitHub 和 Cloudflare 两个来源及相同摘要。Cloudflare HEAD 返回正确长度，Range 返回 206 和正确片段长度。匿名及旧令牌管理访问均拒绝；最终密码登录读取原三条授权通过。

## 公网下载实测

| 线路 | 完整下载时间 | 大小/摘要 |
| --- | --- | --- |
| Cloudflare | 8.104 秒 | 与发布 APK 完全一致 |
| GitHub 直连 | 5.224 秒 | 与发布 APK 完全一致 |
| gh-proxy.org 加速 | 23.398 秒 | 与发布 APK 完全一致 |

这仅代表执行时本机网络，不保证所有中国大陆运营商相同速度。代理失败的直连回退已通过 Android 回归；用户也可手动选择 Cloudflare。

## 仍需区分的边界

- 第十阶段原签名覆盖升级及正常/小屏大字各 316 项 Android 断言保持有效；本次 APK 未改动。通知与安装交互沿用阶段九模拟器验收，不宣称所有品牌真机均已实测。
- 当前浏览器控制工具两次超时，本轮线上后台以真实 HTTPS 请求完成验收，未把它表述为已完成在线浏览器逐项点击。
- 不同品牌 GPS、相机和外部地图仍需实际设备检查；历史泄露密钥是否撤销不由本轮凭据扫描证明。
- 源码 ZIP、APK 对已保存服务凭据以及本次新管理员密码、恢复凭据、AES 密钥的比对扫描通过。APK 中只保留预期的应用绑定高德 Android Key。

本记录覆盖此前第十阶段检查点的“等待批准/未发布”状态，不修改历史阶段证据。
