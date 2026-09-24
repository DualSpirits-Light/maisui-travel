# 第 3 阶段检查点

状态：用户已授权，按用户要求优先 MiniMax，并使用中国接入地址。当前在修复与验证，未发布。

## 已有真实请求证据

- MiniMax 中国 `api.minimaxi.com/v1/chat/completions`，用户 Token Plan Key，MiniMax-M2.7：短请求 HTTP 200，4.251 秒。
- 同接口使用 reasoning_split=true：生成杭州一日两个地点的 JSON，HTTP 200，45.684 秒；需要继续用 App 的 Java 解析器和适配器复验。
- 当前新官方文档中的 api.minimax.cn 在本机一次请求 50 秒超时，未作为默认地址；保留已经实测成功的中国 api.minimaxi.com。
- 百度智能搜索：补充 deepseek-v4-flash 模型、baidu_search_v2、关闭深搜索，HTTP 200，3.765 秒，答案及 10 条来源。
- 高德 REST 搜索：HTTP 200，业务码 10009 USERKEY_PLAT_NOMATCH（Key 平台类型不匹配）。
- 腾讯 REST 搜索：HTTP 200，业务码 199（未开启 WebService API）。
- 百度地图 REST 搜索：HTTP 200，业务码 240（APP 服务被禁用）。
- 2026-09-19 用户澄清：三家提供的都是 Android 平台密钥。上述 REST 结果不能用于判定 Android 密钥失效；此前要求修复 Android Key 的判断撤回。恢复旧版高德 Android SDK 搜索及构建密钥注入；腾讯、百度现有 REST 能力另需 Web 服务密钥，已向用户请求。

## 当前实现分工

- 主 Agent：ApiHttp 中文 HTTP/网络分类；真实受控请求、Java/Android 验证和最终整合。
- Terra：AiProviders / AiResponse / AiSettingsUi，国内 MiniMax、分离推理、响应错误处理与内联配置错误。
- Terra：BaiduSearch / AiExploreUi，请求模型及来源解析。
- Terra：MapErrors / MapService / MapRoutes / AdvancedSettingsUi，平台错误码及验证表单。

## 私有配置与输出

真实凭据仅在仓库外 `../../work/config/stage3-credentials.json`，设置仅当前 Windows 用户和 SYSTEM 可读写。不得打印或复制进源码。AI 私密凭据不得进入 APK；高德 Android SDK 的应用绑定密钥需要在本机构建时注入 Android 清单，以包名及签名约束使用。
受控探测脚本和脱敏响应位于仓库外 `../../work/stage3-*`。
凭据只在本机请求官方服务，不随备份导出，不上传源码仓库。

## 官方依据

- https://platform.minimaxi.com/docs/token-plan/other-tools
- https://platform.minimaxi.com/docs/api-reference/text-openai-api
- https://platform.minimax.cn/docs/api-reference/text-openai-api
- https://cloud.baidu.com/doc/qianfan-api/s/Hmbu8m06u
- https://lbs.qq.com/service/MCPServer/MCPServerGuide/overview
- https://developer.amap.com/api/mcp-server/summary

MCP 评估：腾讯 MCP 依赖 WebService 权限与额度，不能绕过当前 199。当前 App 使用 REST 搜索/路线；MCP 链路不宣称已验证成功，不混用 MCP URL 和 REST Key。

完成本阶段后应询问是否继续第 4 阶段，不能自动开始 WebDAV 或发布。

## 2026-09-19 Android 密钥纠正与复验

- 用户确认高德、腾讯、百度所给地图凭据均为 Android 平台。未将其迁移至 Web 服务配置。
- 对照旧提交 029bdc5 恢复高德清单密钥注入（本机文件或环境变量），同时补齐 Gradle placeholder；源码不包含实际密钥。
- MapSearchUi 的高德分支恢复 AmapPlaceSearch / PoiSearchV2；高德链接资料补全恢复 AmapDetails 原生 SDK，保留用户同意门控及拒绝后的基础导入。
- 腾讯、百度现有实现仍为 REST；高德 Web 路线和 AI 地图参考也需独立 Web Key，已向用户请求，未宣称这些功能真实验证通过。
- AdvancedSettingsUi 明确区分内置 Android SDK 与可选 Web 配置；验证失败持续显示在表单，验证时锁定输入及清除按钮。
- stage3 APK 构建、签名及安装成功。版本暂维持 0.4.0/code10，仅用于分阶段内部验证，不发布。
- 使用本次构建的 Java 适配器真实复验：MiniMax 中国 minimaxi.com / M2.7 生成 1 日 2 地点并由 AiPlan 成功解析，4.119 秒。首次尝试收到 IllegalArgumentException（未捕获响应，不能确定具体字段原因）；第二次正常，不能声称模型每次都返回有效格式。
- 百度智能搜索通过 App 的请求/响应适配器返回正文及来源，5.817 秒。
- 本次独立运行通过 42 项断言：AiResponse 10、BaiduSearch 12、MapErrors 7、ApiHttpError 13。
- Astra 限定范围 review 未发现阻止集成的问题。高德真实 SDK 探针及 Android 集成回归待记录。

## 2026-09-20 最终复验

- 新凭据按平台分开保存在仓库外受限配置中；高德安全密钥没有发送给 REST 接口，也没有进入源码或 APK。
- 高德 Android SDK 使用当前包名、调试签名和 Android Key 实际搜索“杭州 / 博物馆”，成功返回 20 条结果。
- 高德“Web 端 Key”调用 Web 服务地点搜索和驾车路线均返回业务码 10009，确认它不是当前 REST Web 服务类型；安全密钥属于浏览器端接入配置，不能用于修复 REST 平台类型。
- 腾讯 Key 的地点搜索实际返回业务码 0，并获得 2 条结果；当前已具备 WebService 搜索权限。
- 百度浏览器端 AK 调用服务端地点搜索返回业务码 240；浏览器端 AK 不能替代当前 REST 所需的服务端能力。
- 首轮原生探针因测试探针缺少 Instrumentation 启动入口而挂起，且与整套回归并行时互相终止应用进程；该结果作废。补齐启动入口并改为串行后得到上述高德成功结果。
- 最终整套 Android 回归串行通过 226 项断言。第 3 阶段不发布，等待用户确认后再进入第 4 阶段。

## 2026-09-20 服务端密钥补充

- 用户补充高德“Web 服务”类型 Key 与百度地图“服务端”类型 AK，均只写入仓库外受限配置；仓库扫描未发现凭据，ACL 仅允许当前 Windows 用户与 SYSTEM。
- 使用 App 当前 `MapService` / `MapRoutes` / `ApiHttp` 调用链真实复验：高德地点搜索返回 20 条，驾车路线包含有效距离和时长；百度地点搜索返回 20 条，驾车路线包含有效距离和时长。
- 先前的高德浏览器 Web Key、浏览器安全密钥和百度浏览器 AK 保留作类型记录，不用于 REST 服务端调用。
- 本次没有修改 App 源码或重新构建 APK；当前阶段构建和 226 项 Android 回归结果仍有效。
