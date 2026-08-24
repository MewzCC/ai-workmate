# Phase 2（Agent）任务分解

> 依据 `docs/roadmap/phase-2-agent-plan.md` 与强制安全附件 `docs/roadmap/phase-2-agent-security-boundary.md` 修订。先冻结契约和能力上限，再并行建设工具与任务引擎；Phase 2A 验收通过后才进入 Phase 2B。估时为一名人工负责人使用 Codex/AI 协作的 Vibe Coding 有效工作日，不等于传统纯人工人日。

## 模块 0：契约冻结与安全基线（约 1~2 个有效工作日）

| 编号 | 任务 | 内容 | 产出 | 依赖 |
| --- | --- | --- | --- | --- |
| P1 | API 契约冻结 | 定义 plan、taskId 路径 execute、任务列表/详情/取消、SSE 的请求响应、202 语义、错误码和鉴权要求 | OpenAPI/架构文档中的接口契约 | - |
| P2 | 状态机冻结 | 明确 L0 与 L1/L2 分支、合法状态迁移、可取消状态、终态、超时和部分成功语义 | 状态转换表 + Mermaid 图 | - |
| P3 | 安全附件冻结 | 逐项确认自治等级、永久禁止能力、运行上限、Prompt Injection、越权、重放、跨租户、敏感数据留存和发布门 | security-boundary 评审结论 + 测试映射 | P1/P2 |
| P4 | 现有接口迁移策略 | 删除 execute 的管理员角色硬编码，确定旧 `/execute` 到 `/{taskId}/execute` 的同版本迁移方式，不保留伪成功兼容层 | 迁移说明 | P1 |
| P5 | Vibe Coding 防降级规则 | 建立安全失败测试清单；禁止为通过联调放宽 SecurityConfig、扩大 schema、启用 mock 或删除权限校验 | 实施检查表 | P3 |

## 模块 A：工具注册表与只读工具（Phase 2A，约 2~3 个有效工作日）

| 编号 | 任务 | 内容 | 产出 | 依赖 |
| --- | --- | --- | --- | --- |
| A1 | agent_tool 表与种子数据 | 新增 Flyway 迁移创建 `agent_tool`；JSONB schema、平台/租户部分唯一索引、风险/权限 CHECK；租户覆盖只能收窄能力；幂等插入 4 个 L0 工具 | 新的 `V*__agent_tool_registry.sql` | P2/P3 |
| A2 | 实体与 Mapper | `AgentTool` 实体和 Mapper；所有查询显式带 tenant 范围，不提供不受控的仅 code 查询 | 后端类 + Mapper 测试 | A1 |
| A3 | 代码注册表 | `ToolDefinition`、`ToolHandler`、handlerVersion、schemaHash、风险与权限策略；代码元数据作为安全权威来源，启动时校验数据库配置不能扩大权限 | 工具框架 + 启动校验测试 | A2 |
| A4 | 输入/输出校验 | JSON Schema 输入与输出校验；`additionalProperties=false`；分页/长度/数组/嵌套/结果大小上限；敏感字段过滤和统一错误 | 校验组件 + 单测/属性测试 | A3 |
| A5 | 白名单与实时权限 | `resolveAllowedTools(userId, pageId)` = 代码注册 ∩ 平台启用 ∩ 租户启用 ∩ 实时权限 ∩ 页面能力；route 权限与业务 action 权限分离 | ToolRegistry/Policy 服务 + 权限测试 | A3 |
| A6 | 四个只读工具 | `todo.query`、`leave.mine`、`knowledge.search`、`notification.mine`；复用领域 Service，不直接调用 Mapper；所有资源仅限当前认证用户和租户 | 4 个 handler + 单元测试 | A4/A5 |
| A7 | 工具安全测试 | 跨用户、跨租户、schema 类型混淆/未知字段、输出超限、数据库风险配置篡改、知识内容 prompt injection、工具版本变化；验证永久禁止能力不能注册 | 安全回归测试 | A6 |

## 模块 GW：Tool Gateway 安全网关（Phase 2A，约 2~3 个有效工作日）

| 编号 | 任务 | 内容 | 产出 | 依赖 |
| --- | --- | --- | --- | --- |
| GW1 | 网关最小合约 | `ToolGateway.execute(stepId, workerLease)`；禁止 caller 提交 user/tenant/toolCode/args/risk/permission；定义 ALLOW/DENY/STALE/THROTTLED/UNAVAILABLE | ToolGateway 接口与安全 DTO | P2/P3/A3 |
| GW2 | 调用决策审计表 | 新增 Flyway 迁移创建 `agent_tool_invocation`；task/step/attempt/decisionId/toolVersion/planHash/argsHash/decision/trace/耗时及查询索引 | 新的 `V*__agent_tool_gateway.sql` + Entity/Mapper | GW1 |
| GW3 | 前置决策管线 | 依次校验 Kill Switch、租户用户、task/step 状态、Worker 租约、attempt、不可变哈希、实时 RBAC、永久禁止清单、风险/确认、预算、inputSchema 和资源预检 | ToolGateway 实现 + 决策单测 | GW1/GW2/A5 |
| GW4 | 唯一 handler 分派 | handler 映射只由网关持有；网关创建 ToolExecutionContext；禁止通用 execute(toolCode,args,userId)、管理员代执行和公共 HTTP 网关 | 内部 HandlerResolver + 包可见性约束 | GW3 |
| GW5 | 输出与审计闭环 | handler 前必须成功写 ALLOW/DENY 决策；执行后校验 outputSchema、限量、脱敏并记录结果；关键依赖异常 fail closed | 调用审计与结果过滤测试 | GW3/GW4 |
| GW6 | 防绕过架构测试 | ArchUnit 限制只有 gateway 包可依赖 handler；Controller/Planner/Task Service/Worker 直接注入 handler 或 Mapper 的测试失败 | 架构测试 | GW4 |
| GW7 | 网关攻击测试 | 伪造 stepId、跨租户、过期 lease、错误 workerId/attempt/hash、确认重放、权限回收、限流异常、审计失败；全部验证 handler 调用次数为 0 | 网关安全回归集 | GW5/GW6 |

## 模块 B：持久任务引擎与 Worker（Phase 2A，约 4~6 个有效工作日）

| 编号 | 任务 | 内容 | 产出 | 依赖 |
| --- | --- | --- | --- | --- |
| B1 | 任务表结构 | 新增 Flyway 迁移创建 agent_task、agent_task_step、agent_task_event；外键、CHECK、幂等域、taskId+seq 唯一约束和列表/领取/过期索引 | 新的 `V*__agent_task_engine.sql` | P1/P2/P3 |
| B2 | 实体与 Mapper | 三张表实体、Mapper；状态+version 条件更新、事件按 eventId 查询、任务所有权查询、Worker 原子领取 SQL | 后端类 + Mapper 测试 | B1 |
| B3 | 状态机服务 | RECEIVED/PLANNING/PLAN_READY/WAITING_CONFIRMATION/QUEUED/RUNNING/终态转换；取消、超时、非法流转拒绝 | 状态机 + 完整转换表单测 | B2 |
| B4 | 幂等服务 | 解析 `Idempotency-Key`；唯一域为 tenant+user+operation+key；计算 requestHash；同 key 异请求返回冲突 | 幂等组件 + 并发测试 | B2 |
| B5 | 确认凭证 | L1/L2 在用户确认后签发随机 token；凭证绑定 task/user/tenant/planVersion/planHash/过期时间，仅存哈希；重新签发使旧 token 失效；凭证消费和入队原子更新 | 确认组件 + 重放/并发/过期测试 | B3 |
| B6 | 异步任务投递 | execute 事务提交后唤醒数据库队列 Worker；不得在 Controller 或 plan 事务中同步执行工具 | 投递器 + Worker 调度 | B3/B4 |
| B7 | Worker 租约与恢复 | 原子领取、workerId、leaseUntil、heartbeat、最大尝试次数；重启后回收过期租约；非幂等写工具不自动重试 | Worker + 重启恢复测试 | B6 |
| B8 | 步骤执行器 | Worker 只把 stepId + lease 交给 Tool Gateway；禁止直接调用 handler；根据网关决策更新步骤状态，部分成功进入人工处理提示 | StepExecutor + 测试 | B7/GW5 |
| B9 | 持久事件与 SSE | 写入 task event；SSE 支持 snapshot、进度、终态、错误、heartbeat 和 Last-Event-ID；订阅前校验任务所有权 | 事件服务 + SSE 端点 + 测试 | B2/B8 |
| B10 | 超时与清理 | 实时工具超时、确认过期、任务超时、过期事件和留存数据清理；定时任务只作兜底 | Cleaner + 测试 | B3/B5/B7/B9 |
| B11 | 引擎安全与并发测试 | 并发 execute/确认/领取、服务重启、错误租约恢复、跨用户查询和订阅、不可重试写步骤 | 集成测试 | B3~B10 |
| B12 | 运行预算与 Kill Switch | 实施用户/租户限流、并发任务、步骤数、调用次数、结果大小和超时上限；实现全局/租户/写工具/单工具关闭；依赖异常 fail closed | 配置、限流和故障演练测试 | B3/B6/B9 |

## 模块 C：Planner、Policy Guard 与 API（Phase 2A，约 3~4 个有效工作日）

| 编号 | 任务 | 内容 | 产出 | 依赖 |
| --- | --- | --- | --- | --- |
| C1 | DTO 与错误码 | plan/execute/列表/详情/取消/SSE DTO；Bean Validation 使用 i18n key；新增权限变化、工具版本变化、确认过期、幂等冲突和 GATEWAY_DENIED/STALE/THROTTLED/UNAVAILABLE | DTO + MessageSource zh/en | P1/GW1 |
| C2 | 页面上下文过滤 | pageId 对应字段白名单，限制 pageContext 类型、长度、深度和总大小；禁止接收 role/tenant/permission 等可信声明 | PageContextSanitizer + 测试 | P3 |
| C3 | 计划生成服务 | 组装最小上下文、白名单工具和数据范围摘要；ChatClient 结构化输出；解析失败最多重试一次；记录 model/promptVersion/latency/token | AiPlanner + 单测 | A5、B3、C2 |
| C4 | Policy Guard | 校验工具、版本、schema、实时权限、数据范围、风险、预算和永久禁止清单；与 Tool Gateway 共享版本化安全策略定义，但执行时网关必须独立重检 | PolicyGuard + 策略一致性测试 | C3/GW1 |
| C5 | plan API | 创建持久任务与步骤，支持 plan 幂等，返回 taskId/status/planHash/planVersion/风险/确认要求；plan 不签发确认凭证 | Controller + WebMvc/集成测试 | B3/B4、C4 |
| C6 | 确认凭证 API | `POST /{taskId}/confirmation-token`；仅 WAITING_CONFIRMATION 可调用；复核计划与实时权限；重新签发使旧 token 失效；按用户和任务限流并审计 | Controller + 安全测试 | B5、C4 |
| C7 | execute API | `POST /{taskId}/execute`；校验所有权、幂等、计划和实时权限；L0 直接入队，L1/L2 原子消费凭证后入队；返回 202 和查询/SSE URL | Controller + 集成测试 | B4/B5/B6、C4 |
| C8 | 任务查询与取消 API | `GET /tasks`、`GET /tasks/{id}`、`POST /tasks/{id}/cancel`；按 tenant+user 过滤，分页与结果脱敏 | Controller/Service + 安全测试 | B3/B9 |
| C9 | SSE API | `GET /tasks/{id}/events`，Accept-Language、Last-Event-ID、所有权、心跳、终态关闭和流中错误协议 | Controller + SSE 集成测试 | B9、C1 |
| C10 | 攻击与评估用例集 | 每工具覆盖正常、空数据、越权、参数错误、拒答和长上下文；全局增加直接/间接 prompt injection、永久禁止工具诱导、未知字段、类型混淆、权限回收、版本变化、限流、SSE 重连和重启 | 自动化攻击语料 + 手工评估集 | C5~C9/B12 |

## 模块 D：OA 前端闭环（Phase 2A，约 2~3 个有效工作日）

| 编号 | 任务 | 内容 | 产出 | 依赖 |
| --- | --- | --- | --- | --- |
| D1 | API 客户端与类型 | `oaApi.ts` 和 `types/oa.ts` 升级 plan、confirmation-token、taskId execute、列表/详情/取消、202 和 SSE 类型；所有请求携带 JWT 与 Accept-Language | API 客户端 + 类型测试 | C5~C9 |
| D2 | AI Drawer 计划展示 | Ant Design Steps 展示工具、参数脱敏摘要、影响范围和风险；L0 执行按钮、L1/L2 确认按钮；凭证仅在确认后签发并存组件内存，关闭/刷新后重新签发；真实错误态 | AIOperationDrawer 升级 | D1 |
| D3 | 确认与执行体验 | L2 `Modal.confirm`；execute 后订阅 SSE；展示步骤进度、结果和失败；终态关闭连接 | AIOperationDrawer 升级 | D2 |
| D4 | SSE 重连与恢复 | Authorization header 认证且 token 不进 URL；心跳超时、lastEventId、快照、有限退避重连和卸载清理；不得重复 execute | 前端流管理模块 + 测试 | D3 |
| D5 | 我的 AI 任务页 | Ant Design Table 列表、状态筛选、步骤详情、终态结果、失败原因和取消；服务端动态路由通过新 Flyway 迁移写入，前端注册组件和 OaIcon | AiTasksPage + Flyway 路由迁移 | D1/C7 |
| D6 | 国际化与可访问性 | zh-CN/en-US 同步；按钮、状态、错误、SSE 文案使用 t()；确认弹窗与进度区域键盘/aria-live 支持 | i18n 资源 + 可访问性检查 | D2~D5 |
| D7 | 前端测试 | plan 展示、L0 执行、L2 确认、202、SSE 重连、权限/确认过期、任务列表/详情/取消；结果文本 XSS、危险 Markdown 链接和 token 泄露测试 | Vitest 用例 | D2~D6 |

## Phase 2A 发布门（约 2~3 个有效工作日，不得压缩）

| 编号 | 任务 | 内容 | 产出 | 依赖 |
| --- | --- | --- | --- | --- |
| G1 | 数据库验证 | PostgreSQL 空库首次 Flyway、历史库 baseline 后升级、迁移后重启；验证约束、索引、种子数据和租户覆盖；不修改已发布迁移 | 验证记录 | A1/GW2/B1/D5 |
| G2 | 安全与恢复演练 | 普通员工、跨租户、权限回收、网关绕过、伪造租约/hash/attempt、并发 execute、重启、SSE、注入、限流、Kill Switch、审计异常 fail closed | 人工逐项演练记录 | A~D/GW |
| G3 | 工程验证 | Java 17 `mvn test`；两个前端 lint/build；fonted-oa Vitest；zh/en 验收 | 验证记录 | A~D |
| G4 | 人工发布评审 | 人工负责人逐项签署安全附件发布门并确认是否启用 Phase 2B；代码生成 Agent 不得自行判定通过 | 发布门结论 | G1~G3 |

## 模块 E：写操作试点（Phase 2B，约 2~3 个有效工作日）

| 编号 | 任务 | 内容 | 产出 | 依赖 |
| --- | --- | --- | --- | --- |
| E1 | 写工具启用策略 | 租户级开关、L1/L2 风险与权限策略、非幂等重试策略、人工介入文案 | 配置与策略测试 | G4 |
| E2 | leave.createDraft | L1；只允许 Tool Gateway 分派；调用 LeaveWorkflowService 创建本人草稿；使用领域幂等避免 Worker 重试产生重复草稿；写审计 | handler + 网关/领域测试 | E1/B8/GW7 |
| E3 | leave.submit | L2；仅提交执行前已存在的本人有效草稿；不得与 createDraft 同计划；要求 planVersion/planHash/一次性凭证；领域条件更新与 business audit 同事务 | handler + 测试 | E2 |
| E4 | 写操作联合流程 | 计划、确认、原子入队、Worker 执行、SSE、任务详情、审计中心完整可见 | E2E 联调用例 | D3/D5/E3 |
| E5 | 写工具安全测试 | 未确认、凭证重放、计划变化、权限回收、他人草稿、同键异请求、Worker 重启与不可重试失败 | 安全与恢复测试 | E3/E4 |

## 模块 F：观测、数据治理与文档（贯穿，约 1~2 个有效工作日）

| 编号 | 任务 | 内容 | 产出 | 依赖 |
| --- | --- | --- | --- | --- |
| F1 | 观测字段 | 任务记录 plannerModel/promptVersion/latency/token；步骤记录 toolVersion/attempt/latency/traceId；定义指标和告警阈值 | 日志、指标与查询 | B8/C3 |
| F2 | 审计边界 | Tool Gateway 前置决策、执行结果、成功写入、安全拒绝、确认重放、非法 schema、越权和幂等冲突写脱敏审计 | 审计实现 + 查询验证 | GW5/A4/B5/C4 |
| F3 | 数据留存与清理 | input/pageContext/args/result/event 的留存期限、脱敏、清理和人工查询边界 | 配置、Cleaner 与说明 | P3/B10 |
| F4 | 架构文档 | `docs/architecture/agent-task-engine.md`：Tool Gateway、状态机、幂等、确认、Worker 租约、SSE、纵深鉴权和时序图 | 架构文档 | A~C/GW |
| F5 | 规则与 Skill 更新 | 同步 AGENTS.md、agent-rules.md、agent-engineering-skill.md 与 OA skill 中的新接口和任务引擎约定 | 规范文档 | F4 |

## 汇总

| 模块 | Vibe Coding 有效工作日 |
| --- | ---: |
| 0 契约与安全基线 | 1~2 |
| A 工具注册表与只读工具 | 2~3 |
| GW Tool Gateway 安全网关 | 2~3 |
| B 持久任务引擎与 Worker | 4~6 |
| C Planner、Policy Guard 与 API | 3~4 |
| D OA 前端闭环 | 2~3 |
| Phase 2A 发布门 | 2~3 |
| E 写操作试点 | 2~3 |
| F 观测、治理与文档 | 1~2 |
| 总体工作量 | 19~29 个有效工作日 |

排期建议：一名人工负责人持续使用 Codex/AI 协作，安排 5~7 个自然周并将 Phase 2A、Phase 2B 分两次发布。AI 可并行生成样板代码和测试骨架，但 Tool Gateway、防绕过架构测试、人工安全评审和发布门不得折算或跳过。

## 关键路径

```text
P1/P2/P3
  ├─ A1 → A3 → A5 → A6 → GW1 → GW3 → GW5/GW7 ─┐
  └─ B1 → B3 → B6 → B7 ────────────────────────┼→ B8 → C3/C4 → C5/C7 → D1 → G1~G4
                                                ├→ B9 → C9 → D4 ──────────────┤
                                                └→ B12 → G2 ──────────────────┘

G4 通过 → E1 → E2 → E3 → E4/E5 → Phase 2B 验收
```

## 完成定义

- 任务产出代码、数据库、DTO、前端类型、i18n 和文档同步，不留只在文档存在的接口。
- 每个 Controller 端点覆盖未登录、跨用户、跨租户和无权限场景。
- 每个工具覆盖输入/输出 schema、空数据、越权、超时、版本变化和审计。
- 每个 Agent 工具调用只能经过 Tool Gateway；防绕过架构测试、伪造网关上下文测试和领域 Service 二次鉴权测试通过。
- 每个状态迁移覆盖正常、并发、非法迁移、超时和服务重启恢复。
- 永久禁止能力、运行预算、一个写步骤上限和 Kill Switch 有自动测试与人工演练证据。
- Vibe Coding 生成代码必须经过 diff 评审；不得用 Prompt、前端隐藏或高权限角色替代后端安全控制。
- 无真实 LLM 或工具依赖时明确返回能力不可用，不使用 fallback mock。
- 所有可行验证命令通过；受本机 Java/Maven 环境阻塞时，在验证记录中写明原因并由 CI 补齐。
