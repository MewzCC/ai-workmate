# 预算草稿更新原子工具

`budget.updateDraft` 只负责按乐观锁版本更新一条租户内 `DRAFT` 预算，不改变预算编号、状态、占用额或已支出额。

## 封闭契约

- 输入只包含 `budgetId`、`version` 与名称、年度、负责人、总额、币种、预警阈值和摘要。
- `tenantId`、`userId`、角色、权限和数据范围仅来自 ToolGateway 可信上下文。
- 工具不能激活、关闭或取消预算，不能占用、释放或核销额度，也不能修改预算编号。
- 工具要求实时 `budget:manage`，采用 `TENANT_SCOPED`、L1 显式确认、禁止自动重试和单写步骤。
- 全局、租户和单工具写开关继续默认关闭，本迁移只发布代码拥有的固定契约。

## 分层与迁移边界

Handler 只把冻结 Schema 转换成 `BudgetToolPort.BudgetDraftUpdate`。本地适配器再转换成与传输无关的
`BudgetAgentDraftCommand` 并调用预算领域服务。未来拆分 Spring Cloud 时，可以用固定服务发现、服务身份
和 mTLS 保护的 RPC 适配器替换本地适配器；ToolGateway、Planner、Handler 与工具 Schema 不需要改变。

预算领域服务重新解析实时权限，以 `tenant_id + id + version + DRAFT` 作为条件执行更新，并校验负责人仍在
当前租户、金额与文本边界。预算变更、`UPDATED` 业务流水和 `UPDATE` 业务审计在同一事务内完成；任一失败
全部回滚。版本冲突、跨租户资源和非草稿状态均失败关闭。
