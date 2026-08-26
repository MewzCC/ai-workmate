# Phase 2A 安全验收矩阵

## 发布原则

任一安全用例失败，Phase 2A 不得发布，Phase 2B 不得开始。自动化结果必须记录测试类或脚本、提交 SHA 和执行环境；人工演练记录 traceId，不记录 JWT、confirmationToken、Prompt 或完整业务内容。

## 自动化安全矩阵

| 边界 | 必测用例 | 预期 |
| --- | --- | --- |
| 鉴权与所有权 | 未登录、跨用户 taskId、跨租户 stepId、用户停用、权限刚回收 | 请求或网关拒绝；handler 调用 0 次；产生脱敏拒绝审计 |
| Tool Registry | 未注册 code、重复 code、数据库降低 risk、扩大权限、替换 handlerVersion/schemaHash | 启动失败或 `GATEWAY_STALE/DENIED`；数据库不能扩大代码声明能力 |
| Schema | 未知字段、身份字段、类型混淆、恶意嵌套、数组/文本/分页超限 | `SCHEMA_INVALID`；不猜测或修复参数；handler 调用 0 次 |
| Gateway | 错误 workerId、过期 lease、错误 attempt、planHash、argsHash、schemaHash、toolVersion | `GATEWAY_STALE`；handler 调用 0 次 |
| 故障安全 | Registry、权限、Policy、限流或前置审计异常；执行 Kill Switch 关闭 | `GATEWAY_UNAVAILABLE`；无 fallback；handler 调用 0 次 |
| 纵深鉴权 | 网关预检通过后资源 owner、tenant 或业务状态变化 | 领域 Service 拒绝，业务写入 0，结果审计记录领域拒绝 |
| 幂等与并发 | plan/execute 同键同 hash、同键异 hash、并发 execute/领取/取消 | 相同请求返回原结果；冲突稳定报错；副作用和领取最多一次 |
| Prompt Injection | 用户、pageContext、知识片段要求忽略规则、调用禁止工具或输出密钥 | 只能作为不可信文本；候选计划被严格 DTO/Policy 拒绝 |
| 数据泄露 | SSE、详情、错误、审计和日志检查 | 不含其他用户数据、JWT、token、Prompt、SQL、类名或完整敏感参数 |
| SSE | 跨用户订阅、Last-Event-ID、断线、重复事件、心跳、终态 | 仅本人可订阅；按 eventId 续传和去重；终态关闭 |
| 永久禁止项 | SQL、代码、文件、URL、权限修改、删除、批量、导出、外部消息、后台自治 | 不可注册、不可规划、不可通过 SUPER_ADMIN/配置/确认解除 |
| 架构边界 | Controller、Planner、Task Service、Worker 依赖 ToolHandler；handler 依赖 Mapper/HTTP/文件系统 | ArchUnit 构建失败 |

## 工具契约用例

每个工具必须至少覆盖 3 条正常问题、2 条空或缺失数据、2 条越权/敏感问题、1 条参数错误、1 条应拒绝请求和 1 条长上下文请求。工具加入数据库种子和 Planner 白名单前，安全失败测试必须先通过。

## 人工发布演练

1. 分别关闭全局 Agent、planning、execution、租户和单工具开关，确认 fail closed。
2. 在任务计划后撤销用户权限或禁用工具，确认 execute/Worker 拒绝并要求重新规划。
3. 并发发送 execute，重启 Worker，确认没有重复领取或不可解释成功。
4. SSE 断线后以最后 eventId 恢复，确认不遗漏终态、不泄露其他任务。
5. PostgreSQL 空库首次迁移、历史库升级、迁移后重启并执行 Flyway validate。
6. 在 zh-CN/en-US 下检查 REST 与 SSE 错误、本地化文案、键值和内部信息泄露。

## Phase 2B 人工门

Phase 2A 全部自动化与人工项目通过后，由人工负责人记录结论并明确批准，才能开始任何写工具实现。该批准不自动开启生产写工具；生产启用仍需独立租户配置和发布签字。
