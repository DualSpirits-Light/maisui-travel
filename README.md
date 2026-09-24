# 麦穗旅序

麦穗旅序是 Android 旅行规划与记录应用，涵盖行程、地点、预算、清单、打卡和照片。当前已发布版本为 **0.5.0（versionCode 11）**，支持 Android 8.0（API 26）及以上。完整更新内容见 [0.5.0 发布说明](docs/release-notes-0.5.0.md)。

- [下载 0.5.0（GitHub Release）](https://github.com/DualSpirits-Light/maisui-travel/releases/tag/v0.5.0)
- [备用下载（Cloudflare）](https://license.zjm0929.cn/updates/apk/11.apk)
- [授权管理后台](https://license.zjm0929.cn/console)（仅管理员）

## 从这里接手项目

后续修改先读 [项目当前状态](docs/PROJECT-CONTEXT.md)，再按工作内容查看 [开发与验证](docs/DEVELOPMENT.md) 和 [文件地图](docs/FILE-MAP.md)。版本交付证据见 [正式发布记录](docs/superpowers/plans/2026-09-24-release-050-published.md)；后台操作见 [授权管理](LICENSE-ADMIN.md)。

| 位置 | 内容 |
| --- | --- |
| `app/` | Android 应用；Java 17、系统 View、SQLite，编译及目标 API 35 |
| `server/license/` | Cloudflare Worker、D1 和 R2；授权、口令分享、捐赠管理与更新清单 |
| `tests/` | 桌面 Java 测试、Android 仪器测试及升级夹具 |
| `docs/` | 当前上下文、开发说明、发布记录及历史资料 |
| `marketing/` | 宣传图文和制作素材 |
| `tools/` | 辅助管理工具 |

## 当前架构边界

旅行数据和照片以本机为主；完整备份、WebDAV 与旅行分享由用户主动操作。云端服务承担授权验证、限时口令分享、公开更新信息和后台管理，不提供普通用户账号。地图页优先使用经用户同意的高德 Android SDK；不可用时回退到内置 Leaflet / OpenStreetMap。地点搜索、路线估算及外部打开可使用用户选择并配置的地图服务，地图连线仅作直线示意。AI 搜索与规划需要用户配置相应服务。

构建需要本机 Android SDK、JDK 和所需的私有配置；仓库不提供签名私钥或服务凭据。具体命令、参数与验证入口以 [开发与验证](docs/DEVELOPMENT.md) 为准，发布或升级必须核对签名连续性。

## 文档时效

[整理前 README 原文](docs/history/README-before-organization-2026-09-24.md) 仅供追溯旧版本描述，**不是当前开发或发布操作手册**。其他阶段记录也保留其写作时状态；遇到冲突，先以当前代码、项目当前状态和正式发布记录核对。
