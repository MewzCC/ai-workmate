# 费用草稿提交原子工具

`expense.submitDraft` 是费用报销页面的专用原子写工具。它只提交当前登录用户拥有的一条 `expense-application` 草稿，不接受租户、申请人、审批人、表单类型、流程节点或权限参数。

## 复用边界

`ExpenseSubmitDraftToolHandler` 只解析 `applicationId` 与乐观锁 `version`，再调用类型化 `ExpenseToolPort`。本地 Adapter 将命令映射到 `ExpenseApplicationService.submitAgentDraft`；费用领域门面先固定校验表单类型、租户和本人归属，再复用 `GenericApprovalService.submitAgentDraft` 完成表单校验、流程选择、快照冻结、实例和首待办创建。审批状态机与事务不会复制到 Handler 或 Adapter。

未来迁移到 Spring Cloud 时只替换 `ExpenseToolPort` 的固定目标远程 Adapter。远端仍必须从可信服务身份恢复用户与租户、重新鉴权，并按申请 ID 与版本执行原子状态迁移；不得把 ToolGateway 策略、任意服务地址或通用表单选择权下放给模型。

## 安全与一致性

- ToolGateway 提供可信用户、租户、任务、步骤和确认上下文；模型只能提供申请 ID 和预期版本。
- 网关要求独立 `agent:tool:expense.submitDraft` 权限；领域层再次实时检查 `route:approval-start`、`approval:submit`、租户、本人归属、固定费用表单、`DRAFT` 状态和乐观锁版本。
- 工具为 L1、本人范围、显式确认、单写步骤。提交会创建流程与待办，因此使用 `RetryPolicy.NEVER`，未知结果不得自动重试。
- 申请状态、冻结快照、流程实例、首待办、动作流水和业务审计继续处于通用审批事务中；任何异常必须整笔回滚。
- 全局与租户写开关、Kill Switch 和人工发布门继续默认关闭。数据库中的工具契约可用不代表生产写能力已经放行。

该工具不审批、不付款、不上传或发送发票、不修改流程配置、不批量提交，也不能提交请假或其他通用申请。
