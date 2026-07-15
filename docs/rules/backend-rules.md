# Backend Rules

## 技术栈

- Java 17。
- Spring Boot 3.3。
- Spring Security + JWT。
- Spring AI OpenAI-compatible client。
- MyBatis-Plus。
- PostgreSQL + pgvector。
- Redis。

## Skill 触发

- 修改 Spring Boot、Spring Security、JWT、MyBatis-Plus、SSE、RAG、Agent 后端能力时，读取 `docs/skills/backend-engineering-skill.md`。
- 修改 OA AI 后端接口、`SystemController`、`AiTaskController`、`AiTaskService` 或 `AiTask*DTO` 时，必须读取 `docs/skills/oa-workbench-skill.md`。
- 修改真实 Agent、Tool Calling、RAG 权限和提示词边界时，读取 `docs/skills/agent-engineering-skill.md`。

## 分层规范

Controller：

- 只处理 HTTP、鉴权上下文、参数校验、响应包装。
- 不直接调用 mapper。
- 不拼接 SQL。
- 不包含模型提示词的大段业务逻辑。

Service：

- 业务流程入口。
- 用接口表达能力，用 `impl` 承载实现。
- 管理事务边界。
- 对外返回 DTO 或业务模型，不返回框架内部对象。

Mapper：

- 只负责数据库访问。
- 简单 CRUD 优先使用 MyBatis-Plus。
- 复杂查询需要可读、可索引、可解释。

DTO：

- 请求 DTO 使用 Bean Validation。
- 响应 DTO 避免暴露密码、密钥、内部状态字段。
- 字段语义必须清楚，不复用无关 DTO。

## OA AI 接口规则

OA AI 接口不得再提供伪造成功的 mock 能力：

- `GET /api/system/health`
- `POST /api/ai/tasks/plan`
- `POST /api/ai/tasks/execute`

约束：

- 返回统一 `Result<T>`。
- `AiTaskController` 只做校验和服务调用。
- `AiTaskService` 表达 plan/execute 能力。
- 禁止确定性 mock 数据和 fallback mock。
- `plan` 必须基于真实能力或明确返回能力不可用。
- `execute` 必须要求 `confirm=true`。
- 真实执行前必须完成鉴权、权限、幂等、审计和高风险确认。
- 没有真实 `ChatClient`、`AI_API_KEY` 或业务依赖时必须返回可解释失败。
- 不得伪造真实审批、真实导出、真实上传成功。

## 安全

- JWT secret 必须来自环境变量；默认值仅开发可用。
- 鉴权失败返回 401，权限不足返回 403。
- 用户只能访问自己的 conversation、message、knowledge_doc。
- 工具调用必须白名单化，参数必须校验。
- 模型输出不能直接执行为工具调用。
- `SecurityConfig` 仅允许放行：
  - `/api/auth/**`
  - `/api/system/**`
  - OPTIONS
- `/api/ai/tasks/**` 必须通过 JWT 认证，身份、角色和权限从服务端上下文获取。
- 不得为了联调放开全部接口。
- 不得破坏 `/api/chat/stream` 的认证逻辑。

## 异常与返回

- 普通 REST 返回 `Result<T>`。
- 业务异常映射为明确 code 和 message。
- 不把 Java 堆栈返回给前端。
- 全局异常处理集中在 `GlobalExceptionHandler`。
- SSE 不能只依赖 HTTP 状态码，流中错误要设计事件或 data 约定。

## 数据库

- 新表必须包含主键、必要索引、创建时间、更新时间，或说明为什么不需要。
- 高频查询必须建立索引。
- 迁移脚本必须可重复、可回滚或有人工回滚说明。
- 避免使用 `"user"` 这类需要转义的保留字作为新表名。
- RAG chunk 表必须包含 doc_id、chunk_index、content、embedding、metadata。

## 日志

- 使用结构化、低敏日志。
- info 记录关键业务完成点。
- warn 记录可恢复异常。
- error 记录不可恢复异常并带异常对象。
- 不输出完整 prompt、完整用户文件内容、JWT、API Key。

## 测试

- Service 层新增复杂逻辑应有单元测试。
- Controller 新增接口应有 WebMvc 或集成测试。
- 安全相关改动必须覆盖未登录、越权、过期 token。
- 当前本机若缺 Java 17 或 Maven，最终说明必须写明无法运行后端测试的原因。
