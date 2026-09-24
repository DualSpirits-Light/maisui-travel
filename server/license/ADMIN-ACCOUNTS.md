# 第七阶段：管理员初始化、恢复与迁移

本阶段仅提供待发布实现和本地验证；下列远端操作须在统一发布获准后执行。

## 存储与会话

`0004_admin_accounts.sql` 只新增 `admin_accounts`、`admin_sessions`，不改动授权、设备或分享记录。管理员使用一个固定管理账户。密码为 12–128 个字符，不去除前后空格；使用随机 192 位盐、HMAC-SHA256 服务端 pepper 和 PBKDF2-SHA256（100,000 次）派生。D1 只保存盐、派生值和迭代数。该工作因子受 [Workers PBKDF2 上限](https://github.com/cloudflare/workerd/issues/1346)约束；应使用密码管理器生成的强密码。pepper 使用既有 `CODE_PEPPER`，不要为改密而轮换它，否则会破坏现有授权码查找。

会话使用 256 位随机值，数据库只保存 SHA-256 摘要；浏览器使用 `__Host-lvxu_admin`、Secure、HttpOnly、SameSite=Strict、Path=/ cookie，绝对有效期 8 小时。写请求验证同源 Origin；退出撤销当前会话，改密和恢复原子增加会话版本，全部旧会话立即失效。过期或旧版本行在后续成功登录时清理。

登录、恢复、改密各每地址每分钟最多 5 次；登录和恢复还有全局每分钟 100 次总限额，管理请求仍保留每地址总限额。返回 429 时提示稍后重试。计数使用 Cloudflare 注入的客户端地址摘要，不存储原始地址。

## 首次设置与忘记密码

1. 登录仍受控的 Cloudflare 账户，选择实际 Worker。用密码管理器生成新的至少 32 字符随机秘密，在 Worker 的加密 Secrets 中保存为 `ADMIN_RECOVERY_TOKEN`。也可以在受控终端交互运行 `npx wrangler secret put ADMIN_RECOVERY_TOKEN`。不要把值写在命令参数、仓库、文档、截图或工单中。
2. 打开 HTTPS 管理门户 `/console`，展开“首次设置 / 忘记密码”，填写该恢复凭据和新的管理员密码。
3. 点击设置后，恢复凭据的摘要被记录为已使用，重复使用会失败；所有旧会话撤销。随后使用新密码登录。
4. 验证授权列表、原有设备和捐赠记录完整。可从 Cloudflare 移除 `ADMIN_RECOVERY_TOKEN`，日后忘记密码时重新生成一个全新的值。不要重复使用历史恢复凭据。恢复不需要旧密码或旧 `ADMIN_TOKEN`。

若 Cloudflare 管理权和独立恢复凭据同时丢失，须先通过 Cloudflare 账户恢复途径恢复控制权；公开门户不会提供绕过认证的恢复入口。

初始化前，旧 `ADMIN_TOKEN` API 自动化暂时可用；初始化后该旧管理入口自动拒绝，不影响已有终端授权或设备密钥。门户不再收集旧令牌配置文件。`ADMIN_TOKEN` 已不属于 Worker 必需配置；初始化验证通过后应移除它。

## API

所有写请求均使用 JSON。浏览器自动发送同源 Origin；受控脚本须显式设置为服务自身的 HTTPS origin。

| 方法与路径 | 内容 | 结果 |
| --- | --- | --- |
| POST `/admin/auth/recover` | `Authorization: Bearer <新恢复凭据>`，`{password}` | 初始化或恢复，清除 cookie，撤销全部旧会话 |
| POST `/admin/auth/login` | `{password}` | 设定受保护 cookie |
| GET `/admin/auth/session` | 会话 cookie | 验证当前会话 |
| POST `/admin/auth/password` | 会话 cookie，`{currentPassword,password}` | 改密，撤销全部会话 |
| POST `/admin/auth/logout` | 会话 cookie，`{}` | 撤销当前会话 |

不要把 cookie 或密码输出到日志。恢复操作依赖 Cloudflare Secrets 的管理权，是独立于日常密码的应急权限。

## 备份、发布与回滚

1. 暂停后台写入，在仓库外受限目录备份现有 D1 数据（含迁移记录），记录现有 Worker 版本和数据库时间点。示例：`npx wrangler d1 export maisui-license --remote --output <仓库外受限备份路径>`。备份可能含授权摘要、设备摘要与捐赠信息，不纳入公开产物。
2. 在隔离的本地 D1 应用所有迁移，确认原有授权/设备/分享行数和关联关系保持不变，再运行 `npm test`。
3. 获准正式发布后，先应用增量迁移，再部署 Worker。随后执行上述初始化；检查未登录拒绝管理、旧令牌拒绝、登录/改密/恢复与公开捐赠数据。
4. 如发布失败，优先回滚 Worker 代码，保留新增表以防信息丢失。旧代码仍要求 `ADMIN_TOKEN`：应先在 Cloudflare 设置全新的随机临时值，限制后台入口，不能重新启用已丢失或可能泄露的旧值。密码账户在旧代码中不可用。
5. 回滚旧 Worker 会恢复旧令牌认证模式，因此必须明确验证访问控制；新增账户、会话和捐赠表可保留。禁止直接删除新增表或整个数据库来“回滚”，也不要直接恢复全库覆盖新产生的授权/捐赠数据。若确需数据库还原，先导出发布后数据，与发布前备份对比并制定合并方案。
6. 再次升级前检查管理员表与会话版本，使用全新恢复凭据执行恢复，以撤销回滚前可能仍存在的会话。

本地测试覆盖的 SQLite 语义不等同于已完成线上部署；发布前仍需隔离 Workers 运行时和实际 HTTPS 浏览器验证。
