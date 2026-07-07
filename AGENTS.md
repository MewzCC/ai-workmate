# AI WorkMate Agent Rules

本文件是本仓库的 AI 协作入口规范。任何 AI Agent、代码助手或自动化任务在修改本项目时，必须先遵循本文件，再按需读取 `docs/rules` 与 `docs/skills`。

## 项目定位

AI WorkMate 是企业级 AI 助手平台雏形：

- 前端：Next.js 14、React 18、TypeScript、Tailwind CSS、Zustand。
- 后端：Spring Boot 3、Java 17、Spring AI、MyBatis-Plus、PostgreSQL、Redis、JWT。
- 当前核心链路：登录注册、JWT 鉴权、SSE 流式聊天、对话与消息持久化。
- 后续重点：RAG 知识库、Agent Tool Calling、多 Agent、容器化部署。

## AI 工作原则

1. 先理解现有结构，再修改代码。
2. 小步提交，禁止无关重构。
3. 前后端接口变更必须同步更新 DTO、类型、调用方和文档。
4. 安全、鉴权、数据一致性、流式体验优先于视觉炫技。
5. 任何新增能力都必须能被本地验证，无法验证时要说明原因。
6. 不提交密钥、token、真实账号、生产连接串或 `.env` 私密内容。
7. 中文业务文案使用 UTF-8 编码，避免乱码；新增文件统一使用 UTF-8。

## 必读规范

- 通用工程规范：`docs/rules/engineering-rules.md`
- 提交与分支规范：`docs/rules/git-rules.md`
- 前端规范：`docs/rules/frontend-rules.md`
- 后端规范：`docs/rules/backend-rules.md`
- AI Agent 规范：`docs/rules/agent-rules.md`

## 可用 Skills

- 前端设计：`docs/skills/frontend-design-skill.md`
- 前端动效：`docs/skills/motion-skill.md`
- 后端工程：`docs/skills/backend-engineering-skill.md`
- AI Agent 开发：`docs/skills/agent-engineering-skill.md`

## 默认验证命令

前端：

```bash
cd frontend
npm install
npm run lint
npm run build
```

后端：

```bash
cd backend
mvn test
mvn spring-boot:run
```

基础设施：

```bash
docker compose -f docker-compose.yml up -d
```

## 目录边界

- `frontend/src/app`：Next.js App Router 页面与布局。
- `frontend/src/components`：业务组件与可复用 UI。
- `frontend/src/lib`：API 客户端、浏览器侧基础工具。
- `frontend/src/store`：Zustand 状态管理。
- `frontend/src/types`：前端共享类型。
- `backend/src/main/java/com/aiworkmate/config`：配置类。
- `backend/src/main/java/com/aiworkmate/controller`：REST/SSE 入口。
- `backend/src/main/java/com/aiworkmate/service`：业务接口。
- `backend/src/main/java/com/aiworkmate/service/impl`：业务实现。
- `backend/src/main/java/com/aiworkmate/mapper`：MyBatis-Plus 数据访问。
- `backend/src/main/java/com/aiworkmate/entity`：数据库实体。
- `backend/src/main/java/com/aiworkmate/dto`：请求与响应对象。
- `backend/src/main/java/com/aiworkmate/common`：统一响应、异常处理、公共模型。
- `backend/src/main/resources/db`：数据库初始化与迁移脚本。

## 完成标准

一次任务只有在以下条件满足后才算完成：

- 代码改动贴合对应分层，没有跨层硬编码。
- 前端状态、错误、加载、空态、移动端表现已考虑。
- 后端鉴权、参数校验、异常映射、日志边界已考虑。
- AI/RAG/Agent 相关改动包含提示词、工具调用、安全边界和失败兜底。
- 已运行可行的验证命令，并在最终说明中列出结果。
