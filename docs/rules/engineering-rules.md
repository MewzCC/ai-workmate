# Engineering Rules

## 总体要求

本项目按企业级全栈项目维护。AI 修改代码时必须保持边界清晰、可测试、可回滚。

## 代码层级

前端层级：

- `app` 只放页面编排、路由入口和全局布局。
- `components` 放 UI 与业务组件，复杂组件按功能继续拆分。
- `lib/api.ts` 只负责 HTTP/SSE 调用、鉴权 header、响应解析。
- `store` 只放前端状态，不直接写复杂业务规则。
- `types` 是前后端契约镜像，接口字段变动必须同步更新。

后端层级：

- `controller` 只负责协议适配、参数校验、鉴权上下文、响应包装。
- `service` 负责编排业务流程，接口先行。
- `service.impl` 负责具体实现，不把 HTTP 细节带入业务层。
- `mapper` 只做数据访问，不写业务判断。
- `entity` 映射数据库结构，不承载请求语义。
- `dto` 表达 API 请求/响应，不直接暴露 entity。
- `common` 放跨模块通用能力，例如统一返回、异常处理、错误码。
- `config` 放框架配置，禁止混入业务逻辑。

## 编码原则

- 优先复用现有技术栈：Next.js、Tailwind、Zustand、Spring Boot、Spring AI、MyBatis-Plus。
- 新增依赖必须有明确收益，且不能替代已有轻量实现。
- 命名表达业务含义，避免 `data1`、`handleClick2`、`temp` 这类不可维护名称。
- 删除死代码；保留 TODO 时必须写清楚触发条件和负责人语义。
- 不在日志中输出密码、JWT、API Key、完整用户输入或模型原始敏感输出。
- 涉及并发、流式输出、持久化时，必须考虑幂等、取消、失败恢复。

## API 契约

- REST 普通响应使用统一 `Result<T>`。
- SSE 流式接口明确 `Content-Type: text/event-stream` 与错误事件格式。
- 字段命名前后端统一使用 camelCase；数据库使用 snake_case。
- 新增接口必须定义请求 DTO、响应 DTO、错误场景和鉴权要求。
- 前端不得猜测后端字段；后端不得依赖前端隐藏字段保证安全。

## 配置与环境

- 生产敏感配置只允许从环境变量读取。
- 默认配置可以保留开发占位值，但必须明显标记不可生产使用。
- `application-dev.yml` 只放开发环境差异，通用配置放 `application.yml`。
- Docker Compose 用于本地依赖启动，不能内置真实密钥。

## 验证要求

- 前端：至少运行 `npm run lint`，涉及构建或路由时运行 `npm run build`。
- 后端：至少运行 `mvn test`，涉及启动配置时尝试 `mvn spring-boot:run`。
- 数据库：涉及 schema 时验证初始化 SQL 可重复执行。
- 流式聊天：涉及 SSE 时手动或脚本验证首 token、错误中断、完成状态。
