# 平台操作日志与 AI 调用可观测性

## 1. 目标与范围

平台操作日志统一展示三类活动：

- `HUMAN`：用户登录成功后发起的认证 API 读取、写入与退出操作，以及登录成功事件。
- `AGENT`：AI Agent 经 `ToolGateway` 产生的工具决策与调用记录。
- `INTEGRATION`：平台经受控接口中心发起的系统集成调用。

运行日志页面通过 `GET /api/admin/runtime-logs` 及详情接口读取统一视图。接口继续要求
`route:runtime-logs` 与 `runtime-log:read`，并按当前认证租户查询；前端权限不能代替后端鉴权。

独立的 `/oa/platform-observability` 页面通过
`GET /api/admin/platform-observability/overview?range=24h|7d|30d` 展示同一视图的脱敏聚合。
该接口要求 `route:platform-observability` 与 `runtime-log:read`，由服务端重新解析实时权限和租户。
响应包括时间桶调用量、失败与拦截、来源分布、高频错误码、平均及 P95 耗时。
空时间桶不伪造事件；无数据时显示明确空态。时间范围固定为 24 小时、7 天或 30 天。
图表仅代表运行日志已有事实，不冒充 CPU、内存或基础设施可用性监控。

本能力用于平台运维和业务追踪，不替代不可抵赖的合规审计、基础设施日志或 SIEM。

## 2. 数据模型

Flyway 迁移 `V202609221600__platform_operation_observability.sql` 新增
`platform_operation_log`，记录以下有界元数据：

- 租户、用户和显示标签；
- `LOGIN`、`HTTP_READ`、`HTTP_WRITE`、`LOGOUT` 事件类型；
- HTTP 方法和受限长度的请求路径；
- `SUCCEEDED`、`REJECTED`、`FAILED` 结果；
- 状态码、耗时、客户端 IP 和浏览器标识；
- request ID、trace ID、低敏错误代码及开始、完成时间。

`runtime_log_view` 使用 `CREATE OR REPLACE VIEW` 合并三类既有事实来源：

| 来源 | 主体类型 | 事实表 | 事件 |
| --- | --- | --- | --- |
| `HUMAN` | `HUMAN` | `platform_operation_log` | 登录、读取、写入、退出 |
| `AGENT` | `AI_AGENT` | `agent_tool_invocation` | AI 工具调用 |
| `INTEGRATION` | `SYSTEM` | `integration_invocation` | 受控接口调用 |

AI 调用继续复用 `agent_tool_invocation`，不重复写第二份 AI 审计。统一视图只负责查询，
不会改变 `ToolGateway`、领域事务、确认、权限或工具发布门。

## 3. 采集边界

- 登录成功和失败由认证入口显式记录，避免认证前请求没有可信用户上下文。
- 登录失败不保存原始账号，只保存标准化账号的 SHA-256 截断摘要；失败记录没有租户归属，
  不进入普通租户的运行日志查询。
- 登录后的 `/api/**` 请求由安全过滤器在请求结束时记录；`OPTIONS`、非 API 请求和登录入口
  不重复采集。
- 读取请求标记为 `HTTP_READ`，其他认证请求标记为 `HTTP_WRITE`，退出入口标记为 `LOGOUT`。
- AI 工具来源于网关调用事实表，因此包括允许、拒绝、执行中、成功和失败等真实状态。
- 集成调用只来自服务端白名单端点；该日志能力不会增加任意 URL 调用能力。

平台操作日志禁止保存：

- 请求体和响应体；
- 密码、邮箱验证码、JWT、Cookie、Authorization Header 或 API Key；
- 完整异常堆栈、数据库连接串、对象存储地址或其他内部服务地址；
- AI 工具原始参数。AI 行只暴露现有哈希和有界安全摘要。

客户端 IP 仅用于安全追踪。反向代理部署时必须只信任受控代理写入的转发头，禁止让公网客户端
直接伪造来源地址。

## 4. 页面展示

`/oa/runtime-logs` 显示并可筛选：

- 人为操作、AI 自助调用、系统集成三类来源；
- 平台用户、AI Agent、系统服务三类主体；
- 登录、读取、写入、退出、接口调用和 AI 工具调用事件；
- 操作人、结果、耗时、时间、引用编号和安全详情。

详情抽屉可显示客户端 IP 与浏览器标识，但不显示受保护载荷。所有新增页面文案均提供
`zh-CN` 和 `en-US`。

## 5. 验证证据

- 后端定向测试覆盖登录成功/失败、认证 API 采集、敏感字段缺失、统一视图和租户查询。
- 后端完整测试：980 项通过，0 failures，0 errors；9 项既有 Testcontainers 环境测试因本机
  Docker daemon 不可用而跳过。
- 真实 PostgreSQL 16 验证覆盖空库迁移、既有库增量升级、99 个迁移 validate、二次启动零迁移，
  并确认人为登录行和 `AI_AGENT` 视图分支可查询。
- OA 测试：36 个文件、114 项通过；组件测试同时渲染人为登录和 AI 工具调用记录。
- OA lint：0 errors，3 个任务外既有 React Hooks warnings；生产构建通过。

## 6. 部署与回滚

部署顺序：

1. 备份 PostgreSQL，并在预发布环境执行 Flyway `validate`。
2. 部署后端，让 Flyway 应用 `V202609221600`。
3. 查询 `flyway_schema_history`、`platform_operation_log` 和 `runtime_log_view`，确认版本与列一致。
4. 部署 OA 前端，使用具备 `runtime-log:read` 的测试用户验证人为、AI 和集成三种来源。
5. 再次重启后端，确认无 Pending 迁移和 checksum 异常。

迁移为前向兼容新增表并替换视图。代码需要回滚时使用 `git revert`，不要删除或修改已执行的
Flyway 文件。旧版本若不能识别扩展后的视图列，应先通过更高版本迁移恢复兼容视图，再回滚应用；
历史日志保留，禁止通过代码回滚自动删除。需要移除新表时也必须另建更高版本迁移，并在备份和
留存策略确认后人工执行。
