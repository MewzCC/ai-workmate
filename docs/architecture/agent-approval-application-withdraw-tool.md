# 通用审批申请撤回原子工具

`approval.application.withdraw` 只撤回当前登录用户拥有的一条 `PENDING` 通用审批申请。输入仅包含 `applicationId` 与乐观锁 `version`，不接受租户、申请人、审批人、任务、流程实例或权限参数。

## 复用与服务化边界

Handler 通过既有 `ApprovalApplicationToolPort` 调用 Adapter，Adapter 复用 `GenericApprovalService.withdrawAgentApplication`。领域事务继续负责本人归属、状态和版本校验，并原子更新申请、当前有效待办、运行中流程实例、动作流水、业务审计与站内通知。迁移到 Spring Cloud 时只替换 Port Adapter；ToolGateway 的可信上下文和审批领域约束分别留在各自服务边界，不共享 Mapper 或数据库实体。

## 安全约束

- ToolGateway 校验独立 `agent:tool:approval.application.withdraw` 权限、租户策略、任务快照、租约、确认令牌、预算和 Kill Switch。
- 领域层再次实时校验 `route:approval-start` 与 `approval:withdraw`，并按租户与申请人查询资源。
- 只允许 `PENDING → WITHDRAWN`，同时将唯一有效待办和流程实例置为取消；任一乐观锁冲突都会回滚整个事务。
- 工具为 L1、显式确认、单写步骤、`RetryPolicy.NEVER`。未知执行结果不得自动重试，应先通过后续只读结果核验协议确认状态。
- 全局和租户写开关继续默认关闭，数据库 `enabled=true` 仅表示构建具备该冻结契约。

该工具不执行审批、不转交、不加签、不批量撤回，也不开放外部消息能力。
