# 本人费用草稿取消原子工具

## 范围与调用链

`expense.cancelDraft` 只取消一份本人费用草稿，不删除数据，不取消审批中的流程，不付款。
它绑定费用页及按业务页面权限过滤的 Workspace；成功结果复用现有共享导航，定位费用申请详情。

调用链固定为 ToolGateway → `ExpenseCancelDraftToolHandler` → `ExpenseToolPort` →
`ExpenseAgentDomainToolAdapter` → `ExpenseApplicationService.cancelAgentDraft` → 通用审批取消事务。
复用 `TypedVersionedWriteToolHandler`，不增加公共执行 API 或任意请求转发，不复制状态机。

## 冻结契约

- 输入仅 `applicationId`（正整数）和 `version`（0–2147483646）；拒绝额外字段。
- 输出仅 `applicationId`、固定 `formKey=expense-application`、`status=CANCELLED`、新 `version`。
- Handler 版本 `1.0.0`，Schema Hash 为
  `sha256:a8b1701915dd62722f5ec22a97d3195b2671a66147dc448964ed83a15601490b`。
- L1、SELF、EXPLICIT、SINGLE_WRITE、NEVER、FULL_WRITE_AUDIT；最多一条、4096 字节、15 秒。
- 独立权限 `agent:tool:expense.cancelDraft`；领域再检查实时 `route:approval-start` 和 `approval:cancel`。

租户及申请人只取可信身份。领域读取 SQL 同时限定租户、申请人、费用表单与 ID，
不能通过其他表单、管理可见范围或模型传入的身份扩大资源范围。
通用取消再次验证归属、DRAFT 状态和乐观锁；状态更新和成功审计同事务。

迁移 `V202610221900` 只为已有 `approval:cancel` 的角色增加对应工具权限种子；
工具本身 `enabled=false`，不改变全局/租户写开关。管理员仍需完成独立人工发布门，
不能将本节点当作所有写工具已经可运行。

## 失败与回滚

跨租户、跨申请人和非费用表单返回资源不存在；撤权拒绝，非草稿和旧版本失败关闭。
重复或并发取消不重复更新或产生重复成功审计。审计失败和外层事务回滚均不留下成功状态。
结果不明时先人工只读核验状态/版本/审计，禁止自动重试。

回滚应先关闭工具并停止新增执行，再同步回退 Handler、定义、枚举、页面绑定与前端回执映射。
保留已执行 Flyway 文件及校验和；不要删除取消记录、历史审计或回退已发生的业务状态。
本工具不撤销通用审批取消事务的前置修复。

## 验证边界

单元门禁覆盖冻结 Hash、封闭 Schema、策略、类型化传参、实时撤权、费用归属和页面能力绑定。
真实 PostgreSQL 门禁覆盖空库与旧库升级、工具默认关闭、权限种子、重复迁移、validate、
版本冲突、跨用户/租户/表单、非草稿、并发单次成功及审计回滚。
这些证据不等于真实 LLM、Worker 和浏览器逐工具验收；费用催办及其他页面剩余交互仍需继续。

2026-10-22 验证结果：

- OA `npm run lint` 零错误（3 条既有警告）、`npm run test` 49 个文件 298 项通过，`npm run build` 通过。
- Java 17 `mvn clean test` 1331 项零失败、零错误，10 项环境依赖测试跳过。
- 显式指定本地 PostgreSQL 的 `P1PostgresMigrationIT` 1 项多场景门禁零失败、零跳过；
  空 schema 应用 155 个迁移、156 项 validate，旧库增量升级与重复执行零迁移通过，测试 schema 自动清理。
- 隔离端口开发后端首次启动将开发库升级至 `V202610221900`，健康检查 HTTP 200；
  二次启动 156 项 validate、零迁移、健康检查 HTTP 200，核对工具仍关闭且 Hash 匹配。
  仅停止本轮自行启动的实例，不重启用户应用；启动参数关闭 Agent 执行及清理，不修改持久运行开关。

保留既有 AntD/Router 弃用提示、聊天输入器 NaN 样式和构建大分包提示；本节点未修复这些问题。
