# 开发、验证与发布

所有示例从仓库根目录运行。路径是当前 Windows 工作区的已核对配置，换机时先核对目录，不要复制私密文件到仓库。

## 本机构建

当前正式包由 `build-apk.ps1` 生成；它锁定 SDK/依赖摘要，使用 Java 17 语法、Android SDK 35。Gradle 配置同时保留，但没有 Gradle Wrapper，不宣称只运行 gradlew 即可重现。

```powershell
$sdk = (Resolve-Path '../../work/android-sdk').Path
$jdk = 'D:/Program Files/JetBrains/PyCharm 2025.1.3.1/jbr'
$buildOut = Join-Path (Resolve-Path '../../work').Path 'build-next-local'
$signingKey = (Resolve-Path '../../work/signing/debug.jks').Path
./build-apk.ps1 -SdkPath $sdk -JdkPath $jdk -BuildDirectory $buildOut -SigningKey $signingKey -AmapSdkPath '../../work/vendor/amap-sdk.jar' -NetworkLibDirectory '../../work/vendor/network'
```

这些参数构建当前源码版本。开发下一版时同步确认 `app/build.gradle`、`build-apk.ps1`、`ReleaseNotes.java`，不要自行推测版本号。构建目录不可指向已发布的 `build-stage10-release`。

**签名关键点：** 当前发布沿用 `work/signing/debug.jks`，别名 androiddebugkey，证书 SHA-256 为 `6feea399c67761f33962ffe1ed9181336d391f25c7fbc294aedfaa5382f9e15e`。它虽然名为 debug，实际承担已发布应用的身份；另一个 maisui-release.jks 不匹配。构建脚本默认签名路径不同，必须显式传入。密码如需覆盖，使用 `-SigningPasswordEnv` 指向本机环境变量，不在命令或文档中写值。

```powershell
$env:JAVA_HOME = $jdk
& "$sdk/build-tools/35.0.0/apksigner.bat" verify --print-certs "$buildOut/lvxu-debug.apk"
& "$sdk/build-tools/35.0.0/aapt2.exe" dump badging "$buildOut/lvxu-debug.apk"
```

下一版不再在清单注入 Android SDK Key，也不读取构建环境或 local.properties 中的地图 Key；由用户在高级设置配置并在 SDK 初始化前动态设置。Web/AI/管理员密钥必须留在用户配置或服务端秘密配置中。依赖列表见 `dependencies-lock.json` 和 `THIRD-PARTY-NOTICES.md`。

## 按影响范围验证

纯文档整理只检查链接、引用及 diff；不要为此重跑整套 Android 构建。业务改动跑相关逻辑或集成测试；发布前再整体验收。

后端：

```powershell
Push-Location server/license
try { npm ci; npm test } finally { Pop-Location }
```

Android：先运行专用模拟器，再安装本次被测 APK。`test-android.ps1` 只安装测试包，不替你安装应用；设备默认 emulator-5580，应先核对序列号。

本机既有 AVD 不在默认用户目录。启动前在当前终端设置 `ANDROID_AVD_HOME` 为 `../../work/avd` 的绝对路径、`ANDROID_USER_HOME` 为 `../../work/android-user` 的绝对路径；再用 SDK 中的 emulator 列出 AVD（当前为 Lvxu）。不要因为默认目录为空就新建或清除已有模拟器。以隐藏窗口、端口 5580 启动，等待 `sys.boot_completed=1` 后测试。

```powershell
& "$sdk/platform-tools/adb.exe" devices
& "$sdk/platform-tools/adb.exe" -s emulator-5580 install -r "$buildOut/lvxu-debug.apk"
./test-android.ps1 -SdkPath $sdk -JdkPath $jdk -AppBuildDirectory $buildOut -SigningKey $signingKey -Device emulator-5580 -UpdateFixtureApk '../../work/build-stage9-fixture/lvxu-debug.apk' -WrongSignerFixtureApk '../../work/build-stage9-fixture/lvxu-wrong-signer.apk'
```

更新专项可加 `-StageNineOnly`。测试 APK 必须与被测 App 同签名。模拟器测试不能并行启动多个 instrumentation，会互相终止进程。测试库按 `$buildOut` 的父目录寻找 vendor/network，因此以上布局应保留。

出游体验专项使用 `-TravelDraftOnly`（名称沿用草稿专项，现包含地图标点选择、旅行草稿、预约调整/撤销和 AA 账本）。`-TravelEntryOnly` 验证城市、日历、标签、热力和照片录入；`-AuditFixesOnly` 验证此前审计修复。每项串行运行；不同专项开关不得组合。0.5.1-preview / 12 是本地体验测试版，不能当作已经部署的正式版本。

Android Key 配置专项可加 `-MapKeyOnly`；不得与 `-StageNineOnly` 同时使用。它验证配置和入口行为，不代表 ARM 原生地图底图、真实 Key 鉴权或网络路线已通过真机验证。

覆盖升级：以下命令会卸载**指定模拟器**的测试 App，再安装旧包、写入夹具、覆盖新包。仅用于专用模拟器，不可替换为用户手机序列号。

```powershell
./test-upgrade.ps1 -SdkPath $sdk -JdkPath $jdk -OldBuildDirectory '../../work/build-040' -OldApk '../麦穗旅序-0.4.0.apk' -NewApk "$buildOut/lvxu-debug.apk" -BuildDirectory '../../work/upgrade-next-local' -SigningKey $signingKey -Device emulator-5580 -AllowEmulatorReset
```

该夹具目前验证 0.4.0 的数据模型；以后新增 0.5.0→下一版升级测试时应明确准备相应旧版本基线，不要把同一夹具报告成覆盖所有历史版本。

`tests/cn` 有独立 Java 逻辑测试，按文件 main/依赖运行；当前没有统一的一键逻辑测试脚本。真实 API 验证只使用仓库外配置，输出状态/数量等脱敏结果，不打印请求 Key。定位和相机机型问题需要真机，不能用模拟器断言代替。

## 后端与发布

正式 Worker 配置在 `server/license/wrangler.toml`，域名 license.zjm0929.cn，D1 maisui-license，R2 maisui-shares 与 maisui-updates。0001–0006 已应用到生产；下一次变更新增迁移，不修改历史迁移内容。

管理员常规入口为密码门户 `/console`。旧 tools/license-admin.mjs 使用 ADMIN_TOKEN，不适用于当前生产。密码恢复见 [管理员手册](../server/license/ADMIN-ACCOUNTS.md)，密钥托管和旧授权码边界见 [授权码手册](../server/license/LICENSE-CODES.md)。

下一版获得发布授权后按顺序进行：

1. 读取线上现状、备份 D1/Worker 配置与内容到仓库外受限目录；保留 CODE_PEPPER、签名私钥和历史 AES 密钥。
2. 以原安装签名构建，检查版本/证书、数据覆盖升级、变更对应测试；扫描源码 ZIP 与 APK，既检查已知秘密值也检查可疑新增配置。
3. 源码打包只包含版本控制源码和必要资料，排除 .git、node_modules、.wrangler、构建产物、私有配置。固定 APK 字节和 SHA-256 后不再重建同一已发布版本。
4. 需要时应用增量迁移，再部署兼容 Worker。GitHub Release 上传并验证 APK/源码/摘要；R2 上传**同一份** APK 到 `updates/releases/<versionCode>.apk`。
5. R2 对象 customMetadata 必须包含字符串 versionCode 和小写 sha256，大小须符合后端限制。只有文件存在但无元数据时不会被清单视为就绪。CLI 单独 object put 不会补齐这两项。
6. 更新 Worker 的 UPDATE_VERSION_CODE、UPDATE_VERSION_NAME、UPDATE_GITHUB_APK_URL、UPDATE_APK_SHA256、UPDATE_NOTES。GitHub 必须是原始 Release URL，代理由客户端选择；仅 R2 就绪时可先提供 Cloudflare 单源。
7. 实际完整下载 GitHub/加速/Cloudflare并比较摘要，检查清单、HEAD/Range、密码登录、旧授权与捐赠隔离。移除临时上传入口，记录公开链接、部署版本和未验证边界。

Wrangler OAuth、Git 凭据从本机既有登录读取，不写入项目。`../../work/publish050.py` 等是针对 0.5.0 的历史发布辅助脚本，含固定版本/摘要；不要直接重跑或作为通用发布命令。0.5.0 发布过程中用过的临时元数据 Worker 已删除，未来如需临时入口必须限制鉴权、固定目标并在结束后移除。

## 收尾与交接

更新 [PROJECT-CONTEXT](PROJECT-CONTEXT.md) 的当前版本/未完成项；在 `docs/superpowers/plans` 新建有日期的检查点。每个检查点写明改动、已运行的验证、产物位置、下一步及需要用户处理的真实阻塞。不能把旧成功记录当作新代码验证结果。

发布附件保持不变；后续记录文档单独提交。不清理签名、备份、旧版包或测试夹具来“整理上下文”。

## 下一版地图第二阶段专项

`test-android.ps1 -StageTwoMapOnly` 用于真实路线解析与缺 Key 配置交互的离线回归，与其他专项开关互斥。先构建并安装当次应用，再运行测试；结果与线上服务调用、原生地图底图验收分别记录。

## 下一版行程第三阶段专项

`test-android.ps1 -StageThreeItineraryOnly` 覆盖导出快照、长图分页与图库回滚、时间轴触摸排序、预览及取消/保存。须安装与当前源码对应的应用 APK，不能只更新测试包；与其他专项参数互斥。

`tests/cn/lvxu/travel/ItineraryFormatTest.java` 是独立日期/时间格式检查，可用 Java 与 `../../work/json.jar` 编译 Trip、ItineraryFormat 后执行。

专项在专用模拟器 `externalFiles/stage3-evidence` 留下 demo 截图及长图，用于目视检查；只含合成示例。API 29+ 真实 MediaStore 测试会删除其创建的唯一测试图片，既有用户媒体不参与。旧版写入权限分支的逻辑测试不能替代 Android 8/9 真机验收。

## 下一版 AI 与回忆专项

`test-android.ps1 -AiMemoriesOnly` 运行 AI 预览/建议交互、打卡地点关联/按天浏览及完整 ZIP 分享关联验证。与其他专项互斥，须先构建并安装最新应用。使用合成 AI 响应，不调用付费接口；此结果不能描述成新的真实 AI 服务验证。

统一开发输出使用 `../../work/build-next-integrated-0928`；此前阶段目录只保留历史证据。当前范围连续推进，用户要求暂停时再保存检查点，正式发布需另行授权。

## 城市热力图依赖与专项回归

手工构建现另接受 `-BaiduSdkDirectory` 缓存目录；四份 AAR（Map/base/Util 8.2.0、common 1.0.43）按 `baidu-dependencies-lock.json` 下载和核验，不能遗漏 base 的传递鉴权组件。详见 [百度城市热力图](BAIDU-CITY-HEATMAP.md)。

`test-android.ps1 -TravelEntryOnly` 在一次性模拟器执行录入、标签、键盘、手势、费用照片及无 AK/拒绝隐私的热力入口检查。真实在线热力检查另需临时私人输入，禁止把 AK 写进构建参数、测试文件或 APK。

2026-10-05 后续优化见 `superpowers/plans/2026-10-05-city-calendar-heat-browser.md`。聚焦测试 `-TravelEntryOnly` 包含目的地/日历/标签/城市搜索/收藏；真实接口测试需一次性私人输入百度 Android AK 和高德 Web 服务 Key，测试结束移除、恢复设置与权限。

## 体验报告修复回归

`test-android.ps1 -AuditFixesOnly` 在专用模拟器验证旅行编辑标志、打卡照片分类与草稿、SQLite 写入失败、页面重建接续原图导入、画笔撤销重做、备份冲突合并与损坏快照恢复。与其他专项互斥；测试会保存并恢复夹具前的旅行和设置，不得在真实用户手机运行。

当前构建和日志在 `../../work/build-audit-fixes-1006`。最终状态以 [2026-10-06 检查点](checkpoints/2026-10-06-audit-fixes.md) 为准；模拟器结果不代表真实定位、在线热力、AI 服务或大陆网络下载重新验收。

## 2026-10-07 界面专项

`test-android.ps1 -UiPolishOnly` 验证紧凑行程卡、账单/分摊、小屏控制、导航语义和错误聚焦。用当前 APK 构建目录传 `-AppBuildDirectory`，其他 SDK、JDK、签名参数与上方示例一致。专项与其他 Only 开关互斥；仪器测试串行运行。截图在模拟器应用外部文件目录 `ui-polish-evidence`；截图等待主题变色稳定后获取。小屏测试改变模拟器分辨率/密度/字体后，结束时恢复原设置。

`-TravelDayOnly` 验证出行速览入口、实际当天跳转、预约、窗口失焦停止计时与返回立即刷新、默认不带备注和实际剪贴板复制。截图在 `travel-day-evidence`。纯 Java 测试为 `TravelDaySummaryTest`（24项）与 `DayItineraryTextTest`（37项），以当前构建 classes 和仓库外 json.jar 为 classpath；覆盖跨午夜、重叠、拖动排序与允许导出字段，不依赖第三方在线服务。
