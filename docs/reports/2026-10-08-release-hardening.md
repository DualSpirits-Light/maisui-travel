# 2026-10-08 发布构建加固

## 改动与使用

`build-apk.ps1` 新增可选 `-Release`。不传时保持现有 D8、`lvxu-debug.apk` 和仪器测试行为；传入时使用 SDK 35.0.0 自带 `lib/d8.jar` 的 R8，显式 `--release`，生成 `lvxu-release.apk`。发布模式强制显式提供已存在的签名文件，禁止意外自动创建新身份。Gradle release 同步启用混淆并关闭调试，但本轮只验证正式使用的手工构建流程。

```powershell
./build-apk.ps1 -SdkPath '../../work/android-sdk' `
  -JdkPath 'D:/Program Files/JetBrains/PyCharm 2025.1.3.1/jbr' `
  -BuildDirectory '../../work/build-release-060' `
  -SigningKey '../../work/signing/debug.jks' `
  -AmapSdkPath '../../work/vendor/amap-sdk.jar' `
  -NetworkLibDirectory '../../work/vendor/network' `
  -BaiduSdkDirectory '../../work/vendor/baidu' `
  -VersionCode 15 -VersionName '0.6.0' -Release
```

正式候选使用新建的独立构建目录。`mapping-private.txt` 只留仓库外私有构建目录，便于故障定位；不得上传到 Release、R2、源码 ZIP。`classes` 和 `classes.zip` 保留原始编译类供本地检查，也不得发布。最终仅发布审查过的 APK、源码归档和摘要；不要将整个构建目录打包。

`app/proguard-rules.pro` 对应用内部类和成员做名称混淆，首次上线保守关闭裁剪与优化；保留 Android 组件、JNI/反射、地图 SDK 和 HTTP/Kotlin 库的契约。高德分发包原有五个未提供的可选引用采用精确 `-dontwarn`，没有全局忽略缺类；原生 MapView 路径不使用 SupportMapFragment 或高德热力瓦片。构建没有新增开发者密钥注入入口。

## 验证证据

- PowerShell 语法解析通过，相关修改 `git diff --check` 无空白错误。
- 独立真实构建：`../../work/hardening-probe-1008/lvxu-release.apk`，退出码 0。探针使用当时源码版本 0.5.3-preview / 14，不是最终 0.6.0 发布产物。日志在同目录 `build.log`。
- APK v2/v3 签名验证通过，证书 SHA-256 为 `6feea399c67761f33962ffe1ed9181336d391f25c7fbc294aedfaa5382f9e15e`，与已发布身份一致。
- 二进制清单：`debuggable=false`、`testOnly=false`、既有 `allowBackup=false`。
- 映射统计：1,074 个 `cn.lvxu.travel` 应用类被重命名（包括内部/匿名类）；APK 内 `.java`、`.class`、签名库及 mapping 文件条目合计 0。
- 探针 SHA-256：`a75bd4b33beaba8895cc9413aeaf4e6f28d969ce63bca3ec1c25f8a2ef49df80`。不得把探针摘要填入最终发布配置。
- 地图二进制 SDK 有既有 stack-map / EnclosingMethod 元数据警告，已保留记录，未通过全局规则隐藏。没有运行 Gradle、模拟器仪器测试、真实在线地图或 ARM 真机测试。

发布验收建议：先对同一最终源码的普通构建执行完整仪器回归，再对实际混淆发布 APK 做覆盖安装、启动、地图入口、后台下载与主要 UI 的冒烟验证。当前仪器测试直接引用应用内部类，不能用未混淆测试 APK 对混淆应用跑全套并声称兼容。源类文件保留不等于发布 DEX 类名保留。ARM 地图鉴权、底图和定位仍需要对应真机验证。

## 密钥与备份只读审查

`SecureVault` 使用 AndroidKeyStore 非导出 AES 密钥、AES-GCM 随机 IV，把用户配置加密写入应用私有偏好；硬件保护取决于设备，代码未强制硬件后端。密钥在 SDK/API 使用时必然进入进程内存，root、运行时注入或被攻陷系统仍可能读取。混淆只能提高静态分析成本，不能让嵌入 APK 的开发者秘密变安全。开发者 AI/Web/管理员等秘密应继续仅在服务端保存；Android 地图能力由用户自行配置并利用提供商签名/包名约束。

系统清单已有 `allowBackup=false`；`BackupArchive` 只导出旅行、引用媒体及 `AppPrefs.exportJson()` 的非秘密字段白名单，不导出 secure-vault。手动 ZIP 本身未加密，包含个人旅行/照片，SHA-256 只检查完整性，不提供保密性或来源认证。部分厂商设备迁移行为需另行实测，不把清单配置当作所有设备的绝对保证。本轮没有修改 SecureVault、备份 Java 或生产秘密。

本报告未执行已知秘密值扫描；最终源码 ZIP / APK 必须由发布主流程在不打印秘密值的条件下扫描。无部署、发布或提交操作。
