# 美团旅行查询代理

2026-10-08：此接口仅查询旅行信息，不实现预订、支付或订单操作。Android 包只带固定代理地址，开发者凭据只保存在 Worker Secret `MEITUAN_TRAVEL_TOKEN`。客户端反编译可以发现代理地址；不能据此获得服务端美团凭据。设备凭据泄露仍可能消耗该授权额度，因此服务端限制授权状态、每日预算和并发。

`POST /v1/travel/query`，HTTPS、`Content-Type: application/json`：

```json
{
  "licenseId": "existing-license-id",
  "deviceId": "existing-device-uuid",
  "deviceSecret": "existing-device-secret",
  "city": "杭州",
  "query": "推荐适合亲子的一日游"
}
```

使用 `/v1/activate` 已签发并由 `/v1/refresh` 使用的同一设备凭据，不接受客户端自报已激活或离线授权声明。沿用 SHA-256 `devices.secret_hash` 与常量时间比较；在原子额度预留时再次检查数据库中设备及授权，撤销、归档、冻结、过期均不可查询。

成功：`{"content":"旅行建议文本","source":"美团旅行"}`。失败：`{"error":{"code":"TRAVEL_UNAVAILABLE","message":"旅行查询暂不可用，请稍后重试"}}`。错误码包含 `INVALID_REQUEST`、`INVALID_CREDENTIAL`、`LICENSE_REVOKED`、`LICENSE_FROZEN`、`LICENSE_EXPIRED`、`RATE_LIMITED`、`TRAVEL_LIMIT_REACHED`、`TRAVEL_UNAVAILABLE`、`TRAVEL_TIMEOUT`。错误不透传上游响应、异常或凭据。结果为第三方文本，客户端应作为文本显示，不能直接作为可信 HTML 执行。

完整 JSON 请求最多 4096 UTF-8 字节，城市最多80字符，查询最多1500字符；中文长查询通常先触及字节限制。上游固定为 `https://mcp-open-cater.meituan.com/v1/api/voyage/openapi/query`，使用原始 `Authorization` Secret。只发送 `city`、`query`、与 query 相同的 `originQuery` 和固定 `channel: meituan-developer`；不发送本应用设备凭据。使用 manual 模式且拒绝所有非 2xx 响应，禁止跟随重定向，无自动重试，仅接受 `code: 0` 和非空字符串 `data`。响应最多256KiB，默认总上游超时110秒。

## 部署顺序

1. 按既有流程备份生产D1并应用增量迁移 `0007_meituan_travel.sql`；不重写0001–0006。
2. 通过 Worker Secret 管理流程配置 `MEITUAN_TRAVEL_TOKEN`，不写入 wrangler.toml、源码、日志、Android资源或发布附件。
3. 部署兼容的新 Worker；未配置凭据时接口返回503，其余接口不受影响。
4. 使用有效设备凭据做一次受控线上查询，另验无凭据拒绝；不能把本地模拟上游测试当作真实美团成功。

可选变量（缺失或非法值回退默认）：

| 变量 | 默认 | 上限 |
| --- | ---: | ---: |
| TRAVEL_LICENSE_DAILY_LIMIT | 10 | 1000 |
| TRAVEL_GLOBAL_DAILY_LIMIT | 100 | 10000 |
| TRAVEL_GLOBAL_CONCURRENCY | 4 | 20 |
| TRAVEL_TIMEOUT_MS | 110000 | 115000 |

每日按UTC零点重置。每个授权最多一个并发；额度、全局并发与授权条件由同一D1 INSERT原子判断，跨Worker实例生效。每IP另限制10次/分钟。上游失败和超时也消耗一次预算，防止失败重试绕开成本上限。结束后释放并发槽；意外终止的槽150秒后失效。预留表不保存查询、设备Secret或美团Token；不级联删除额度，删除授权不会退回全局预算。正常请求顺带清除两天前的历史预留；当前额度不会受清理影响。

回退可以部署上一版Worker；保留新增表即可，不要为回退删除已有授权数据。已预留的预算不自动退还。

## 本地验证

2026-10-08：`node --test` 共69项通过，其中新增14项覆盖固定上游契约、设备认证、冻结/撤销/过期、输入限制、每日全局/授权额度、并发、并发期间撤销、失效锁恢复、上游失败/超大响应、超时取消、缺少Secret。真实生产/真实美团请求由部署阶段另行记录。
