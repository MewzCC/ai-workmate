# Backend Engineering Skill

## 触发场景

当任务涉及 Spring Boot、Spring Security、Spring AI、MyBatis-Plus、JWT、PostgreSQL、Redis、SSE、RAG、Agent 后端能力时使用本 skill。

如果任务涉及 OA AI 接口、`AiTaskController`、`SystemController`、`AiTaskService`、`AiTask*DTO`，请同时读取 `docs/skills/oa-workbench-skill.md`。

如果任务涉及 Phase 2 Tool Gateway 或 ToolHandler，必须同时读取 `docs/roadmap/phase-2-agent-security-boundary.md` 与 `docs/architecture/agent-tool-gateway.md`。

## 当前后端结构

- 包名：`com.aiworkmate`。
- REST 入口：`controller`。
- 业务抽象：`service`。
- 业务实现：`service.impl`。
- 数据访问：`mapper`。
- 数据模型：`entity`。
- API 契约：`dto`。
- 公共能力：`common`。
- 配置：`config`。

## 开发流程

1. 先定义接口契约：URL、method、request DTO、response DTO、鉴权要求。
2. 在 service 接口表达业务能力。
3. 在 impl 中实现业务编排。
4. mapper 只处理数据访问。
5. controller 只做协议适配和响应包装。
6. 补充异常处理、日志和测试。

## REST 规范

- Auth：`/api/auth/*`
- Chat：`/api/chat/*`
- OA System：`/api/system/*`
- OA AI Tasks：`/api/ai/tasks/*`
- 后续 Agent：`/api/agents/*`
- 后续 Knowledge：`/api/knowledge/*`

命名：

- 查询列表：`GET /api/<resource>`
- 查询详情：`GET /api/<resource>/{id}`
- 创建：`POST /api/<resource>`
- 更新：`PUT /api/<resource>/{id}`
- 删除：`DELETE /api/<resource>/{id}`
- 动作：`POST /api/<resource>/{id}/<action>`

## OA AI 后端

OA AI 后端必须接入真实认证、权限、审计和可观测流程：

- 禁止新增或保留伪造成功的 mock 能力。
- 禁止接口失败后 fallback mock。
- 未接入真实业务能力时必须返回明确错误，不得模拟执行成功。
- 真实写操作必须先完成权限校验、幂等设计、人工确认和审计记录。
- 真实 LLM 接入必须通过环境变量配置 `AI_API_KEY`、`AI_BASE_URL`、`AI_MODEL`。
- 安全配置不得为了联调放开全部接口。
- 所有 Agent ToolHandler 必须由进程内 Tool Gateway 分派；Worker 只传 stepId 与租约，网关不开放公共 HTTP API。
- 只有 gateway 包可以依赖 handler；使用 ArchUnit 或等价测试阻止 Controller、Planner、Task Service、Worker 直接调用。
- Tool Gateway 必须实时校验租户、用户权限、任务/步骤状态、租约、attempt、不可变哈希、确认、预算和前置审计；异常 fail closed。
- 网关不是领域鉴权替代品，handler 调用的领域 Service 仍须执行资源归属和业务状态条件校验。

## Spring AI 规范

- 模型配置通过环境变量覆盖：`AI_API_KEY`、`AI_BASE_URL`、`AI_MODEL`。
- 提示词模板集中管理，不散落在 controller。
- Tool Calling 必须白名单化并做参数校验。
- 模型异常按超时、限流、鉴权、内容安全、未知错误分类。
- OA plan/execute 不得返回 mock 结果；未接真实能力时返回可解释失败。

## 测试重点

- 登录注册成功和失败。
- JWT 过期、伪造、缺失。
- SSE 正常完成、模型异常、客户端中断。
- OA plan/execute 鉴权、权限不足、确认缺失、真实执行失败。
- SecurityConfig 未放开非公开业务接口。

## 数据库脚本

- 数据库结构、索引与种子数据变更统一通过 Flyway 版本化迁移 `backend/src/main/resources/db/migration/V*__*.sql` 管理；后端启动自动执行，禁止修改已发布的历史迁移（checksum 校验会失败）。
- 旧版 `backend/src/main/resources/db/init.sql` 保留为幂等基线参考与手工建库脚本，不再追加新结构变更。
- 新迁移必须保持幂等，支持新库初始化和旧库重复执行，优先使用事务、`IF NOT EXISTS`、条件 `DO` 块和 `ON CONFLICT`；迁移由 Flyway 统一管理事务，脚本内不要再写 `BEGIN;`/`COMMIT;`。
