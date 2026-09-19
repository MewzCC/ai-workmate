# 预算草稿创建原子工具

`budget.createDraft` 是预算中心首个 Agent 写工具，只负责创建一条 `DRAFT` 预算计划。

## 边界

- 输入固定为预算编号、名称、年度、负责人、总额、币种、预警阈值和可选摘要。
- 身份、租户、角色、权限和数据范围只来自 ToolGateway 可信上下文。
- 工具不能启用、关闭或取消预算，也不能占用、释放或核销额度。
- 工具要求 `budget:manage`，采用 `TENANT_SCOPED`，L1 显式确认且禁止自动重试。
- 全局、租户和单工具写开关仍可独立关闭，默认写开关不因本工具改变。

## 分层

Handler 仅将封闭参数解析为 `FinanceToolPort.BudgetDraft`。当前适配器调用本地
`BudgetService`；未来拆分 Spring Cloud 时可替换为固定服务发现和服务身份保护的
RPC 适配器，工具契约、ToolGateway 和 Planner 不需要感知传输方式。

预算领域服务再次解析实时用户权限，按租户校验有效负责人，并在同一事务中写入
预算草稿、`CREATED` 业务流水和 `CREATE` 业务审计。任一写入失败时整笔回滚。
