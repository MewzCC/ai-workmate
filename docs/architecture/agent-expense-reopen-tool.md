# 费用申请恢复草稿原子工具

`expense.reopen` 将当前用户拥有的一条 `REJECTED` 或 `WITHDRAWN` 费用申请恢复为 `DRAFT`。输入只包含 `applicationId` 与乐观锁 `version`，模型不能指定表单、租户、申请人、目标状态或流程。

工具复用 `TypedVersionedWriteToolHandler`、`ExpenseToolPort` 与费用领域门面。费用门面先确认资源属于可信上下文中的当前用户、当前租户且表单固定为 `expense-application`，再调用 `GenericApprovalService.reopenAgentApplication` 复用已有状态机和事务审计。旧流程实例与动作历史保留，提交时间和完成时间清除，申请恢复为可编辑草稿。

该工具只恢复草稿，不编辑字段，也不重新提交。后续修改与提交必须分别经过独立工具、独立确认和独立审计，持续满足一个 Agent 任务最多一个写步骤。

安全约束：

- ToolGateway 重新校验任务快照、租户、用户、实时工具权限、确认令牌、预算、租约、Schema 与参数哈希。
- 领域层再次校验 `route:approval-start`、`approval:reopen`、本人归属、固定费用表单、来源状态和乐观锁版本。
- 工具为 L1、本人范围、显式确认、禁止自动重试、单写步骤；全局和租户写开关默认关闭。
- 并发恢复只有一个请求成功；审计失败时申请状态与版本整体回滚。

未来迁移到 Spring Cloud 时，保持 `ExpenseToolPort` 作为稳定应用边界，只替换本地 Adapter 为固定服务发现目标的认证远程 Adapter。远端费用服务必须从服务身份恢复可信租户和用户并再次鉴权；禁止模型提供任意服务地址，也不能把恢复事务拆成多个跨服务写步骤。未知结果只能通过只读状态查询确认，不能自动重放写请求。

该工具不审批、不编辑、不重新提交、不付款、不上传或外发票据，也不能恢复请假或其他通用申请。
