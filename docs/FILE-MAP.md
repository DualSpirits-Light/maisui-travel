# 文件与上下文地图

## 源码仓库

| 位置 | 用途/接手入口 |
| --- | --- |
| `app/src/main/java/cn/lvxu/travel/` | Android 主逻辑；MainActivity、Trip、TripStore、AppPrefs 为基础入口 |
| 同目录 ThemeColors / ThemeSettingsUi / RoundedDialogs / TripPickerUi | 配色、圆角弹窗和旅行切换 |
| 同目录 MapService / MapRoutes / AmapUi / AmapRoadRoutes / AmapRouteSession / AmapPlaceSearch / ApiConfig / AiProviders | 地图、平台密钥、AI 接入 |
| 同目录 ItineraryTimelineUi / ItineraryFormat / QuickLookUi / ItineraryImageRenderer / ItineraryGallery | 每日时间轴、随手看生成预览与相册保存 |
| 同目录 TravelDaySummary / TravelDayUi / DayItineraryText / DayItineraryTextUi | 按设备本地计划时间的出行速览、固定预约和当日日程预览复制 |
| 同目录 TripEditorUi / TravelFormRules / TravelAdjustment / TravelAdjustmentUi / TravelUndo | 旅行草稿、连续录入时间、预约锁定、批量预览和单步撤销 |
| 同目录 FinanceUi / AaLedger / AaUi / BackupMergePlan | 本地同行成员、均摊与实际转账；分享与备份引用重映射 |
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

## 下一版新增模块（2026-09-28 开发中）

- `AiPlan` / `AiExploreUi` / `AiOptimization`：可编辑的 AI 草稿、选中地点导入、逐项优化建议与冲突保护。
- `CheckinMemories` / `CheckinUi` / `PlaceDetailsUi`：稳定地点关联、旧记录提示、按日回忆和照片入口。
- `Trip.Checkin.stopId`：可选的行程地点关联；`TripShareArchive` 导入重新生成编号时同步映射。
- `tests/cn` 中相应 AI/回忆/日期测试为离线逻辑验证；`tests/android` 中相应 UI 测试验证实际窗口交互。

## 旅行录入与当地热力图（2026-10-05）

- `TripEditorUi` / `CityPickerUi` / `TravelFormRules`：旅行表单、省市目录、完成日期和连续新增的纯逻辑。
- `TagChooser`：单窗标签组、跨组草稿、多选与移除。
- `PlaceLinkPicker` / `PlaceSearchPicker`：将地图识别结果填入当前地点 / 住宿草稿。
- `MapGestureFrame`：地图手势归属，保护父页面滚动。
- `BaiduHeatmapUi` / `BaiduHeatmapRuntime`：探索中的独立百度人流图层、用户 Android AK、隐私同意、定位与生命周期；默认地图仍为高德。依赖固定在 `baidu-dependencies-lock.json`。
- `Trip.Expense.photo` / `FinanceUi` / `MediaController`：可选账单原图、拍摄 / 相册、取消和保存失败回滚；BackupArchive / TripShareArchive 处理完整媒体链。
- `test-android.ps1 -TravelEntryOnly`：本轮表单、标签、账单照片和离线热力配置专项；真实热力鉴权为独立 opt-in测试。

## 2026-10-05 后续优化：目的地、日历与附近热力
- 分类栏改为文字页签与选中下划线，区别于内容标签；跨组选择保持。
- 目的地字段右侧下拉打开省/市两栏；自绘圆角日历替代原生 DatePickerDialog，标题满宽，支持年份、月份、闰日。
- 添加地点查询严格限定当前旅行目的地；SDK 与 REST 均加城市范围过滤。
- 附近热力首次定位居中，浏览后不被定位更新拉回；再次点击定位才居中。
- 高德按设备所在城市加载人文、博物馆、风景、美食、逛街，收藏第一项，默认人文。用户选择的地图 App 导航；未安装时高德网页兜底。收藏随设置备份恢复。
- 距离为设备实际位置到 POI 的直线距离，评分来自服务商、缺失显示暂无。支持距离/评分/热力参考排序。
- 百度城市图层没有逐景点人数接口；同视口两张 TextureMapView 快照对照，保守识别热力颜色，只对加载且视口内的地点显示参考小人。移动地图立即清除旧参考，120 秒过期；未知显示人形+问号，不能等同人少。第二张普通底图覆盖在可见热力图下面，不闪烁切换。
- 新代码入口 NearbyPlacesService / NearbyPlace / NearbyFavorites / NearbyNavigation / CrowdIcons / HeatVisualEstimate，集成 BaiduHeatmapUi。参考图与证据在仓库外 work/heat-refine-evidence。
- 本轮验证：旅行录入 91 项、行程 42 项、AI 回忆 55 项、纯逻辑 38 项、真实百度鉴权/高德城市 POI/配对热力分类及实际地点卡片 17 项通过（共 243 项）。日历浅/深色截图已检查，标题无右侧白边；真实热力与普通底图对齐，红色区域经对照识别为高参考。模拟器位置为北京；尚未替代真实手机与兰州覆盖测试。
- 本轮不发 Release、不部署、版本仍 0.5.0（11）。用户 Key 仅短期输入模拟器私人目录，测试清除并恢复设置；源码不内置。

## 2026-10-06 体验报告修复

- `CheckinEditRules`：同行人分隔、去重和保存前验证；`Trip` 负责编辑复制时保留收藏等标志，并区分旧封面迁移与现代照片数组。
- `FormDraftState` / `MediaController` / `DraftMediaFiles`：录入草稿、外部照片选择和原图导入接续；取消时保护旅行清单、费用、地点、打卡和设置中仍引用的媒体。
- `BackupMergePlan` / `ArchiveByteBudget` / `BackupArchive.PreparedRestore`：一致大小限额、按最新快照合并、重复冲突恢复去重、后台准备与界面提交；`TripStore` 显式允许损坏快照恢复前保留原始数据库证据。
- `MainActivity` / `TripEditorUi` / `ItineraryTimelineUi`：首页旅行优先、必填表单与选填折叠、行程常驻新增入口；`ReleaseNotes` / `TutorialUi` 串行启动提示。
- `AuditHostUiTest` / `CheckinAuditUiTest` / `MediaDraftResumeTest` / `PhotoBrushAuditUiTest` / `BackupPreparedAuditTest`：`-AuditFixesOnly` 专项回归，验收证据见最新检查点。

2026-10-07 界面优化入口：`UiControls.java` 管理按钮角色、字段错误聚焦与按宽度排列的按钮；`ic_nav_*.xml` 是五个底部矢量图标。专项测试为 `CompactTimelineUiTest`、`FinancePolishUiTest`、`ControlsPolishUiTest`，从 `-UiPolishOnly` 运行。报告与检查点分别位于 `docs/reports/2026-10-07-*` 和 `docs/checkpoints/2026-10-07-ui-research-polish.md`。
