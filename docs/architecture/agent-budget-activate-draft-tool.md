# 预算草稿启用原子工具

`budget.activateDraft` 只负责按乐观锁版本将一条租户内预算从 `DRAFT` 迁移到 `ACTIVE`。目标状态由服务端固定，Agent 不能选择任意状态。

## 封闭契约

- 输入只包含 `budgetId` 与 `version`，不接收状态、金额、负责人或备注等可扩张业务语义。
- `tenantId`、`userId`、角色、权限和数据范围仅来自 ToolGateway 可信上下文。
- 工具不能修改预算字段，不能关闭或取消预算，也不能占用、释放或核销额度。
- 工具要求实时 `budget:manage`，采用 `TENANT_SCOPED`、L2 二次确认、禁止自动重试和单写步骤。
- 全局、租户和单工具写开关继续默认关闭；数据库迁移只登记代码拥有的固定契约和独立工具权限。

## 分层与迁移边界

Handler 复用统一版本化单写模板，只将 `budgetId` 与 `version` 交给类型化 `BudgetToolPort`。本地适配器把固定目标状态
`ACTIVE` 写入领域请求，并调用预算领域服务。未来拆分 Spring Cloud 时，可以使用固定服务发现、服务身份、mTLS 和超时策略
保护的 RPC 适配器替换本地适配器；ToolGateway、Planner、Handler、工具 Schema 和确认流程不需要改变。

预算领域服务重新解析实时权限，并以租户、资源、版本和 `DRAFT` 来源状态执行乐观锁迁移。状态变更、`STATUS_ACTIVE`
业务流水和业务审计在同一事务内完成；任一失败全部回滚。跨租户资源、非草稿状态、权限回收和版本冲突均失败关闭。
