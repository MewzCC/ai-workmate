# Phase 0 工程基线决策

## 1. 数据库

- 生产目标数据库固定为 PostgreSQL，向量能力后续使用 pgvector。
- 后端移除 MySQL runtime driver，禁止新增 MySQL 专属实现。
- `schema-mysql.sql` 与 `schema-h2.sql` 仅作为历史兼容参考冻结，不再作为生产初始化入口。
- 数据库采用单一 `backend/src/main/resources/db/init.sql` 作为初始化和升级入口，不再维护任何版本化 SQL 文件。
- 所有后续 schema 变更必须以幂等方式合并到 `init.sql`；生产执行前先备份，并在对应变更文档中记录前向兼容与人工回滚步骤。

## 2. 身份与权限

- JWT 是当前开发阶段身份来源，AI Task 必须认证。
- role 目前从 JWT claim 重建；Phase 1 改为按 userId 查询带版本的 IAM 权限快照。
- tenantId、dataScope 尚未建立，因此 Phase 0 不开放任何真实 OA Tool。
- 执行接口使用服务端方法权限做最低边界，Phase 1 再接统一 permission code。

## 3. AI 能力

- 当前聊天能力可调用真实模型供应商；模型错误通过 SSE `error` 事件显式返回。
- OA plan/execute 只有在真实领域 Tool、权限和审计闭环接入后才能返回成功。
- 没有真实工具时返回 `AI_TASK_CAPABILITY_UNAVAILABLE`，禁止生成临时 taskId 或 auditId。

## 4. 配置与日志

- 生产必填：`JWT_SECRET`、`DB_PASSWORD`、`AI_API_KEY`。
- `prod` profile 使用默认 JWT 或数据库密码时启动失败。
- 日志不得输出 JWT、API Key、完整 prompt 或模型原始敏感输出。
- MyBatis 使用 SLF4J，Spring AI 默认 info；调试日志仅在受控开发环境短时开启。

## 5. 追踪

- 每个请求具备 requestId 和 traceId，并同时写入响应体、响应头和 MDC。
- 客户端可传递安全格式的 `X-Request-Id`、`X-Trace-Id`；非法值由服务端替换。
- Phase 1 的审计、数据库写入和后续 Tool Invocation 必须沿用同一 traceId。

## 6. 回滚

只能回滚到“能力不可用且明确失败”的版本。不得恢复匿名 AI Task、前端 fallback 或模拟成功服务。
