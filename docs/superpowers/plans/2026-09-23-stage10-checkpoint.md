# 第 10 阶段检查点：候选包验收与发布准备

2026-09-23。状态：本地验收与候选包准备完成；等待用户按既定路线图批准正式发布。未提交、推送、部署生产 Worker、应用生产迁移、上传 R2 或创建 Release。

## 最终版本与关键修复

- 0.5.0 / versionCode 11。APK SHA-256 `c407d7c8b3e56c8ddc0e7d2c4b60417fcfb06b462512a0fbc12397ca4f4c0316`，54,145,483 字节。
- 对照真实发布的 `../麦穗旅序-0.4.0.apk`，确认原证书指纹 `6feea399c67761f33962ffe1ed9181336d391f25c7fbc294aedfaa5382f9e15e`。密钥位于仓库外 `../../work/signing/debug.jks`，别名 androiddebugkey；不能使用另一个名为 maisui-release.jks 的不同证书。候选包已使用原签名重新构建并核验。
- UpdateService 代理正文 SocketTimeout 作为网络失败回退直连；显式取消保持取消，不回退。新增相应回归。
- 更新清单允许只有 Cloudflare 就绪来源，兼容 apkUrl；无 GitHub 且 R2 不就绪返回 404。新增后端回归，先失败再修复通过。
- 新增可重复的 test-upgrade.ps1 与升级仪器夹具，仅允许明确指定的可重置模拟器；在旧包中写入数据，再以 install -r 覆盖候选包读取。
- .gitignore 排除 build-stage*/ 和 .wrangler/，防止阶段编译产物与本地运行缓存进入源码包。

## 当前证据

- Android 全套 316 项通过；小屏 720×1280 / density 320 / 字体 1.3 再跑 316 项通过，之后已恢复模拟器显示设置。
- 原发布 0.4.0 -> 0.5.0：seed 8、verify 19 项通过，检查旅行、地点、标签、打卡、原始照片字节、偏好和历史授权。
- 后端 node:test 55/55；本地 workerd + D1 全部六个迁移成功，15 项实际 HTTP 断言通过。使用隔离本地测试凭据，没有生产写入。
- 最新构建的 Java REST 适配器真实请求高德、百度，各返回 20 条地点，驾车距离/时长有效。其余第三阶段真实 API 成功结果未在本轮重复，不宣称新近全部通过。
- 源码、交付源码 ZIP 与 APK 已保存凭据比对扫描通过，仅 APK AndroidManifest 中存在预期的高德应用绑定 Android Key；没有服务端私钥、管理员令牌和签名密码命中。
- 标准截图 `../../work/stage10-screens`；小屏大字截图 `../../work/stage10-small-screens`。六套明暗主题、侧滑按钮、选择窗口、关于页和更新弹窗检查未见严重不可读/不可操作问题。小屏 GitHub 加速标签会换行，但按钮可用。
- Astra 最终定向复审未发现具体阻断项；Sol 整理交付说明，Luna 审查截图。所有改动保留在当前工作区，未撤回早期阶段内容。

## 下一步（批准发布后）

本地交付目录 `../release-0.5.0/`：maisui-travel-0.5.0.apk、maisui-travel-0.5.0-source.zip、release-notes-0.5.0.md、0.5.0-delivery.md、SHA256SUMS.txt，以及尚未启用的 update-manifest.pending.json。不要把 pending 清单当作已上线资源。

1. 备份生产 D1/配置并核对现状，配置管理员恢复凭据、授权码加密密钥和 UPDATES 桶；应用所需迁移，部署后台。
2. 发布同一 APK、源码与中文更新说明到新 0.5.0 Release；上传同一 APK 到 R2 `updates/releases/11.apk`，正确设置版本/哈希对象元数据。
3. 配置更新清单，再实际下载两条公开线路，验证哈希、签名、通知与安装；生产门户、授权及捐赠完成 HTTPS 验收。不能先宣布公网双线已验收。
4. 不同品牌真机 GPS、相机及外部地图唤起仍需真机检查；不能用模拟器结果替代。历史泄露密钥的撤销状态不在本轮扫描证明范围内。

用户长期分工偏好已保存于记忆扩展目录：Luna 做简单测试/小 UI，Sol 做普通功能，Astra 做复杂问题与最终 Review，主 Agent 负责拆分与整合。
