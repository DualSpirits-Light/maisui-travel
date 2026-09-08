# 第三方组件说明

本项目在构建或运行时使用以下第三方组件。实际发行前请由发行方复核对应服务条款、隐私政策和许可证。

- 高德开放平台 Android 地图与搜索 SDK：用于原生地图、兴趣点搜索、标记和折线。使用前由 App 展示隐私提示并取得用户同意。服务条款与隐私政策以[高德开放平台](https://lbs.amap.com/)公布内容为准。
- OkHttp 4.12.0、Okio 3.6.0：用于支持 WebDAV 的 HTTPS 与自定义请求方法，采用 Apache License 2.0。
- Kotlin 标准库 1.8.21、JetBrains annotations 13.0：由网络依赖间接使用，采用各自发布包所附许可证。
- Leaflet 1.9.4：地图回退界面，采用 BSD 2-Clause License，完整文本见 `app/src/main/assets/map/LICENSE.txt`。
- OpenStreetMap 地图数据：仅在回退地图联网加载底图时使用，须遵守 OpenStreetMap 署名和瓦片使用政策。

依赖下载地址与 SHA-256 摘要记录在 `dependencies-lock.json`，便于复现和审计。
