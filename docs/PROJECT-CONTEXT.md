# 当前项目上下文

更新时间：2026-09-28。本文件是接手入口；代码与最新用户指示优先于文档，线上状态在部署前重新读取。

## 已发布基线

麦穗旅序 Android：`cn.lvxu.travel`，0.5.0 / versionCode 11，最低 Android 8 / API 26，compile/target 35，Java 17。

- 发布代码：`90e9335`；发布证据记录提交：`ab31e32`。后续文档整理提交不改变 APK。
- [GitHub Release](https://github.com/DualSpirits-Light/maisui-travel/releases/tag/v0.5.0) · [Cloudflare APK](https://license.zjm0929.cn/updates/apk/11.apk) · [管理门户](https://license.zjm0929.cn/console)。
- APK SHA-256：`c407d7c8b3e56c8ddc0e7d2c4b60417fcfb06b462512a0fbc12397ca4f4c0316`；54,145,483 字节。
- 安装证书 SHA-256：`6feea399c67761f33962ffe1ed9181336d391f25c7fbc294aedfaa5382f9e15e`。不要与另一个 maisui-release.jks 混用。
- [完整发布证据及已知边界](superpowers/plans/2026-09-24-release-050-published.md)。前十阶段已交付，旧计划的空复选框不等于仍待开发。

## 架构与已经确定的选择

- Android 使用原生 Java UI；现阶段没有迁移 Kotlin/Compose/Room 的新授权。TripStore 以 SQLite 保存版本化旅行快照；媒体在私有文件目录，偏好与用户服务凭据分别管理。
- 下一版保留高德 Android SDK，取消构建期 Android Key 注入；用户在高级设置加密配置。默认地图入口接原生高德，初始化失败不自动回退 OSM。其他提供商旧行为保持。Web 服务能力使用独立 REST Key，浏览器/Android/Web 服务 Key 不能混用。AI 默认配置不携带开发者服务端 Key；MiniMax 采用已经验证的中国接入地址。未实现 MCP。
- 分享保留本地 ZIP 和三天 Cloudflare 口令；R2 图片是临时分享副本，到期清理不影响用户本地已导入的数据。
- 后端为 Cloudflare Worker + D1 + R2。同一门户管理密码账户、授权和捐赠；管理员初始化后旧 ADMIN_TOKEN 入口停用，线上旧令牌已移除。历史授权码只有 HMAC，不能凭空恢复明文，新码另存 AES-GCM 加密副本。
- 更新来自 GitHub Release（支持加速与直连回退）和 Cloudflare；下载校验 SHA-256、包名、版本、安装证书，安装仍需 Android 系统确认。

## 最近验证（不是对未来代码的保证）

- 0.5.0 模拟器全套 316 项断言，小屏 720×1280 / 字体 130% 再次 316 项；实际 0.4.0 覆盖升级 seed 8 / verify 19。
- 后端 55 项测试；生产 HTTPS 19 项验收。修复 D1 级联删除计数导致的成功删除误报 409，使用 DELETE RETURNING 判断主行。
- GitHub 直连、加速源、Cloudflare 实际完整 APK 哈希一致；后台现存数据保留。公开捐赠列表当次为空，不应造演示数据充数。
- 不同品牌真机 GPS/相机/外部地图仍需实测。生产浏览器自动化超时，本轮线上 HTTP 验证不能冒充浏览器逐项点击。MiniMax 等第三阶段真实成功记录与本轮重测范围见发布记录。

## 下次任务从这里开始

1. 读取最新用户要求、`git status` 和本文件；需要重构/新版本时先明确本次范围，别恢复执行旧路线图。
2. 查 [文件地图](FILE-MAP.md)，按文件所有权拆分任务；使用 [开发指南](DEVELOPMENT.md)中的显式构建参数。
3. 不覆盖 `../release-0.5.0` 或阶段十构建目录；新工作建立独立输出目录。
4. 根据变更选择必要验证，涉及签名/升级/下载才补相应专项；未经新授权不改线上数据或 Release。
5. 保存本次结果和未完成项，更新下列交接状态。

当前状态：下一版已确认的地图/行程/AI/回忆优化代码已整合。时间轴与随手看、AI 草稿编辑和逐项建议、地点关联回忆与按天浏览完成；本机逻辑 58 项、完整 Android 回归 447 项通过，最终按钮间距调整后小屏大字专项 42+55 项通过。原安装签名一致、已知凭据扫描 0 匹配。APK 与截图在 `../../work/build-next-integrated-0928`。用户取消逐阶段确认，已确认范围连续推进。尚未发布，正式版本号未指定，当前开发基线仍 0.5.0 / 11；高德 SDK 在线鉴权/原生底图与真机验收待验证。完整事实和已保留边界见 [本轮完成记录](superpowers/plans/2026-09-28-continuous-next-version.md)。

## 本机私有资源（只记录位置）

相对仓库的 `../../work` 是开发资源根。`signing` 存签名、`config` 存测试服务凭据、`license-private` 存后台凭据/备份，均不属于源码包。管理员登录信息位于其中 `release-050/admin-login.json`，只在本机查看。不要在 Agent 消息、Markdown 或工具日志展开内容。

后续维护只需本文件和与任务相关的指南；历史详查再进入 `docs/superpowers/plans`，无需每次重读完整对话。
