# 热力渐进刷新与拖动性能

用户授权连续优化，保留所有工作树，无Release/部署/内置Key。

发现：mapstatusfinish在每次拖动350ms后采样，browsing重置cooldown；classify在主线程copy整幅图并对所有POI循环，updated重建整个地点列表；refreshAll主动clearHeat且TTL30分删除，引起全未知；refreshedAt开始刷新就更新，不能反映完成时间。

实现：拖动回调不采样；采样计算移单线程；地图拖动立即取消刷新；过期结果保留最多7天且明确上次参考；30分钟仅表示刷新期限；完成周期后才记成功时间，取消/失败不更新；手动可强制，自动依据持久完成时间且手势后15秒不启动；刷新逐批更新热力行，不全列表重建；热力仍依赖百度图层，首次没数据不能承诺立即数值，地图瓦片更新由SDK控制，应用不能每30分钟自行下载重播瓦片。查询高德截图人流公开接口，只研究不偷偷抓客户端接口。

文件：root BaiduHeatmapUi及整合；nearby_cache Cache/Place/Warmup+tests；android_regression Sampler+focusedtests。专用模拟器串行验证。验收含旧参考保留、30分due、完成时间取消/失败、逐条更新、拖动不采样、离线不抹值、签名及凭据扫描。
本轮验证完成：新包 121 项 Android 界面/缓存、22 项真实地图服务、56 项纯逻辑检查共 199 项通过；实际拖动/地点卡片截图已检查。交付及限制见 docs/checkpoints/2026-10-05-heat-stale-smooth.md。未发 Release。