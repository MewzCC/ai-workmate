# AI WorkMate Agent Rules

## Frontend App Split

- `fronted-main` is the standalone marketing website program and runs on port `3000`.
- `fonted-oa` is the standalone OA workbench program and runs on port `3001`.
- The old `frontend` program has been split and must not be used as a shared App Router boundary.
- Do not treat the homepage and OA as two pages in the same App Router app. They must remain independently runnable apps.
- Home CTA buttons should enter `http://<host>:3001/oa`; the OA app root `/` redirects to `/oa`.

本文件是本仓库的 AI 协作入口规范。任何 AI Agent、代码助手或自动化任务在修改本项目时，必须先遵循本文件，再按需读取 `docs/rules` 与 `docs/skills`。

## 项目定位

AI WorkMate 是企业级 AI 助手与 OA 工作台平台雏形：

- 前端：Next.js 14、React 18、TypeScript、Tailwind CSS、Zustand、Ant Design、ECharts。
- 后端：Spring Boot 3、Java 17、Spring AI、MyBatis-Plus、PostgreSQL、Redis、JWT。
- 当前核心链路：营销官网、OA 工作台、登录注册、JWT 鉴权、SSE 流式聊天、对话与消息持久化。
- 当前 OA 链路：`/oa` 独立工作台、Ant Design 中后台布局、mock 菜单权限、ECharts 图表、AI 任务 plan/execute mock 接口。
- 后续重点：RAG 知识库、Agent Tool Calling、多 Agent、真实 OA 权限后台、容器化部署。

## AI 工作原则

1. 先理解现有结构，再修改代码。
2. 小步提交，禁止无关重构。
3. 前后端接口变更必须同步更新 DTO、类型、调用方和文档。
4. 安全、鉴权、数据一致性、流式体验优先于视觉炫技。
5. 任何新增能力都必须能被本地验证，无法验证时要说明原因。
6. 不提交密钥、token、真实账号、生产连接串或 `.env` 私密内容。
7. 中文业务文案使用 UTF-8 编码；新增文件统一使用 UTF-8。
8. 修改 OA 页面时，基础业务控件优先使用 Ant Design；图表使用 ECharts；不要用原生 HTML 模拟 Button/Table/Drawer/Modal/Select。

## 必读规范

- 通用工程规范：`docs/rules/engineering-rules.md`
- 提交与分支规范：`docs/rules/git-rules.md`
- 前端规范：`docs/rules/frontend-rules.md`
- 后端规范：`docs/rules/backend-rules.md`
- AI Agent 规范：`docs/rules/agent-rules.md`

## 可用 Skills

- 前端设计：`docs/skills/frontend-design-skill.md`
- 前端动效：`docs/skills/motion-skill.md`
- OA 工作台：`docs/skills/oa-workbench-skill.md`
- 后端工程：`docs/skills/backend-engineering-skill.md`
- AI Agent 开发：`docs/skills/agent-engineering-skill.md`

## 默认验证命令

前端：

```bash
cd fronted-main
npm install
npm run lint
npm run build

cd ../fonted-oa
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

> 注意：当前本机 shell 可能只有 Java 8 且没有全局 `mvn`。后端验证需要 Java 17 + Maven；如果无法运行，最终说明必须写明原因。

## 目录边界

### 前端

- `fronted-main`：营销官网独立 Next.js 程序，默认端口 `3000`。
- `fonted-oa`：OA 工作台独立 Next.js 程序，默认端口 `3001`。
- `fronted-main/src/app`：营销官网 App Router 页面与布局。
- `fonted-oa/src/app`：OA 工作台 App Router 页面与布局。
- `fronted-main/src/app/page.tsx`：营销官网与旧 experience/chat 入口；“立即尝试”应进入 `/oa`。
- `fonted-oa/src/app/oa/page.tsx`：企业 OA 工作台路由入口。
- `fronted-main/src/components`：官网、登录、聊天体验组件。
- `fonted-oa/src/components`：OA 业务组件。
- `fonted-oa/src/components/oa`：OA 工作台组件，包含布局、菜单、顶部栏、Dashboard、AI Drawer、外观 Drawer、ECharts 卡片等。
- `fronted-main/src/lib`：官网与聊天体验 API 客户端。
- `fonted-oa/src/lib`：OA API 客户端与浏览器侧工具。
- `fonted-oa/src/lib/oaApi.ts`：OA mock API 封装，包含 system health、AI plan、AI execute 及 fallback mock。
- `fonted-oa/src/mock`：前端 mock 数据与 mock 权限模型。
- `fonted-oa/src/mock/oaMenus.ts`：OA 菜单树。
- `fonted-oa/src/mock/oaDashboard.ts`：OA 首页指标、审批列表、时间线数据。
- `fonted-oa/src/mock/oaPermissions.ts`：角色、菜单过滤、按钮权限、AI 动作权限。
- `fronted-main/src/store`：官网与聊天体验 Zustand 状态管理。
- `fronted-main/src/types`：官网与聊天体验共享类型。
- `fonted-oa/src/types/oa.ts`：OA 菜单、权限、AI 计划、执行结果等类型。

### 后端

- `backend/src/main/java/com/aiworkmate/config`：配置类。
- `backend/src/main/java/com/aiworkmate/config/SecurityConfig.java`：安全配置；当前仅临时放行 `/api/system/**` 与 `/api/ai/tasks/**` mock 接口，不得放开全部接口。
- `backend/src/main/java/com/aiworkmate/controller`：REST/SSE 入口。
- `backend/src/main/java/com/aiworkmate/controller/SystemController.java`：`GET /api/system/health`。
- `backend/src/main/java/com/aiworkmate/controller/AiTaskController.java`：`POST /api/ai/tasks/plan`、`POST /api/ai/tasks/execute`。
- `backend/src/main/java/com/aiworkmate/service`：业务接口。
- `backend/src/main/java/com/aiworkmate/service/AiTaskService.java`：OA AI 任务接口。
- `backend/src/main/java/com/aiworkmate/service/impl`：业务实现。
- `backend/src/main/java/com/aiworkmate/service/impl/MockAiTaskServiceImpl.java`：确定性 mock AI 任务实现，不依赖真实 LLM。
- `backend/src/main/java/com/aiworkmate/mapper`：MyBatis-Plus 数据访问。
- `backend/src/main/java/com/aiworkmate/entity`：数据库实体。
- `backend/src/main/java/com/aiworkmate/dto`：请求与响应对象。
- `backend/src/main/java/com/aiworkmate/dto/AiTask*`：OA AI 任务 plan/execute DTO。
- `backend/src/main/java/com/aiworkmate/common`：统一响应、异常处理、公共模型。
- `backend/src/main/resources/db`：数据库初始化与迁移脚本。

## OA 工作台约束

- 首页与 OA 需要逻辑切分：默认首页运行在 `3000`，OA 工作台运行在 `3001`。
- 首页“立即尝试”必须跳转到 `http://<host>:3001/oa`；如果当前已经在 `3001`，则跳转 `/oa`。
- 本地开发脚本：
  - 首页：`cd fronted-main && npm run dev`
  - OA：`cd fonted-oa && npm run dev`
- OA 必须使用路由驱动页面切换，菜单点击进入 `/oa/<pageId>`；不得只用组件内部 state 假装跳页。
- OA 页面必须优先使用真实 Ant Design 组件。
- 业务按钮必须使用 `Button`。
- 业务表格必须使用 `Table` 或后续 ProTable。
- 抽屉必须使用 `Drawer`。
- 弹窗必须使用 `Modal`。
- 标签必须使用 `Tag`。
- 表单控件必须使用 Ant Design `Input`、`Select`、`Switch`、`Upload`、`Slider` 等。
- 右下角 AI 入口必须使用 `FloatButton`。
- 图表必须使用 ECharts，并封装在 Ant Design `Card` 中。
- 主题切换必须同步 Ant Design `ConfigProvider` token 和 CSS variables。
- OA 内置主题必须包含“首页风格”和“黑夜风格”，并且所有卡片、顶部栏、侧栏、文字、边框、ECharts 主色都要适配。
- 自定义换肤能力属于全局 OA 规则，新增主题时必须写入规则与 skill。
- 主题与 AI 小窗配置必须写入 localStorage：
  - `workmeta-oa-theme`
  - `workmeta-oa-ai-mini-enabled`
  - `workmeta-oa-wallpaper`
  - `workmeta-oa-wallpaper-opacity`
  - `workmeta-oa-wallpaper-blur`
- 左侧 OA Sider 必须固定在视口左侧；页面滚动时不得跟随内容移动。
- 左侧 Sider 滚动条默认隐藏，仅在鼠标移入侧栏时显示。

## OA 权限与 AI 约束

- 权限 mock 入口：`can(role, menuId, action)`、`filterMenusByRole(role)`、`getAllowedAiActions(role, pageId)`。
- 普通员工不得看到系统设置，不得执行审批、删除、权限修改、敏感导出等高风险 AI 操作。
- AI Drawer 必须展示当前页面、当前角色、数据范围、可执行动作和高风险确认提示。
- AI 计划生成调用 `POST /api/ai/tasks/plan`；接口失败时允许本地 fallback mock，但必须提示用户。
- AI 确认执行调用 `POST /api/ai/tasks/execute`；高风险动作必须二次确认。
- 当前阶段不要接真实数据库、真实审批接口、真实文件上传、真实导出或真实 LLM。

## 新增依赖说明

- `antd`：OA 基础 UI 组件。
- `@ant-design/icons`：OA 菜单与按钮图标。
- `echarts`：OA 图表。
- `eslint`、`eslint-config-next`：Next.js lint 验证。

新增依赖必须有明确收益，不能为单个小效果引入大型库。

## 完成标准

一次任务只有在以下条件满足后才算完成：

- 代码改动贴合对应分层，没有跨层硬编码。
- 前端状态、错误、加载、空态、移动端表现已考虑。
- 后端鉴权、参数校验、异常映射、日志边界已考虑。
- AI/RAG/Agent 相关改动包含提示词、工具调用、安全边界和失败兜底。
- OA 相关改动不破坏营销官网、旧登录、旧 ChatInterface 与 SSE 聊天链路。
- 已运行可行的验证命令，并在最终说明中列出结果。
