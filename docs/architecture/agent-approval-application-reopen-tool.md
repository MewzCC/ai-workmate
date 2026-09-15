# 通用审批申请恢复草稿原子工具

`approval.application.reopen` 将当前登录用户拥有的一条 `REJECTED` 或 `WITHDRAWN` 通用审批申请恢复为 `DRAFT`。输入仅包含 `applicationId` 与乐观锁 `version`。

## 原子边界

该工具只执行一次状态恢复，不在同一 Agent 任务内继续编辑或提交。恢复后可由用户修改表单，再在另一个明确确认的任务中调用 `approval.application.submitDraft`。这样既保留“一任务最多一个写步骤”，也避免把未经确认的旧数据直接重新送审。

Handler、`ApprovalApplicationToolPort` 与 Adapter 不复制审批逻辑；Adapter 调用 `GenericApprovalService.reopenAgentApplication`，领域层保留旧流程实例和动作历史，只清除当前提交/完成时间并将申请恢复为可编辑草稿。未来 Spring Cloud 化只替换 Adapter，Port 契约、ToolGateway 策略和领域状态机保持不变。

## 安全约束

- ToolGateway 校验独立工具权限、任务快照、租约、确认、预算、写开关和 Kill Switch。
- 领域层再次实时校验 `route:approval-start`、`approval:reopen`、租户、本人归属、允许的来源状态和版本。
- 申请更新、历史动作与业务审计处于同一事务，失败时整体回滚。
- 工具为 L1、显式确认、单写步骤、`RetryPolicy.NEVER`；未知结果不得盲目重试。

该工具不修改历史审批结论、不覆盖表单数据、不启动新流程，也不授予审批权限。
