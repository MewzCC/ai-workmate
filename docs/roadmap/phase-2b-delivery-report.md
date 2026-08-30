# Phase 2B Agent 受控写入发布门验收报告

## 1. 验收结论

- 自动化发布门：**PASS**
- 当前发布状态：**IMPLEMENTED_BUT_PRODUCTION_DISABLED**
- 验收日期：2026-08-29
- 验收分支：`feature/zcc`
- 交付范围：Phase 2B 单一受控写入试点
- 生产启用状态：未批准；全局与租户写工具开关均保持关闭

本阶段完成 `leave.createDraft` L1 与 `leave.submit` L2。每个任务仍最多包含一个写步骤，执行必须经过 `ToolGateway.execute(stepId, workerLease)`、实时权限复核、一次性确认消费、领域纵深鉴权与审计。实施验收通过不代表生产租户可以启用写能力。

## 2. 已推送提交线

| 顺序 | 提交 | 主题 |
| ---: | --- | --- |
| 18 | `5304afd5` | `feat(agent): add confirmed leave draft tool` |
| 19 | `22b8fd93` | `feat(agent): add confirmed leave submit tool` |
| 安全补强 | `18d08a51` | `feat(agent): add handling for uncertain tool write outcomes` |
| 20 | 本报告所在提交 | `test(agent): complete phase 2b controlled write gate` |

步骤 18、19 均在定向测试和真实 Flyway 升级通过后提交、推送。安全补强提交将不可判定的写结果固定映射为 `TOOL_RESULT_UNKNOWN`，禁止自动重试。步骤 20 完成最终全仓、真实数据库、真实模型与前端确认界面验收。

## 3. 受控写入安全边界

- `leave.createDraft` 固定为 L1、`SINGLE_WRITE`、`BUSINESS_IDEMPOTENT`、显式确认；服务端领域幂等键由 taskId、stepId 和工具版本构造，重复执行不会生成第二份草稿。
- `leave.submit` 固定为 L2、`SINGLE_WRITE`、`NEVER`、二次确认；只允许提交本人、同租户、任务创建前已存在且版本匹配的 `DRAFT`。
- Planner 和任务服务均拒绝超过一个写步骤，因此 `leave.createDraft` 与 `leave.submit` 不能出现在同一计划。
- 写工具需要 Agent 总开关、规划/执行开关、全局写开关、租户 Agent 策略、租户写策略、Registry 和实时 `leave:create` 权限同时通过；任一读取或校验异常均 fail closed。
- 确认凭证与 taskId、userId、tenantId、planVersion、planHash、风险等级绑定，十分钟有效、仅保存在进程内、数据库仅保留哈希，并在执行时原子消费。
- 领域变更与业务审计处于同一事务；Handler 仍禁止直接依赖 Mapper、HTTP、文件系统或脚本能力。
- 写 Handler 调用后如发生超时、中断、执行异常、输出序列化/校验异常或完成审计异常，任务进入 `PARTIALLY_SUCCEEDED/TOOL_RESULT_UNKNOWN`，不得由 Worker 自动重试；租约过期重启恢复遵循相同规则。

## 4. 测试证据

### 4.1 后端、安全与真实 PostgreSQL

| 验证 | 结果 |
| --- | --- |
| Java 17 全量 `mvn test` | 72 reports，330 tests，0 failures，0 errors，9 skipped |
| 真实 PostgreSQL 定向门 | 33 tests，0 failures，0 errors，0 skipped |
| 写结果未知策略 | 覆盖 Handler 超时、执行异常、输出异常、审计异常、Worker 不重试及终态事件 |
| 重启恢复 | 真实 PostgreSQL 验证过期写租约进入 `PARTIALLY_SUCCEEDED/TOOL_RESULT_UNKNOWN`，不会重新领取执行 |
| 确认安全 | 覆盖缺少确认、哈希/版本篡改、过期、重放、并发消费和权限回收 |
| 领域安全 | 覆盖跨用户/跨租户、非草稿、任务后创建草稿、版本冲突和审计事务 |
| Planner | 模型桩覆盖封闭 Schema、注入、一次重试与多写步骤拒绝 |
| ArchUnit | Gateway 唯一 Handler 依赖边界以及 Handler 禁止依赖边界继续通过 |

全量测试中的跳过项均依赖显式真实 PostgreSQL/Testcontainers 环境；本次另行通过 `AGENT_TEST_DB_URL` 在 PostgreSQL 16.14 上执行对应持久化测试。Docker CLI 当前不可用，未伪造 Testcontainers 成功。

### 4.2 Flyway 三场景

| 场景 | 结果 |
| --- | --- |
| 空库首次迁移 | 22 个版本化迁移全部成功，最新版本 `202608261422` |
| 历史 `init.sql` 库升级 | 自动建立 version 0 baseline 后升级成功，23 条 schema history 记录有效 |
| 迁移后重启 `validate()` | 两个临时库均再次独立校验成功，当前版本 `202608261422` |
| 本地开发库 | PostgreSQL 16.14 成功校验 23 个迁移，当前版本 `202608261422` |

验收使用独立临时库 `ai_workmate_phase2b_empty_20260829` 与 `ai_workmate_phase2b_legacy_20260829`，完成后均已删除。没有修改历史迁移或 `init.sql`。

### 4.3 前端与真实模型

| 验证 | 结果 |
| --- | --- |
| OA `npm run lint` | 0 errors，5 个既有 React Hooks warnings |
| OA `npm run test` | 全量 6 files / 23 tests 通过；其中 4/4 Drawer tests 覆盖 L0、L1 与 L2 |
| OA `npm run build` | PASS；保留既有大 chunk warning |
| 营销站 `npm run lint` | 0 errors，2 个既有 warnings |
| 营销站 `npm run build` | PASS |
| 真实模型正常烟测 | `leave.submit` 返回严格 JSON、唯一白名单工具及 `applicationId/version` 参数；只规划、未执行业务 |
| 真实模型注入烟测 | SQL、任意 URL 与提示词泄露指令未进入计划；结果仍仅为 `todo.query` 且无越界参数 |

OA 的 L2 确认按钮使用危险态样式；确认凭证在确认后即时签发，只通过执行请求发送，不写 localStorage、sessionStorage 或 URL。

## 5. 开关与发布状态

- `application.yml`、`.env.example`、`.env.docker.example` 和本地 `.env` 均为 `AGENT_WRITE_TOOLS_ENABLED=false`。
- 本地 tenant 1 当前为 `enabled=true`、`write_tools_enabled=false`。
- 数据库 Registry 中两个工具定义已存在，风险分别为 L1/L2；Registry 存在不等于允许执行。
- 生产启用必须同时经过独立人工签字、显式全局开关和指定租户写策略变更；本提交不执行这些动作。

## 6. 已知风险与处置

- 进程级超时无法证明下游事务最终状态，因此任何写调用后的不确定故障均保守标记为“结果未知”，要求人工核对业务记录与审计，禁止自动补偿或重试。
- 本地 Docker CLI 不可用；使用两个独立 PostgreSQL 临时库和开发库完成替代验证，CI 继续保留 Testcontainers 覆盖。
- 真实模型输出具有非确定性；代码层严格 DTO、Schema、工具白名单、单写限制与 Gateway 复核仍是安全权威，模型输出本身不构成执行授权。
- OA 既有 lint warnings 与构建大 chunk warning 不影响本阶段功能，但应继续作为前端质量债处理。

## 7. 生产发布人工签字项

- [ ] 审批人接受本报告的自动化证据、真实 PostgreSQL 替代验证和已知风险。
- [ ] 审批人确认 `TOOL_RESULT_UNKNOWN` 的人工核对与处置流程已分配责任人。
- [ ] 审批人确认只为明确租户启用所需写工具，不进行全租户开放。
- [ ] 审批人确认监控、审计查询、回滚开关及值班响应已就绪。
- [ ] 审批人明确签署生产启用；在此之前 `AGENT_WRITE_TOOLS_ENABLED=false` 且租户 `write_tools_enabled=false`。

自动化发布门通过后，Phase 2B 实现工作在此收口。生产启用是独立变更，不由 Agent 自动执行。
