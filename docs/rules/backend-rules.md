# Backend Rules

## 技术栈

- Java 17。
- Spring Boot 3.3。
- Spring Security + JWT。
- Spring AI OpenAI-compatible client。
- MyBatis-Plus。
- PostgreSQL + pgvector。
- Redis。

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
- 复杂查询需要 XML 或注解 SQL 时必须可读、可索引、可解释。

DTO：

- 请求 DTO 使用 Bean Validation。
- 响应 DTO 避免暴露密码、密钥、内部状态字段。
- 字段语义写清楚，不复用无关 DTO。

## 异常与返回

- 普通 REST 返回 `Result<T>`。
- 业务异常应该映射为明确 code 和 message。
- 不把 Java 堆栈返回给前端。
- 全局异常处理集中在 `GlobalExceptionHandler`。
- SSE 不能只依赖 HTTP 状态码，流中错误要设计 event 或 data 约定。

## 安全

- 密码必须使用强哈希算法，不允许明文或可逆加密存储。
- JWT secret 必须来自环境变量，默认值仅开发可用。
- 鉴权失败返回 401，权限不足返回 403。
- 用户只能访问自己的 conversation、message、knowledge_doc。
- 所有文件上传必须校验类型、大小、后缀、内容和存储路径。
- 模型输出不能直接执行为工具调用；工具调用必须有白名单和参数校验。

## 数据库

- 新表必须包含主键、必要索引、创建时间、更新时间或说明为什么不需要。
- 高频查询必须建立索引。
- 生产迁移脚本必须可重复、可回滚或有人工回滚说明。
- 避免使用 `"user"` 这类需要转义的保留字作为新表名。
- RAG chunk 表必须包含 doc_id、chunk_index、content、embedding、metadata。

## 日志

- 使用结构化、低敏日志。
- info 记录关键业务完成点。
- warn 记录可恢复异常。
- error 记录不可恢复异常并带异常对象。
- 不输出完整 prompt、完整用户文件内容、JWT、API Key。

## 测试

- Service 层新增复杂逻辑必须有单元测试。
- Controller 新增接口应有 WebMvc 或集成测试。
- 安全相关改动必须覆盖未登录、越权、过期 token。
- 数据访问复杂查询必须覆盖空数据、边界数据和权限过滤。
