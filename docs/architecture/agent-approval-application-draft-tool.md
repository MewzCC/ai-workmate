# 通用审批草稿 Agent 原子工具

## 能力边界

`approval.application.createDraft` 仅为当前认证用户保存一条 `DRAFT` 通用审批申请。它不启动流程、不创建待办、不发送审批通知，也不替用户作出审批决定。

工具输入使用封闭的 `formKey`、可选 `processKey` 和字段列表。租户、申请人、角色、权限及数据范围不允许由模型传入。动态表单值在 Adapter 中转换为页面接口使用的结构，随后仍由 `GenericApprovalService` 按实时表单 Schema 校验字段白名单、类型、选项、长度和总载荷。

## 复用与服务化

Handler 只解析冻结契约并生成稳定操作键；`ApprovalApplicationToolPort` 只包含 Java 值对象，不依赖 Spring、Jackson、HTTP、DTO、实体或 Mapper；本地 Adapter 调用与页面共用的 `GenericApprovalService`。未来拆分审批服务时替换 Adapter，并保留可信身份、固定服务目标、远端重复鉴权和稳定操作键核验协议。

同一租户、申请人和操作键由数据库唯一索引约束。完全相同的重放返回原草稿；表单、流程、字段或状态不同均返回幂等冲突。申请与业务审计处于同一事务。

## 安全与发布

- ToolGateway 同时检查业务权限 `approval:create` 与独立工具权限 `agent:tool:approval.application.createDraft`。
- 领域层再次检查有效用户、租户、`route:approval-start` 与 `approval:create`。
- 风险等级 L1，显式确认，单写步骤，业务幂等。
- 全局和租户写开关继续默认关闭，开发完成不代表生产发布。
- 禁止通过此工具提交、撤回、审批、批量创建或修改流程配置。
