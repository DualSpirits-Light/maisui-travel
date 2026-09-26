# 地图密钥与安装签名

包名：`cn.lvxu.travel`。当前 0.5.0 与已发布 0.4.0 使用相同安装证书，必须沿用它才能覆盖升级。

- 当前实际发布证书 SHA-1：`17:C6:0F:DB:EB:EE:15:32:A0:B7:8F:0B:45:B0:C9:6D:4E:14:3A:84`。
- SHA-256：`6feea399c67761f33962ffe1ed9181336d391f25c7fbc294aedfaa5382f9e15e`。
- 本机原签名路径与构建参数见 [开发指南](docs/DEVELOPMENT.md)。不能凭文件名选择所谓 release 密钥；必须核对证书指纹。

下一版保留高德 Android SDK，但不再内置 Android Key。构建脚本和 Gradle 不读取地图 Key，清单不携带开发者 Key。用户在高级设置填写自己申请的 Android Key，由应用加密保存并在 SDK 初始化前设置。申请时需绑定上面的包名和安装证书 SHA-1。浏览器 Web Key、安全密钥和 REST Web 服务 Key 不能互换。

运行时设置依据高德官方 [MapsInitializer.setApiKey](https://a.amap.com/lbs/static/unzip/Android_Map_Doc/3D/com/amap/api/maps/MapsInitializer.html) 和 [ServiceSettings.setApiKey](https://a.amap.com/lbs/static/unzip/Android_Map_Doc/Search/com/amap/api/services/core/ServiceSettings.html)。仍需在地图/搜索初始化前完成隐私告知并取得用户同意。

高德可选 REST、百度服务端、腾讯 WebService、AI 与 WebDAV 凭据由用户在 App 配置验证后保存；不要写入源码、发布包或备份。真实接口历史验证见[第三阶段记录](docs/superpowers/plans/2026-09-14-stage3-checkpoint.md)。

[历史原文](docs/history/AMAP-CONFIG-before-organization-2026-09-24.md)中另一张“独立正式签名”证书不是当前发布签名，不能用于覆盖升级。
