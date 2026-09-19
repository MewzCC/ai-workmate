# 供应商草稿创建原子工具

`supplier.createDraft` 创建当前租户内的一条 `DRAFT` 供应商资料；`supplier.updateDraft` 只按版本更新既有草稿。

## 封闭边界

- 输入仅包含编码、名称、简称、类别、供应商等级和可选付款条款。
- 统一社会信用代码、联系人、电话、邮箱、地址和风险备注不进入 Agent Schema，也不进入工具回执。
- 租户、操作人、权限和数据范围只来自 ToolGateway 可信上下文；领域服务再次校验 `route:suppliers` 与 `supplier:manage`。
- 工具采用 L1、显式确认、租户范围、单写步骤和禁止自动重试；所有写开关保持默认关闭。
- 工具不能激活、暂停或拉黑供应商，也不能创建合同、付款或向外部系统发送资料。
- 更新工具不接收供应商编码，使用 `tenant_id + id + version + DRAFT` 条件防止跨租户、并发覆盖和活动资料变更。

## 分层与服务化

Handler 只解析共用的封闭字段并调用类型化 `FinanceToolPort.SupplierDraft` 或 `SupplierDraftUpdate`。本地 Adapter 将非敏感字段映射到既有 `SupplierService`；供应商、初始状态历史和业务审计由领域事务统一写入，异常时整体回滚。

未来迁移 Spring Cloud 时只替换 Adapter 为受认证的固定目标 RPC。远端服务仍从可信服务身份重新解析租户、用户和实时权限，不接受模型提供的身份字段。当前尚无可靠稳定操作键，远程结果不确定时必须失败关闭；补齐幂等和只读结果核验前不得自动重试。
