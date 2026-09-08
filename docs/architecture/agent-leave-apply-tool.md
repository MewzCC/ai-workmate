# Agent 一句话请假原子工具

## 目标

`leave.apply` 允许用户用一句自然语言创建并提交一份本人请假申请。它是一个 L2、`SINGLE_WRITE`、`NEVER`、`SECONDARY` 工具，不是两个工具的编排，也不扩大 Phase 2 的自治上限。

## 执行边界

```text
用户输入
  → Planner 仅生成一个 leave.apply 步骤
  → 用户二次确认并取得一次性 confirmationToken
  → 任务入队
  → Worker 仅提交 stepId 与租约
  → Tool Gateway 重载不可变快照并实时鉴权
  → LeaveApplyToolHandler 使用网关注入的 tenantId/userId
  → LeaveWorkflowService 在同一事务中创建申请、提交、启动审批和写审计
```

工具参数只包含请假类型、起止日期、上午/下午、原因和可选审批人。禁止声明或接收 `userId`、`tenantId`、角色、权限、URL、SQL、文件路径或动态执行信息。

## 原子性与幂等边界

- 申请、流程实例、审批待办、流程动作日志和业务审计位于同一个领域事务中；任一步失败时数据库变更整体回滚。
- `agent_operation_key` 由可信的 taskId、stepId 和工具版本生成，客户端与模型不能提供。
- 工具禁止自动重试。相同步骤被重复分派时，只允许返回已进入 `PENDING` 的同一申请；发现其他中间状态时失败关闭。
- 不允许 `leave.apply` 调用 `leave.createDraft`、`leave.submit` 或任何 ToolHandler，也不允许把创建结果再次交给 Planner 形成递归计划。

## 权限与确认

- 代码注册表、平台工具、全局 Agent/执行/写开关、租户 Agent/写开关和实时 `leave:create` 权限必须同时有效。
- Tool Gateway 校验 task、step、租约、attempt、plan/schema/args 哈希、单写步骤上限、限流、确认凭证和前置审计。
- 领域服务再次按认证用户解析 tenantId，强制 applicant 为本人，并校验审批人属于同租户有效审批范围。
- L2 二次确认只确认当前 planHash，不能授予新权限；权限回收、租户变更或工具关闭后执行必须拒绝。

## 验收要点

- 正常请求只产生一份 `PENDING` 申请、一条运行中流程实例和一个审批待办。
- 缺失确认、确认重放、跨租户、伪造身份字段、无权限、无有效审批人、Schema 篡改和多写步骤均在写入前失败。
- Handler 继续受 ArchUnit 包依赖规则约束，只能由 Tool Gateway 分派，且不依赖 Mapper、HTTP、文件系统或脚本能力。
