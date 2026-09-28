# 费用申请撤回原子工具

`expense.withdraw` 是费用报销页面的专用原子写工具。它只撤回当前登录用户拥有的一条 `expense-application` 待审批申请，不接受租户、申请人、审批人、表单类型、流程节点或权限参数。

## 复用边界

`ExpenseWithdrawToolHandler` 复用统一版本化写入模板，只解析 `applicationId` 与乐观锁 `version`，再调用类型化 `ExpenseToolPort`。本地 Adapter 将命令映射到 `ExpenseApplicationService.withdrawAgentApplication`；费用领域门面先固定校验租户、本人归属和费用表单类型，再复用 `GenericApprovalService.withdrawAgentApplication` 原子取消申请、运行中流程与唯一有效待办。Handler、Adapter 和费用门面都不复制审批状态机。

未来迁移到 Spring Cloud 时只替换 `ExpenseToolPort` 的固定目标远程 Adapter。远端必须从可信服务身份恢复用户与租户并重新鉴权；不得信任模型提供的身份字段，不得接受任意服务地址，也不得将撤回拆成多个可观察的跨服务写步骤。未知结果必须查询领域状态，当前工具禁止自动重试。

## 安全与一致性

- ToolGateway 提供可信用户、租户、任务、步骤和确认上下文；模型只能提供申请 ID 与预期版本。
- 网关要求独立 `agent:tool:expense.withdraw` 权限；领域层再次实时检查 `route:approval-start`、`approval:withdraw`、租户、本人归属、固定费用表单、`PENDING` 状态和乐观锁版本。
- 工具为 L1、本人范围、显式确认、单写步骤和 `RetryPolicy.NEVER`，并发撤回只能一个成功。
- 申请、流程实例、待办、动作流水和业务审计处于同一事务；真实 PostgreSQL 回归证明审计失败时所有状态一并回滚。
- 全局与租户写开关、Kill Switch 和人工发布门继续默认关闭。数据库中的工具契约可用不代表生产写能力已经放行。

该工具不审批、不付款、不重新提交、不修改流程配置、不批量撤回，也不能撤回请假或其他通用申请。
