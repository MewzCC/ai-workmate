# 通用审批草稿提交原子工具

`approval.application.submitDraft` 是通用审批 P6 的第二个原子写工具。它只提交一条当前登录用户拥有的 `DRAFT` 申请，不接受租户、申请人、审批人、流程节点或权限参数。

## 复用边界

`ApprovalApplicationSubmitDraftToolHandler` 只解析 `applicationId` 与乐观锁 `version`，然后调用既有 `ApprovalApplicationToolPort`。本地 Adapter 仅把类型化命令映射到 `GenericApprovalService.submitAgentDraft`；表单完整校验、流程选择、版本快照、审批人解析、实例与首待办创建均继续由通用审批领域事务负责。未来拆分 Spring Cloud 时只替换 Port 的远程 Adapter，不复制 ToolGateway 策略或审批状态机。

## 安全与一致性

- ToolGateway 提供可信用户、租户、任务、步骤和确认上下文；模型不能覆盖这些字段。
- 网关要求独立 `agent:tool:approval.application.submitDraft` 权限，领域层再次实时检查 `route:approval-start`、`approval:submit`、本人归属、`DRAFT` 状态和版本。
- 工具为 L1、显式确认、单写步骤。提交会创建流程和待办，因此使用 `RetryPolicy.NEVER`；未知结果不得盲目重试。
- 申请状态、冻结快照、流程实例、首个待办、动作流水和业务审计处于同一事务；审计失败必须回滚业务写入。
- 全局与租户写开关、Kill Switch 和人工发布门继续默认关闭。数据库工具种子 `enabled=true` 只声明代码能力，不代表生产已经开放执行。

该工具不审批、不代替审批人、不修改流程配置、不批量提交，也不产生外部消息。
