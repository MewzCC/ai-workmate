# AI Task v2 API 与错误契约

## 1. 状态

- 版本：`2.0-draft`
- 对应阶段：Phase 0
- 鉴权：除 `GET /api/system/health` 外，AI Task 接口必须携带有效 JWT。
- 当前能力：尚未注册真实 OA Tool，认证后统一返回 `AI_TASK_CAPABILITY_UNAVAILABLE`，不创建任务、计划、审计或执行结果。

## 2. 统一响应

```json
{
  "code": 50301,
  "errorCode": "AI_TASK_CAPABILITY_UNAVAILABLE",
  "message": "AI 任务能力尚未接入真实业务工具，当前不可用",
  "data": null,
  "requestId": "7ecbf61f4d004fa88fe979bcdac98768",
  "traceId": "7ecbf61f4d004fa88fe979bcdac98768"
}
```

- `code`：稳定数字业务码，成功固定为 `200`。
- `errorCode`：稳定字符串错误码，成功时为 `null`。
- `requestId`：单次 HTTP 请求标识，可由客户端通过 `X-Request-Id` 传入安全格式值。
- `traceId`：端到端追踪标识；未传 `X-Trace-Id` 时与 requestId 相同。
- 响应头同步返回 `X-Request-Id`、`X-Trace-Id`。

## 3. 计划接口

`POST /api/ai/tasks/plan`

```json
{
  "input": "查看我的待办并按超时风险排序",
  "pageId": "todo"
}
```

身份、角色、租户和数据范围只能从服务端认证上下文重建。客户端发送的 `role`、`tenantId` 或权限字段不具有授权语义。

## 4. 执行接口

`POST /api/ai/tasks/execute`

```json
{
  "taskId": "task_01",
  "confirm": true
}
```

- `confirm=false` 返回 `AI_TASK_CONFIRMATION_REQUIRED`。
- 当前仅管理员类角色可进入执行服务；普通用户返回 `PERMISSION_DENIED`。
- 服务端根据持久化计划决定动作类型，客户端不得声明可信 `type`。
- Phase 0 没有真实工具，因此即使通过认证和角色校验也返回能力不可用。

## 5. 错误码

| HTTP | code | errorCode | 前端处理 |
| --- | ---: | --- | --- |
| 400 | 40001 | `REQUEST_INVALID` | 展示字段错误，不自动重试 |
| 401 | 40101 | `AUTH_REQUIRED` | 清理无效会话并提示登录 |
| 401 | 40102 | `AUTH_TOKEN_INVALID` | 清理 token 并提示重新登录 |
| 401 | 40103 | `AUTH_TOKEN_EXPIRED` | 清理 token 并提示重新登录 |
| 403 | 40301 | `PERMISSION_DENIED` | 展示权限不足，不重试 |
| 403 | 40302 | `RESOURCE_FORBIDDEN` | 隐藏资源细节，不重试 |
| 409 | 40901 | `AI_TASK_CONFIRMATION_REQUIRED` | 返回确认步骤 |
| 429 | 42901 | `RATE_LIMITED` | 延迟后允许重试 |
| 503 | 50301 | `AI_TASK_CAPABILITY_UNAVAILABLE` | 展示能力不可用，不产生本地结果 |
| 503 | 50302 | `AI_CHAT_UNAVAILABLE` | 终止 SSE 并展示 traceId |
| 503 | 50311 | `TOOL_CAPABILITY_UNAVAILABLE` | 展示依赖不可用，不伪造成功 |
| 500 | 50001 | `SYSTEM_ERROR` | 展示通用错误，保留 traceId |

## 6. SSE v1

`POST /api/chat/stream` 返回 `text/event-stream`，事件类型固定为：

- `delta`：`data` 为增量文本。
- `done`：正常完成，不再有后续 token。
- `error`：流中错误，包含 `errorCode`、用户可读消息、requestId 和 traceId。

```json
{
  "type": "error",
  "data": "AI 对话服务暂时不可用",
  "errorCode": "AI_CHAT_UNAVAILABLE",
  "requestId": "...",
  "traceId": "..."
}
```

流中错误不得转成普通助手回答，也不得把错误文本持久化为成功消息。
