# Backend Engineering Skill

## 触发场景

当任务涉及 Spring Boot、Spring Security、Spring AI、MyBatis-Plus、JWT、PostgreSQL、Redis、SSE、RAG、Agent 后端能力时使用本 skill。

如果任务涉及 OA mock 接口、`AiTaskController`、`SystemController`、`AiTaskService`、`MockAiTaskServiceImpl`、`AiTask*DTO`，请同时读取 `docs/skills/oa-workbench-skill.md`。

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

## OA mock 后端

当前 OA mock 后端只负责联调：

- 不接数据库。
- 不依赖真实 LLM。
- 不要求 `AI_API_KEY`。
- 不改变真实业务数据。
- 只返回确定性 mock 结果。
- 安全配置只放行 mock 所需接口，不放开全部接口。

## Spring AI 规范

- 模型配置通过环境变量覆盖：`AI_API_KEY`、`AI_BASE_URL`、`AI_MODEL`。
- 提示词模板集中管理，不散落在 controller。
- Tool Calling 必须白名单化并做参数校验。
- 模型异常按超时、限流、鉴权、内容安全、未知错误分类。
- OA mock plan/execute 当前不调用真实 ChatClient。

## 测试重点

- 登录注册成功和失败。
- JWT 过期、伪造、缺失。
- SSE 正常完成、模型异常、客户端中断。
- OA mock plan/execute 正常返回。
- SecurityConfig 未放开非 mock 业务接口。

