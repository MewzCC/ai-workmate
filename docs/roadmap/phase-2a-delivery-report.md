# Phase 2A Agent 可信执行闭环验收报告

## 1. 验收结论

- 自动化发布门：**PASS**
- 当前发布状态：**WAITING_FOR_HUMAN_APPROVAL**
- 验收日期：2026-08-26
- 验收分支：`feature/zcc`
- 交付范围：Phase 2A 受控只读 Agent 闭环
- Phase 2B 状态：未批准、未实施；`leave.createDraft` 与 `leave.submit` 继续默认关闭

本阶段已完成 Tool Registry、租户收窄策略、持久任务状态机、Tool Gateway、四个只读工具、租约 Worker、确认凭证、可续传 SSE、结构化 Planner、OA 执行闭环、个人 AI 任务中心、保留清理和恢复观测。所有工具执行仅能通过 `ToolGateway.execute(stepId, workerLease)` 进入 Handler。

## 2. 已推送提交线

| 顺序 | 提交 | 主题 |
| ---: | --- | --- |
| 1 | `8a43feb7` | `docs(agent): freeze phase 2 contracts and security matrix` |
| 2 | `3f91d475` | `build(agent): add schema validation and architecture test support` |
| 3 | `767e504c` | `feat(agent): add tool registry and tenant policy foundation` |
| 4 | `69ba9266` | `feat(agent): add persistent task state machine and idempotency` |
| 5 | `db24f6f8` | `feat(agent): establish tool gateway security boundary` |
| 6 | `e20b266d` | `feat(agent): enforce gateway policy and fail closed execution` |
| 7 | `8520d98c` | `feat(agent): add secured todo query tool` |
| 8 | `b5c4447a` | `feat(agent): add secured leave query tool` |
| 9 | `e991bfc0` | `feat(agent): add secured knowledge search tool` |
| 10 | `7ea8b559` | `feat(agent): add secured notification query tool` |
| 11 | `c4874dd7` | `feat(agent): add leased worker and runtime safety controls` |
| 12 | `0b41a5b4` | `feat(agent): add confirmation task APIs and resumable events` |
| 13 | `feaf86df` | `feat(agent): add structured planner and task api cutover` |
| 14 | `17968a0f` | `feat(oa): integrate agent plans confirmation and sse progress` |
| 15 | `f1be7a56` | `feat(oa): add personal ai task center` |
| 16 | `faaae113` | `chore(agent): add retention observability and recovery evidence` |
| 17 | 本报告所在提交 | `test(agent): complete phase 2a security release gate` |

每个已完成提交均在对应定向门通过后提交并推送，推送后核对本地 `HEAD` 与 `origin/feature/zcc`。提交未 amend，未 force-push。

## 3. 固化的安全边界

- 工具代码由进程内固定允许列表约束；Phase 2A 只允许 `todo.query`、`leave.mine`、`knowledge.search`、`notification.mine`。
- 永久拒绝 SQL、代码、文件系统、任意网络、权限修改、删除、批量操作、敏感导出、外部消息和后台自治能力。
- Controller、Planner、Task Service、Worker 不依赖 `ToolHandler`；Handler 不依赖 Mapper、HTTP Client、文件系统或脚本能力，ArchUnit 持续验证。
- Gateway 按固定顺序校验总开关、Worker 租约、attempt、任务/计划/工具/schema/参数哈希、实时 RBAC、风险、确认、预算、输入 schema、前置审计和输出 schema。
- Registry、策略、权限、限流或前置审计异常均 fail closed；伪造租约、版本、哈希、用户或租户时 Handler 调用次数为零。
- Handler 使用服务端可信租户与用户上下文，领域 Service 再次校验数据归属；客户端不能提交身份参数。
- Handler 在有界线程池执行；工具定义超时、运行时硬上限、任务或步骤剩余预算取最小值，超时取消并记录 `TIMED_OUT/TOOL_TIMEOUT`。
- 知识检索结果被标记为不可信数据，仅允许展示或非递归总结；工具结果不得触发再次规划。
- SSE 使用认证 Cookie 和 `Last-Event-ID`，URL 不携带 token；事件所有权、重放去重、心跳、语言和终态关闭已验证。
- 写工具、Agent 总开关及租户启用均默认关闭；数据库只能关闭工具、提高风险或收紧限制。

## 4. 测试证据

### 4.1 后端与安全攻击集

| 验证 | 结果 |
| --- | --- |
| Gateway、Registry、SSE 定向发布门 | 71 tests，0 failures，0 errors，0 skipped |
| 最终无外部环境全量 `mvn test` | 312 tests，0 failures，0 errors，6 skipped（均为需要真实 PostgreSQL 的集成场景） |
| 空库真实 PostgreSQL 全量 `mvn test` | 267 tests，0 failures，0 errors，1 skipped |
| 四工具统一参数攻击语料 | 45 tests，覆盖每工具 3 个正常参数及身份注入、类型混淆、恶意嵌套、URL/SQL、超限和长上下文拒绝 |
| Gateway 策略攻击集 | 17 tests，覆盖跨租户、权限回收、开关、attempt、schema/hash、审计异常及 Handler 超时 |
| SSE 事件测试 | 覆盖本人续传、重放去重、心跳和终态关闭；Web 安全测试覆盖跨用户拒绝与 URL token 禁止 |
| Planner 模型桩 | 覆盖严格 DTO、schema 错误、超时、注入隔离和最多一次重试 |
| 真实模型烟测 | 有效 `AI_API_KEY` 环境下，正常与 Prompt Injection 两组均通过；返回工具均严格为 `todo.query` |
| ArchUnit | Gateway 唯一 Handler 依赖边界和 Handler 禁止依赖边界均通过 |

四个只读工具的 Handler/领域测试同时覆盖空结果不伪造、可信用户上下文、跨用户/跨租户纵深鉴权、查询上限和敏感输出过滤。知识库注入文本只作为带引用的不可信内容返回。

### 4.2 PostgreSQL/Flyway

| 场景 | 结果 |
| --- | --- |
| 空库首次迁移 | 20 个版本化迁移全部成功，最新版本 `202608260001` |
| 历史 `init.sql` 库升级 | 自动建立 version 0 baseline 后，20 个迁移全部成功 |
| 升级后重启与 `validate()` | 成功校验 21 条历史记录（baseline + 20 migrations），schema up to date |
| 数据库并发行为 | 条件状态迁移、幂等唯一约束、Worker 原子领取、租约恢复和确认原子消费测试通过 |

验收使用独立临时数据库 `ai_workmate_p2a_empty_20260826` 与 `ai_workmate_p2a_history_20260826`，验证完成后已删除。未修改任何已发布迁移或 `init.sql`。

### 4.3 前端与独立 SPA

| 应用 | 命令 | 结果 |
| --- | --- | --- |
| OA | `npm run lint` | 0 errors，5 个既有 React Hooks warnings |
| OA | `npm run test` | 6 files，22 tests passed |
| OA | `npm run build` | PASS |
| 营销站 | `npm run lint` | 0 errors，2 个既有 warnings |
| 营销站 | `npm run build` | PASS |

OA 与营销站继续保持独立 Vite SPA。任务计划、确认、执行、SSE 进度、任务列表/详情/取消和中英文资源均接入真实 API；确认凭证只保存在组件内存，动态路由仍由后端权限结果驱动。

## 5. 故障恢复与审计证据

- Worker 通过数据库原子领取、心跳和租约过期恢复支持多实例与重启恢复；不可重试错误不会再次执行。
- 任务、步骤、事件和调用审计持久化；调用在 Handler 前先写审计记录，失败时保留低敏错误代码。
- 普通任务保留 90 天，详细事件和调用审计保留 30 天；清理器按 tenant 与时间分批删除并只记录数量。
- 观测日志不记录原始输入、确认凭证、工具结果、知识内容或用户敏感字段。
- 恢复演练步骤和审计查询见 `docs/qa/agent-task-recovery-runbook.md`，架构说明见 `docs/architecture/agent-task-engine.md`。

## 6. 已知风险与限制

- 本机 Docker CLI 不可用，因此 Testcontainers 场景在本地跳过 1 项；本次以两个独立真实 PostgreSQL 数据库完成替代验证，CI 仍保留 Testcontainers 覆盖。
- Java `Future.cancel(true)` 依赖下游对中断的协作。Phase 2A 仅包含无副作用只读工具；Phase 2B 写工具必须额外证明业务幂等、同事务审计及“结果未知”恢复语义。
- OA 构建有既有大 chunk 警告；不影响本阶段正确性，但后续应进行路由级拆包。
- 两个前端 lint 仅剩既有 warnings，无 lint error；应作为后续质量债跟踪。
- 本报告不包含生产部署、生产租户开关启用或写能力批准。

## 7. 人工发布签字项

审批人应逐项确认：

- [ ] 接受本报告列出的自动化验证证据和本地 Docker/Testcontainers 替代方案。
- [ ] 接受当前已知风险，并确认 Phase 2A 只读范围可进入下一发布流程。
- [ ] 确认生产发布时 Agent 总开关和租户工具开关仍按变更单独启用。
- [ ] 明确决定是否批准开始 Phase 2B 的 `leave.createDraft` 与 `leave.submit` 实现和测试。

在用户明确书面批准 Phase 2B 前，开发流程必须停在本门，所有写工具保持关闭；批准开始实现也不等于批准生产启用。

## 8. 本地界面验收补充（2026-08-26）

- Flyway 对 `ai_workmate_dev` 成功校验 21 条历史记录，当前版本 `202608260001`，无待执行迁移。
- 本地 tenant 1 已仅为 Phase 2A 验收开启只读策略；`write_tools_enabled=false`。
- 首次真实界面执行暴露 `AgentWorkerMapper.completeStep` 的 PostgreSQL 多表更新字段歧义：未限定的 `version` 被数据库拒绝，Gateway 与 Handler 本身已成功。
- 修复为 `version=step.version+1`，同时增加低敏 Worker 异常类型日志和真实 PostgreSQL Mapper 回归测试。
- Chrome 实际闭环任务 `7220a5f3-44be-4d71-a75c-fcf9585b5844` 经 Planner、Worker、Tool Gateway、`todo.query`、持久审计与 SSE 后达到 `SUCCEEDED`；调用审计为 `ALLOW/SUCCEEDED`，Handler 耗时 10ms，空结果未伪造数据。
