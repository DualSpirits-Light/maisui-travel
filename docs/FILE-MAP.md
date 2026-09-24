# 文件与上下文地图

## 源码仓库

| 位置 | 用途/接手入口 |
| --- | --- |
| `app/src/main/java/cn/lvxu/travel/` | Android 主逻辑；MainActivity、Trip、TripStore、AppPrefs 为基础入口 |
| 同目录 ThemeColors / ThemeSettingsUi / RoundedDialogs / TripPickerUi | 配色、圆角弹窗和旅行切换 |
| 同目录 MapService / MapRoutes / AmapPlaceSearch / ApiConfig / AiProviders | 地图、平台密钥、AI 接入 |
| 同目录 CheckinUi / CheckinHistoryUi / PhotoEditorUi / LocationSession | 打卡、照片编辑与定位 |
| 同目录 TripShareArchive / BackupArchive / WebDavService | 分享、备份、WebDAV |
| 同目录 UpdateService / UpdateDownloads / UpdateDownloadService / UpdateUi | 清单、校验、下载服务、前台窗口与通知 |
| `app/src/main/res`、`assets` | Android 资源和内置网页资源 |
| `server/license/src` | Worker 路由、管理页面、密码账户、授权码保险库、捐赠、更新接口 |
| `server/license/migrations` | D1 增量迁移，0001–0006 已上线；不得改写已执行迁移 |
| `tests/android`、`tests/upgrade`、`tests/cn` | Android 集成、覆盖升级夹具、独立逻辑测试 |
| `server/license/test` | 后端 node:test；D1 适配器应模拟级联 total_changes |
| `marketing` | 既有小红书预告图文，不能当作当前功能验收图 |
| `tools/license-admin.mjs` | 历史管理员令牌 CLI，当前生产密码初始化后已不可用；保留供历史追溯 |
| 根目录 `build-apk.ps1` / `test-android.ps1` / `test-upgrade.ps1` | 当前可用构建与验证入口，参数见开发指南 |

## 文档阅读顺序

1. [当前上下文](PROJECT-CONTEXT.md)：已发布状态、决策和剩余边界。
2. [开发指南](DEVELOPMENT.md)：构建、测试、发布和私有资源保护。
3. [正式发布记录](superpowers/plans/2026-09-24-release-050-published.md)：0.5.0 最终事实。
4. [管理员账户](../server/license/ADMIN-ACCOUNTS.md)、[授权码机制](../server/license/LICENSE-CODES.md)、[服务端说明](../server/license/README.md)：按任务读取。
5. `history/` 保存旧根目录说明原文；`superpowers/plans/` 保存分阶段证据，均不自动形成新待办。

根目录 ROADMAP、TEST-RESULTS、SETTINGS-INTEGRATION、LICENSE-ADMIN 保留为跳转页，避免旧引用断裂。历史文件内相对链接按原文件位置理解，日常使用上面的当前入口。

## 仓库外工作区

| 位置（相对仓库） | 处理规则 |
| --- | --- |
| `../release-0.5.0` | 已发布附件及其本地副本；保持不可变，不用作下一版输出目录 |
| `../麦穗旅序-0.4.0.apk` | 实际旧安装包，覆盖升级验证依赖，保留 |
| `../../work/android-sdk`、`vendor`、`json.jar` | 当前编译/测试依赖，保留；不能按缓存一并删除 |
| `../../work/signing`、`config`、`license-private`、`activation` | 签名/密钥/备份，保留原路径与访问权限；不提交、不显示内容 |
| `../../work/build-040`、`build-stage9-fixture` | 旧类文件及正/误签名 APK 夹具，现行测试依赖，保留 |
| `../../work/build-stage10-release` | 0.5.0 候选构建及验收基线，保留 |
| `../../work/stage10-screens`、`stage10-small-screens` | 已检查的截图证据，保留 |
| 其他 `../../work/build-*` / `unit-*` / 脚本与截图 | 历史阶段产物；先查引用和可重建性，再单独清理，当前未删除 |
| 仓库内 `build` / `build-manual` / `build-stage*` / `.wrangler` / `work` | 被忽略的本地生成物；部分含签名/配置，不能仅按目录名自动清理 |

本次整理通过入口、分类和历史归档减少上下文负担，未搬迁依赖链或删除备份。若以后需要回收磁盘空间，应另列准确路径与依赖检查结果。
