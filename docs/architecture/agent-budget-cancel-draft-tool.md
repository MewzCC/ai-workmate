# 预算草稿取消 Agent 工具

`budget.cancelDraft` 是预算页面的单资源原子写工具，只接受 `budgetId` 和乐观锁 `version`。目标状态不由模型传入，服务端固定执行 `DRAFT → CANCELLED`。

## 边界

- ToolGateway 重新校验任务快照、可信租户与用户、实时权限、确认令牌、写开关、预算和审计。
- 工具要求 `budget:manage` 与独立的 `agent:tool:budget.cancelDraft` 权限，风险级别为 L2，必须二次确认。
- 领域服务再次按租户读取预算并校验版本，只允许取消零占用、零支出的草稿。
- 状态更新条件包含 `tenant_id + id + version + DRAFT`，状态流水和业务审计与预算变更处于同一事务。
- 工具禁止自动重试，不允许取消活动预算，不包含关闭预算、占用、释放、核销或任何批量操作。

## 迁移到 Spring Cloud

Handler 仅依赖传输中立的 `BudgetToolPort`。当前本地 Adapter 调用预算领域服务；拆分服务时可替换为带服务身份、可信用户上下文、租户、超时和固定方法契约的 RPC Adapter，无需改变 ToolGateway、工具 Schema 或页面能力目录。远程调用不得暴露任意 URL，也不得把领域鉴权迁移到模型参数。

由于本工具禁止自动重试，远程超时应返回“写入结果未知”并失败关闭；只有后续增加类型化只读结果核验协议后，才可由人工判断是否需要新任务。
