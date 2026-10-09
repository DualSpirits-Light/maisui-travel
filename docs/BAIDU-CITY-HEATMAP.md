# 百度城市热力接入（2026-10-05）

探索页独立使用百度城市热力 SDK 图层，保留其他入口的高德默认选择。百度官方描述为实时人群分布密度与变化趋势；调用 `BaiduMap.setBaiduHeatMapEnabled(true)`，在 11–21 级查看。此图层没有提供景区精确人数、区域统计或历史预测接口，本次不生成任何人数或拥挤分数。空白图层可能无覆盖或未加载，不能解释为人少。

## 凭据与初始化

- 用户在高级设置单独配置 **Android SDK AK**；Web 服务端、浏览器 AK 不用于该功能。
- 百度控制台绑定包名 `cn.lvxu.travel` 和实际 APK 安装证书的 SHA1。签名必须沿用已发布证书；用 `apksigner verify --print-certs` 从实际构建输出读取指纹，不能依据证书文件名推测。
- 用户明确同意百度隐私说明后，依次调用 `SDKInitializer.setAgreePrivacy`、`setApiKey`、`initialize` 和 `setCoordType(BD09LL)`。源码、清单、构建参数均无内置 AK；变更进程内已经初始化的 AK 后提示重启。
- 拒绝系统位置权限仍可手动浏览热力图；首次启用地图自动定位，用户也可点击定位重试；申请 coarse/fine，支持仅近似位置。系统 GPS/network 坐标经百度 `CoordinateConverter` 的 GPS 转 BD09LL 后展示。页面暂停/销毁取消定位更新和超时任务，恢复/暂停/销毁映射百度 MapView 生命周期。

## 构建

官方 Maven Central 的 `BaiduMapSDK_Map:8.2.0` 依赖 `base:8.2.0`，base 继续依赖 `common:1.0.43`（内置 `lbsCoreSDK_Proguard.jar`，提供 LBSAuthManager），已确认四份 AAR 可下载。AAR 无资源声明、无新增 manifest 组件；base 含两个附带鉴权/安全 JAR。手工构建提取 classes.jar、libs/*.jar 并包装 assets/jni；Gradle 使用官方依赖。手工构建新增可选 `-BaiduSdkDirectory` 缓存参数，四份 AAR 固定 SHA256 见根目录锁定文件。地图与 base 均包含 ARM 与 x86 原生库。位置坐标转换另引入同版 Util 组件，亦锁定下载摘要。

## 官方资料

- [城市热力与开关](https://lbs.baidu.com/docs/android?title=androidsdk%2Fguide%2Fcreate-map%2Fmaptype)
- [显示地图、动态 AK 与图层缩放范围](https://lbsyun.baidu.com/docs/android?title=androidsdk%2Fguide%2Fcreate-map%2Fshowmap)
- [SDK 自动部署及版本](https://lbsyun.baidu.com/docs/android?title=android-navsdk%2Fguide%2Fautodeploy)
- [初始化前隐私同意](https://lbsyun.baidu.com/faq/details?id=2269&title=2434)
- [AK 包名和签名要求](https://lbs.baidu.com/docs/guide?title=android-guide%2Fprepare)

本地编译和设备回归结果由本轮主检查点记录。实际 AK 鉴权、百度在线城市热力覆盖、真机位置精度须单独实测，不能以编译成功替代。

2026-10-05 在线初始化诊断：初版手工依赖锁遗漏 base 的 common 传递依赖，隐私接口发生 NoClassDefFoundError。现已按官方 base POM 补齐 common 1.0.43 并锁定摘要；离线测试新增不执行类初始化的 LBSAuthManager 可加载检查。在线重验结果以主检查点为准。

2026-10-05 最终在线实测：用户提供的 Android AK 显式 SDK 鉴权状态为成功，模拟器外部注入北京位置，真实城市热力瓦片已显示；在线 6 项检查通过并保存截图。AK 仅通过短期私人文件输入，读取后立即删除，恢复测试前配置；源码及 APK 不包含该 AK。模拟位置不作为真机定位精度验证。

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


## 2026-10-05 单地图、缓存与区域选择替代方案

上一节双 TextureMapView、120 秒过期、拖动清参考仅描述上一轮实现；已由单渲染器串行采样及 30 分钟持久缓存替代。拖动保留有效参考。当前实现、真实拖动证据、Android 后台限制与官方免费/配额调查见 [当前交接](checkpoints/2026-10-05-heat-cache-region.md)。没有实现美食分类地图 Marker。

## 2026-10-05 当前热力渐进刷新与拖动优化

最新交接见 [热力渐进刷新](checkpoints/2026-10-05-heat-stale-smooth.md)。旧参考不再满 30 分钟删除，改为明示日期时间并保留最多 7 天；30 分钟用于判断刷新到期。刷新不清整表，完成后才更新时间；拖动取消采样且不由拖动结束自动启动，像素计算移出主线程。地图关闭时不保证后台热力刷新。新 APK 和本轮 199 项验证记录在交接；高德截图拥挤标签没有找到普通 Key 的公开接口，官方企业客流服务需单独咨询。