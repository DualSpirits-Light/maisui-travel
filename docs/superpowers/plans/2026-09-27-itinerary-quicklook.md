# 第三阶段：每日时间轴与随手看实施计划

**目标：** 在既有行程列表中更清楚地阅读一天安排，并将选中日期生成可离线查看的长图，预览后明确保存到相册。

**依据：** 用户已批准的地图与行程分阶段方案，见 `2026-09-25-map-itinerary-checkpoint.md`；2026-09-27 明确继续第三阶段。按原要求阶段结束停下确认，不进入 AI 或回忆功能开发。

**架构：** 保留 Java 原生 UI 与 Trip 数据结构；时间轴抽为独立 UI，导出用 Canvas/StaticLayout 后台绘图，预览只加载一张缩放图；图库采用 Android MediaStore，旧设备最小权限适配。无新网络服务或依赖。

## 文件分工与接口

- Sol A：MainActivity.itinerary()、ItineraryTimelineUi、ItineraryFormat。保留地图/编辑/详情/拖动，时间轴显示完整起止、跨日和实际抵达方式。共用 `timeRange(Trip.Stop)`、`dateLabel(Trip,int)`。
- Sol B：ItineraryImageRenderer、ItineraryGallery。`render(Context,Trip,int,Options,File)` 返回 Sheet 列表，单图宽 1080、过长分页；`save(Context,File,String)` 返回 Uri。
- Sol C：QuickLookUi。日期与内容选择、快照、后台生成、逐图预览、保存进度/失败/重试/权限/取消。
- 主 Agent：生命周期及权限转发、测试入口、文档、复核。Luna 统一构建与串行模拟器测试，Astra 最终审查。

## 内容和边界

- 默认选当前日期，支持多日期；默认地址、标签、备注，费用与照片由用户勾选。基本地点名称和时间始终保留。
- 按天独立图片；超长内容分张并注明页码，不截掉行程。全量数据只生成到临时文件，不同时持有所有位图。
- 导出浅色纸张式排版用于相册阅读，App 时间轴与弹窗遵循当前深浅配色。默认不导出同行人、车牌等旅行私密字段。
- 图片只读本地媒体，不联网下载；原图保持不变。
- 抵达交通使用终点 mode，不用直线距离或时间空档冒充真实交通耗时。道路路线详情仍在地图导览。
- Android 29+ 写入自有 MediaStore 图片无需存储权限；API 26–28 保存时申请 WRITE_EXTERNAL_STORAGE，拒绝后保留预览。官方依据：https://developer.android.com/training/data-storage/shared/media

## 验证与完成清单

- [ ] 时间轴：跨午夜时间、空日、重叠、长标题、小屏/深色、拖动和详情入口不回退。
- [ ] 渲染：中文/长备注换行、超长分页、图片比例与丢失媒体、选项过滤、取消清理。
- [ ] 交互：未选日期、不保存直接返回、明确保存、部分保存失败仅重试失败项、权限拒绝、页面销毁。
- [ ] 构建签名一致；阶段专项和必要旧行程回归通过；实际截图与导出 PNG 目视检查。
- [ ] 独立审查后修复；更新检查点与当前上下文，本地提交、不推送/发布。

## 接续状态

代码已落盘：MainActivity 入口/生命周期、独立导出快照、时间轴、Renderer、Gallery、QuickLookUi 与阶段专项入口。Astra 已审查，发现保存末尾取消后仍可能发布当前图片，已增加 flush 后及流关闭后的中断检查，复核无剩余 P1/P2。

初次模拟器专项使用修复前 Gallery.class（16:57:23），新源码修复时间 16:58:08，因此“cancel during final flush ignored”断言失败；原记录在 `../../work/build-next-map-stage3/logs/stage3-attempt1.log`，保留。接续时需要重新构建修复后的应用、安装后执行 `-StageThreeItineraryOnly`，不能只更新测试包。尚未声明阶段完成或发布。

## 2026-09-28 收尾

上文为历史接续状态；当前已完成。整合专项 42 项通过，完整回归 447 项通过，最后间距调整后小屏大字号专项 42 项通过。APK、审查、失败修复、截图及保留边界统一记录于 [连续开发完成记录](2026-09-28-continuous-next-version.md)。未发布。
