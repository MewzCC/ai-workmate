# AI Task v2 API 与错误契约

## 1. 状态与信任边界

- 版本：`2.0-frozen`
- 对应阶段：Phase 2A（A1 受控只读）；Phase 2B 写工具须通过独立人工发布门。
- 鉴权：除 `GET /api/system/health` 外，所有 AI Task 接口必须携带有效会话。
- 外部 `taskId` 是服务端生成的不可枚举字符串；数据库 task/step 主键不对客户端暴露。
- userId、tenantId、角色、权限、数据范围、toolCode、args 和风险等级不得由客户端声明。
- 模型只生成候选计划。工具只能由进程内 `ToolGateway.execute(stepId, workerLease)` 分派，不提供公共 Tool Gateway API。

所有普通响应继续使用 `Result<T>`。成功响应的业务 `code` 固定为 `200`；创建异步执行时 HTTP 状态为 `202 Accepted`。

## 2. 计划接口

`POST /api/ai/tasks/plan`

必需请求头：

```http
Idempotency-Key: 7d9737d8-87a6-40e8-b1d3-70d65b79aa35
Accept-Language: zh-CN
```

请求：

```json
{
  "input": "查看我的待办并按超时风险排序",
  "pageId": "todo-list",
  "pageContext": {
    "status": "PENDING"
  }
}
```

约束：input 最大 4 KiB；pageContext 最大 16 KiB、深度不超过 3，且按 pageId 的服务端字段白名单重建。未知页面没有工具能力。相同用户、租户、操作和幂等键且 requestHash 相同返回原任务；hash 不同返回 `IDEMPOTENCY_CONFLICT`。

成功响应：

```json
{
  "taskId": "8a87d7e9-47cb-4a4f-a762-ae3fc40bc262",
  "status": "PLAN_READY",
  "planVersion": 1,
  "planHash": "sha256:...",
  "riskLevel": "L0",
  "confirmationRequired": false,
  "expiresAt": "2026-08-25T16:30:00+08:00",
  "summary": "查询当前用户待办",
  "steps": [
    {
      "sequence": 1,
      "toolCode": "todo.query",
      "title": "查询我的待办",
      "argumentsSummary": { "status": "PENDING", "page": 1, "size": 20 }
    }
  ]
}
```

plan 不签发 confirmationToken，也不自动执行 L0；用户必须显式调用 execute。

## 3. 确认凭证

`POST /api/ai/tasks/{taskId}/confirmation-token`

仅供处于 `WAITING_CONFIRMATION` 的 L1/L2 任务使用。Phase 2A 保留契约但没有可规划的写工具。

```json
{
  "planVersion": 1,
  "planHash": "sha256:..."
}
```

响应包含 `confirmationToken` 与 `expiresAt`。token 默认十分钟有效，仅保存于前端组件内存；服务端只保存哈希。重新签发会使旧 token 失效；签发、execute 和 Worker 执行前均重新鉴权。

## 4. 执行接口

`POST /api/ai/tasks/{taskId}/execute`

要求独立的 `Idempotency-Key`。客户端不能提交 steps、toolCode、args、用户或权限字段。

```json
{
  "planVersion": 1,
  "planHash": "sha256:...",
  "confirmationToken": null
}
```

L0 从 `PLAN_READY` 原子迁移到 `QUEUED`；L1/L2 必须在一次条件更新中消费 confirmationToken 并进入 `QUEUED`。成功返回 HTTP 202：

```json
{
  "taskId": "8a87d7e9-47cb-4a4f-a762-ae3fc40bc262",
  "status": "QUEUED",
  "statusUrl": "/api/ai/tasks/8a87d7e9-47cb-4a4f-a762-ae3fc40bc262",
  "eventsUrl": "/api/ai/tasks/8a87d7e9-47cb-4a4f-a762-ae3fc40bc262/events"
}
```

旧 `POST /api/ai/tasks/execute` 与 `confirm=true` 在前后端同一版本删除，不保留兼容成功层。

## 5. 查询、取消与事件

- `GET /api/ai/tasks?status=&from=&to=&page=1&size=20`：只返回当前 tenantId + userId 的任务。
- `GET /api/ai/tasks/{taskId}`：返回脱敏任务快照、步骤和结果。
- `POST /api/ai/tasks/{taskId}/cancel`：只取消本人处于可取消状态的任务。
- `GET /api/ai/tasks/{taskId}/events`：`text/event-stream`，使用认证 Cookie 和 `Last-Event-ID` 请求头续传。

事件类型固定为 `snapshot`、`step-started`、`step-completed`、`task-completed`、`task-failed`、`heartbeat`。event payload 只包含当前用户可见的脱敏字段；JWT 和 confirmationToken 禁止进入 URL、事件、日志或错误消息。

## 6. 状态机

```text
RECEIVED -> PLANNING
PLANNING -> PLAN_READY | WAITING_CONFIRMATION | REJECTED
PLAN_READY -> QUEUED | CANCELLED
WAITING_CONFIRMATION -> QUEUED | EXPIRED | CANCELLED
QUEUED -> RUNNING | CANCELLED
RUNNING -> SUCCEEDED | PARTIALLY_SUCCEEDED | FAILED | TIMED_OUT
```

所有状态转换使用旧状态 + version 条件更新。并发确认、execute、取消和 Worker 领取只能有一个成功。

## 7. 页面能力与 Phase 2A 工具

| pageId | 工具 |
| --- | --- |
| `todo-list` | `todo.query` |
| `my-applications` | `leave.mine` |
| `knowledge-base` | `knowledge.search` |
| `message-center` | `notification.mine` |
| `dashboard` | `todo.query`、`notification.mine` |
| `ai-workspace` | 当前用户全部获准 L0 工具 |

工具白名单是代码注册、平台启用、租户启用、实时业务权限、页面能力和永久禁止清单的交集。`route:*` 权限不能代替 `todo:read`、`leave:read:self`、`knowledge:search` 或 `notification:read:self`。

## 8. 稳定错误码

| HTTP | errorCode | 语义 |
| --- | --- | --- |
| 400 | `REQUEST_INVALID` / `SCHEMA_INVALID` | 请求或严格 schema 非法 |
| 401 | `AUTH_REQUIRED` / `AUTH_TOKEN_INVALID` / `AUTH_TOKEN_EXPIRED` | 会话不可用 |
| 403 | `PERMISSION_DENIED` / `RESOURCE_SCOPE_DENIED` | 实时权限或资源范围拒绝 |
| 409 | `IDEMPOTENCY_CONFLICT` / `INVALID_TASK_STATE` | 幂等键冲突或非法状态迁移 |
| 409 | `CONFIRMATION_REQUIRED` / `CONFIRMATION_EXPIRED` | 确认缺失、过期或已消费 |
| 409 | `TOOL_VERSION_CHANGED` / `GATEWAY_STALE` | 计划、schema、工具或租约已变化 |
| 429 | `RATE_LIMITED` / `GATEWAY_THROTTLED` / `BUDGET_EXCEEDED` | 达到运行上限 |
| 503 | `AI_TASK_CAPABILITY_UNAVAILABLE` / `GATEWAY_UNAVAILABLE` | 模型、Registry、策略、权限、限流或审计依赖不可用；fail closed |
| 403 | `GATEWAY_DENIED` | 网关确定性拒绝且 handler 未调用 |
| 500 | `TOOL_FAILED` / `SYSTEM_ERROR` | 脱敏内部失败 |

错误响应和 SSE 错误事件按 `Accept-Language` 本地化，不返回堆栈、SQL、表名、Prompt、安全策略细节或敏感参数。

## 9. 默认运行与留存限制

- 计划最多 3 步、每任务最多 5 次工具调用、同用户最多 2 个并发任务。
- 查询默认 20 条、硬上限 50；knowledge topK 默认 5、硬上限 10。
- 单步骤结果最多 256 KiB；单工具默认 15 秒、硬上限 30 秒；任务默认 60 秒、硬上限 120 秒。
- plan 每用户每分钟 10 次；confirmation 每用户每任务每分钟 3 次；execute 每用户每分钟 5 次。
- 普通任务保留 90 天；详细事件和调用审计保留 30 天，按租户和时间分批清理。
- 平台、规划、执行和写工具开关默认关闭；租户配置只能收紧平台限制。
