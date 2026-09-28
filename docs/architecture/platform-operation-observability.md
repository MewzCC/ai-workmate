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

### 看板验收

开发库有真实运行记录时，打开 `/oa/platform-observability`，依次切换近 24 小时、7 天、30 天，
检查汇总卡、流量、异常、来源和错误码图表。图表类型、显示内容、卡片顺序与时间粒度应可切换并保存；
趋势点下钻到运行日志，时段对比、视觉阈值和受权 CSV 导出分别验证。
没有相应记录的图表应显示真实空态，不填充演示数据；接口失败时显示占满工作区的错误态和重试入口。
`RuntimeLogMapperPostgresIntegrationTest` 在真实 PostgreSQL 中插入并回滚一条测试日志，覆盖汇总、
P95、小时/天趋势、来源和错误码，避免仅靠 Mock 测试漏掉 SQL 参数绑定错误。
该测试需要设置 `OBSERVABILITY_TEST_DB_URL`、`OBSERVABILITY_TEST_DB_USERNAME` 和
`OBSERVABILITY_TEST_DB_PASSWORD` 指向**非生产**库；未配置时跳过，不能作为数据库验收通过的证据。

图表个人配置通过 `GET/PUT /api/admin/platform-observability/preferences` 读取和保存。
请求体为 `{ "charts": [{ "id", "kind", "title", "mode", "content", "size", "granularity" }] }`。
`kind` 仅允许 `volume`、`risk`、`source`、`error` 四类真实运行日志指标；每人可保留 1–12 张卡片，
`id` 在当前配置中唯一，列表顺序即页面顺序。可从受控目录添加、复制或移除卡片，标题最多 40 字符；
`size` 仅允许 `normal` 或 `wide`，趋势图时间粒度仅允许 `auto`、`hour` 或 `day`。
服务端在每次读写时重新校验上述两项权限，并逐图校验类型、可显示内容及错误码长度，
拒绝任意指标或任意查询表达式。旧版四字段配置可读取，服务端会补全 `kind` 和默认时间粒度。
`GET /api/admin/platform-observability/timeline?from=...&to=...&interval=hour|day`
仅用于非默认时间粒度的趋势图，服务端限制跨度不超过 31 天并按当前认证租户聚合。

图表点击通过真实路由进入 `/oa/runtime-logs`。趋势点传递与聚合一致的时间桶：
起点取时间桶与总范围较晚者，非末桶使用右开边界 `toExclusive=true`，末桶沿用总范围结束时间；
流量图传 `source`，异常图传 `group=FAILED|BLOCKED`，来源构成图传 `source`，错误码图传精确 `errorCode`。
日志接口的服务端 SQL 与统计汇总使用相同的租户、来源、异常分组、错误码和边界条件；
错误码仅匹配失败、超时、无效结果或拒绝，与高频错误码聚合口径保持一致。
新参数不会授予访问权限：`route:runtime-logs` 与 `runtime-log:read` 仍需实时满足。

`GET /api/admin/platform-observability/comparison?range=24h|7d|30d`
返回本期与紧邻的上一个等长时段。上期结束采用右开边界，本期结束采用右闭边界，
保证交界时刻不会双重计数。调用量、失败数和平均耗时来自同一租户运行日志聚合；
前端仅在用户打开“时段对比”后加载，失败时显示可重试错误，不回退静态数值。
当某期没有记录时，失败率或耗时显示不可比，不计算除以零的增长率。

个人视觉阈值通过 `GET/PUT /api/admin/platform-observability/thresholds` 读写，
保存在现有 `user_setting` 的 `observability.thresholds` 键中，不新增偏好表。
仅允许配置失败数、拒绝／拦截数与 P95 耗时的正整数阈值；`null` 表示关闭。
三个阈值分别有服务端上限，读写均校验实时观测权限。达到阈值仅在当前页面高亮真实汇总卡片；
它不是监控告警，不创建后台任务、不发送通知，也不改变运行日志或业务状态。
每张已保存图表可通过 `POST /api/admin/platform-observability/export` 导出当前选定的
`24h`、`7d` 或 `30d` 区间 CSV，请求仅包含 `{ "range", "chartId" }`。
服务端重新解析实时租户及 `route:platform-observability`、`runtime-log:read`、`data:export`
三项权限，只从用户已保存并验证过的图表配置中选取该卡片，重新聚合当前租户数据。
导出仅包含所选来源、风险类别或错误码，最多 3000 行；CSV 加 UTF-8 BOM，并转义公式前缀。
每次成功导出写入业务审计。页面配置尚未保存、读取失败或用户缺少导出权限时禁用或隐藏入口；
导出时间点重新查询，因此可能与之前渲染的自动刷新快照略有差异。
配置按认证用户写入现有 `user_setting`，不保存租户业务数据；权限被撤销后接口立即拒绝。
`V202609231755__observability_preference_value.sql` 仅将该表的 `setting_value` 从
`VARCHAR(255)` 扩为 `TEXT`，用于容纳受控图表卡片的 JSON。旧设置仍可读取，旧程序也无需新字段。

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

图表偏好功能可单独回滚应用代码；`TEXT` 扩容不应降级回 `VARCHAR(255)`，否则可能截断已经保存的
用户配置。部署该功能时先应用迁移，再发布后端接口和 OA 前端。
