# Backend Engineering Skill

## 触发场景

当任务涉及 Spring Boot、Spring AI、MyBatis-Plus、JWT、PostgreSQL、Redis、SSE、RAG、Agent 后端能力时使用本 skill。

## 当前后端架构

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

- Auth：`/api/auth/*`。
- Chat：`/api/chat/*`。
- Conversation：`/api/conversations/*`。
- Knowledge：`/api/knowledge/*`。
- Agent：`/api/agents/*`。

命名：

- 查询列表：`GET /api/<resource>`。
- 查询详情：`GET /api/<resource>/{id}`。
- 创建：`POST /api/<resource>`。
- 更新：`PUT /api/<resource>/{id}`。
- 删除：`DELETE /api/<resource>/{id}`。
- 动作：`POST /api/<resource>/{id}/<action>`。

## SSE 规范

聊天流式输出必须考虑：

- 开始事件：可选 `event: start`。
- 内容事件：`data: <chunk>`。
- 错误事件：`event: error` + 可解释 message。
- 完成事件：`data: [DONE]` 或 `event: done`。
- 服务端完成后持久化 assistant 消息。
- 客户端断开时停止不必要的后续处理。

## Spring AI 规范

- 模型配置通过环境变量覆盖：`AI_API_KEY`、`AI_BASE_URL`、`AI_MODEL`。
- 提示词模板集中管理，不散落在 controller。
- ChatMemory 后续应从内存升级到 Redis/PostgreSQL。
- Tool Calling 必须白名单化并做参数校验。
- 模型异常按超时、限流、鉴权、内容安全、未知错误分类。

## 数据与事务

- 创建 conversation 和保存 message 应在业务层保证一致性。
- 写入多表时使用事务。
- 用户资源查询必须带 userId 过滤。
- 删除优先软删除；物理删除必须有明确业务理由。

## 测试重点

- 登录注册成功和失败。
- JWT 过期、伪造、缺失。
- 用户访问他人 conversation 被拒绝。
- SSE 正常完成、模型异常、客户端中断。
- RAG 检索为空、召回多条、权限过滤。
