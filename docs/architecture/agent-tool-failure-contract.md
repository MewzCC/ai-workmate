# Agent 工具失败分类契约

## 目标

ToolGateway 在 Handler 调用后必须区分确定性领域拒绝与结果不确定故障，同时禁止把异常消息、堆栈、SQL、表名、连接信息或内部策略原因写入任务结果、SSE 和模型上下文。

## 稳定分类

只有代码白名单内的 `BusinessException.errorCode` 可以转换为用户可理解的稳定类别：

| 稳定类别 | 含义 | 是否允许自动重试 |
| --- | --- | --- |
| `TOOL_INPUT_REJECTED` | 封闭参数或领域参数校验失败 | 否，需修正输入并重新规划 |
| `TOOL_ACCESS_REJECTED` | 实时身份、权限或资源访问拒绝 | 否 |
| `TOOL_RESOURCE_NOT_FOUND` | 资源不存在或对当前账号不可见 | 否 |
| `TOOL_STATE_CONFLICT` | 状态、版本、审批人或幂等命令冲突 | 否，需刷新业务状态 |
| `GATEWAY_THROTTLED` | 领域或网关限流 | 当前任务失败，不由模型循环重试 |

未列入白名单的异常一律失败关闭：只读工具返回 `GATEWAY_UNAVAILABLE`；写工具返回 `TOOL_RESULT_UNKNOWN` 并进入人工核验，禁止自动重放。`SYSTEM_ERROR`、远程超时、连接断开、序列化失败和未知运行时异常不得根据错误消息猜测执行结果。

## 审计与前端

- 审计只保存 `DOMAIN_INPUT_REJECTED`、`DOMAIN_ACCESS_REJECTED`、`DOMAIN_RESOURCE_NOT_FOUND`、`DOMAIN_STATE_CONFLICT`、`DOMAIN_RATE_LIMITED`、`DOMAIN_FAILURE` 或 `DOMAIN_OUTCOME_UNKNOWN`，不保存原始异常消息。
- Worker 只把稳定公开类别写入 task/step 和 SSE。
- OA 任务中心使用 `zh-CN` 与 `en-US` 文案解释稳定类别；未知类别使用通用失败文案，不能渲染服务端异常或 HTML。
- 分类不产生授权、不改变 ToolDefinition 风险、确认、重试和副作用声明。

未来远程 Adapter 必须把确定性的 4xx 领域拒绝映射为受控 `BusinessException`；网络超时、5xx、响应损坏和无法确认的远程结果继续作为未知故障处理，不能用 HTTP 客户端自动重试写请求。
