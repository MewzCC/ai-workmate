# 通用审批申请七工具确认写验收

## 能力与约束

本节点核对已有七个申请人写工具，不新增工具、依赖、迁移或 HTTP 接口，不改变开发及生产发布开关。
目录仍为 140 个工具（41 读、99 写）；本节点不代表其余工具或人工写发布门通过。

| 工具 | 实际结果状态 | 独立业务权限 |
| --- | --- | --- |
| `approval.application.createDraft` | DRAFT，尚未提交 | `approval:create` |
| `approval.application.updateDraft` | DRAFT，尚未提交 | `approval:create` |
| `approval.application.submitDraft` | PENDING，等待审批 | `approval:submit` |
| `approval.application.cancelDraft` | CANCELLED，取消草稿 | `approval:cancel` |
| `approval.application.withdraw` | WITHDRAWN，撤回申请 | `approval:withdraw` |
| `approval.application.reopen` | DRAFT，保留原审批历史，尚未重提 | `approval:reopen` |
| `approval.application.remind` | PENDING，仍待审批 | `approval:remind` |

每个工具仍需自身 `agent:tool:<code>`、当前来源页面、真实业务页面、实时业务权限及显式确认。
领域方法原已要求 `route:approval-start`，现将此条件扩展到七工具的页面能力前置校验。
缺少该入口时不向 Workspace 或业务页提供写能力，确认及执行也拒绝；没有通过增加授权或放宽领域检查处理。
元数据 Schema 与版本不变，已有计划在每次重新鉴权时应用当前限制。

统一回执展示器补充中英文操作语义，区分草稿、提交、取消、撤回、重开与催办，保留可复制申请编号、表单键和版本。
共享申请编号/版本/状态校验供请假与通用申请使用；无效编号、错误状态、缺失或超长表单键、控制字符不能转为成功文字。
展示器只解释已经通过 Gateway 校验的输出，不触发规划或执行。

## 原生执行验收

`AgentApprovalWriteIntegrationTest` 使用自建临时数据库和原生 Redis，启动后验证数据库/schema/Redis 进程绑定。
只在该临时上下文开启写开关和对应工具，使用最小业务权限合成账号、实际密码 Cookie 登录。
启用表单及流程、既有申请和领域提交/撤回仅用于准备数据；被测动作通过实际确认和执行 HTTP API，
再由 Worker、ToolGateway、类型化 Port/Adapter 和领域服务完成。

计划夹具复用 `FrozenWriteTaskFixture`，每个计划只有一个写步骤，初态为 WAITING_CONFIRMATION，
保存真实工具/Schema/参数/计划哈希；夹具不签发确认、不直接调用 Handler，也不证明 LLM 规划成功。
HTTP 与审批人准备复用既有测试夹具。

49 项真实 PostgreSQL 场景：

- 七工具成功：精确回执、状态、版本、字段、成功调用各一次；提交冻结表单/流程/规则快照并建立实际待办。
- 七工具缺少确认：409，Handler 未调用，业务未改变。
- 七工具签发后撤回自身工具权限：403，Handler 未调用。
- 七工具签发后撤回 `approval-start` 入口：403，Handler 未调用。
- 六个版本化操作使用旧版本：失败且不覆盖新版本或修改状态。
- 六个版本化操作指向他人申请：领域返回标准 `TOOL_RESOURCE_NOT_FOUND`，版本不变。
- 六个版本化操作使用另一合法租户的身份、权限、任务和确认凭证指向原申请：领域拒绝，原租户、申请人、状态与版本不变。
- 恢复草稿后以另一个独立确认任务重新提交：旧 CANCELLED 实例与原日志逐行保留，仅追加重开日志；
  新实例不同且恰好一个 RUNNING，与申请租户、业务、申请人和当前实例绑定，只有一个有效待办。
- 第二次独立确认催办：`GATEWAY_THROTTLED`，申请版本、催办次数和成功审计不增加。
- 成功业务审计插入故障：创建草稿与成功审计一起回滚，Gateway 保守报告 `TOOL_RESULT_UNKNOWN`，不自动重试写操作。

成功场景安装临时事务探针，记录申请 insert/update 与成功业务审计的 PostgreSQL transaction ID，
核对两类事件都存在且只有一个事务，而非仅比较模拟 Service 调用。

## 验证命令与证据

使用 Java 17、Maven，并预先在当前进程配置专用本机测试控制库及原生 Redis 路径。
测试自行创建并清理数据库；不得把已有开发数据库当作测试数据写入目标。

```powershell
cd backend
mvn '-Dtest=AgentApprovalWriteIntegrationTest,AgentApprovalWriteResultPresenterTest,ApprovalWritePagePrerequisiteTest,AgentLeaveWriteResultPresenterTest,PageCapabilityCatalogTest' test
```

页面前置权限与回执说明先复现 15 项失败，修复后整组 93 项零失败、零错误、零跳过，其中原生执行 49 项。
独立只读审查指出的历史和流程绑定断言已补齐并实际重跑。

最终 Java 17 `mvn clean test`：1804 项零失败、零错误，新增原生审批 49 项与既有全目录读取 167 项均实际零跳过执行。
2 项既有跳过为缺少 Docker 的 `InitSqlIntegrationTest` 和未配置专属观测数据库的 `RuntimeLogMapperPostgresIntegrationTest`。
OA 全量 56 个文件、455 项通过；lint 零错误（3 条既有 Hook 警告），构建通过。
独立审查无剩余阻断，自有临时数据库、Redis 子进程和浏览器驱动计数均为零。

## 剩余范围与回滚

本节点没有通用申请真实模型或浏览器验收证据，也未核对所有业务字段类型及审批人处理工具。
从零创建并提交通用申请仍缺少单工具原子入口；不能将创建草稿和提交草稿组合成一个双写任务。
后续需补受控原子领域命令和工具，再做真实模型、浏览器、越权及故障验收。

人工发布门仍按 [Phase 2 安全边界](../roadmap/phase-2-agent-security-boundary.md)执行。
回滚本节点不撤销已经提交的业务数据；恢复旧代码会失去前置能力过滤和可读回执，但领域原有入口校验仍在。
不删除 Flyway history、不放宽权限、不重试结果未知的写入。
