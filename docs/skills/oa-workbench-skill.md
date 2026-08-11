# OA Workbench Skill

## Frontend App Split

- The marketing website app lives in `fronted-main` and runs on port `3000`.
- The OA workbench app lives in `fonted-oa` and runs on port `3001`.
- OA implementation owns its source inside `fonted-oa/src`; do not depend on the removed `frontend` app.
- Do not implement OA as a nested page inside the homepage app.
- The OA app root `/` redirects to `/oa`; OA menu pages use `/oa/<pageId>`.

## 触发场景

当任务涉及以下内容时使用本 skill：

- `/oa` 企业 OA 工作台。
- Ant Design 中后台布局、菜单、表格、抽屉、弹窗、表单、FloatButton。
- OA 菜单、角色权限、按钮权限、AI 动作权限。
- ECharts 图表。
- OA AI 操作面板、计划生成、确认执行、审计时间线。
- `GET /api/system/health`、`POST /api/ai/tasks/plan` 以及 Phase 2 的 taskId 路径确认、执行、查询和 SSE 接口。

## 当前实现范围

当前 OA 是基础联调版本：

- 前端页面：`fonted-oa/src/app/oa/page.tsx`。
- 前端组件：`fonted-oa/src/components/oa`。
- 前端权限与演示数据：`fonted-oa/src/mock`。
- 前端类型：`fonted-oa/src/types/oa.ts`。
- 前端 API：`fonted-oa/src/lib/oaApi.ts`。
- 后端控制器：`SystemController`、`AiTaskController`。
- 后端服务：`AiTaskService` 及其真实实现。

AI plan/execute 不再允许 mock 成功；未接入真实数据库、审批系统、文件上传、导出或 LLM 时，必须明确失败并提示当前能力不可用。

实施 Phase 2 Tool Registry、持久任务引擎、确认凭证或写工具时，必须同时读取 `docs/roadmap/phase-2-agent-security-boundary.md`。Phase 2A 只允许受控只读；Phase 2B 一个任务最多一个写步骤，永久禁止能力不得因前端确认或高权限角色解除。

## 首页与 OA 端口切分

- 首页默认运行在 `3000`。
- OA 默认运行在 `3001`。
- 首页“立即尝试”跳转到 `http://<host>:3001/oa`。
- 如果当前端口已经是 `3001`，则跳转 `/oa`。
- 本地开发：
  - 首页：`npm run dev:home`
  - OA：`npm run dev:oa`

## OA 路由规范

- `/oa` 显示企业驾驶舱。
- `/oa/<pageId>` 显示对应菜单页面或占位页面。
- 左侧菜单点击必须使用路由跳转，不得只修改组件内部 state。
- URL、菜单选中态、顶部标题、AI 当前页面上下文必须保持一致。
- 角色切换后，如果当前页面无权限访问，应跳回 `/oa/dashboard`。
- 顶部页面标签记录已访问的有权限页面，支持快速切换、关闭当前、关闭其他和关闭全部；固定页不可关闭。
- 标签切换必须调用路由，不得用组件内部 state 模拟页面；`workmeta-oa-open-tabs` 仅保存页面 ID，恢复时必须按最新服务端导航过滤。

## 前端实现规范

### Ant Design 强制使用

OA 工作台业务 UI 必须使用真实 Ant Design 组件：

- 按钮：`Button`
- 表格：`Table`，后续复杂表格可迁移到 ProTable
- 抽屉：`Drawer`
- 弹窗：`Modal`
- 标签：`Tag`
- 表单：`Form`
- 输入：`Input`、`Input.TextArea`
- 选择：`Select`
- 开关：`Switch`
- 上传：`Upload`
- 滑块：`Slider`
- 悬浮按钮：`FloatButton`
- 步骤：`Steps`
- 时间线：`Timeline`
- 描述：`Descriptions`
- 统计：`Statistic`
- 卡片：`Card`

禁止用原生 `button`、`table`、自定义 `div drawer`、自定义 `div modal` 模拟 Ant Design。

### 图标工作流

- OA 组件通过 `fonted-oa/src/components/OaIcon.tsx` 使用语义图标，避免业务代码依赖 Iconfont 的具体 Symbol ID。
- `iconFontMap` 保存已经进入 Iconfont 的图标；`fallbackIconMap` 保存等待上传期间的开源占位图标。
- 新增正式图标时直接在 Iconfont 项目中选择或上传；仓库不生成、不保存待上传 SVG 或上传压缩包。
- Iconfont 暂无对应图标时，可使用已安装且许可证明确的开源图标作为临时 fallback。
- 用户上传 Iconfont 并提供新下载包前，必须用可见占位图标完成页面构建；禁止引用不存在的 Symbol。
- 更新 Symbol 包后，同步 `IconFont.tsx` 类型、`OaIcon.tsx` 映射并运行 OA lint/build。
- 下载的 Symbol 包使用 `scripts/import-iconfont-symbol.mjs` 导入；单色业务图标统一使用 `currentColor`，禁止固定深色填充导致深色主题不可见。

### 布局规范

- `AdminLayout` 是 OA 工作台总布局。
- 左侧使用 `Layout.Sider + Menu`。
- 顶部使用 `Layout.Header`。
- 内容区使用 `Layout.Content`。
- 左侧 Sider 必须固定在视口左侧，页面滚动时不跟随内容移动。
- 左侧 Sider 默认隐藏滚动条，鼠标移入侧栏时显示滚动条。
- 右侧内容区必须根据 Sider 展开/收起状态保留左边距。
- 移动端必须避免 Sider 遮挡主内容。
- 顶部标题与页面标签组成同一个 sticky 导航区；标签过多时使用 Ant Design Tabs 的横向滚动/更多菜单，不得挤压页面操作区。

### 主题规范

- 主题入口在 `AppearanceDrawer`。
- 主题必须同步 Ant Design `ConfigProvider` token。
- 主题必须同步 CSS variables：
  - `--oa-primary`
  - `--oa-sidebar`
  - `--oa-sider-text`
  - `--oa-surface`
- 主题持久化 key：`workmeta-oa-theme`。
- AI 小窗持久化 key：`workmeta-oa-ai-mini-enabled`。
- 壁纸配置持久化 key：
  - `workmeta-oa-wallpaper-opacity`
  - `workmeta-oa-wallpaper-blur`
- 自定义壁纸必须位于固定底层，不得直接设置在布局容器上。
- 壁纸启用后，Sider、Header、Card、Table 和 Ant Design Drawer 必须使用透明背景与 `backdrop-filter`，不能出现不透明区域截断壁纸。
- 本地上传图片必须裁剪压缩后通过受 JWT 保护的用户资料接口写入 MinIO，存储失败要明确提示，禁止静默丢失。
- 外观 Drawer 必须使用 Ant Design `Image` 展示当前壁纸缩略图并支持大图预览，同时提供处理态、空态和清除态。
- 壁纸上传采用 `react-easy-crop`，并使用 Ant Design `Modal`、`Segmented`、`Slider` 和 `Button` 组成裁剪工作区。
- 裁剪必须支持图片拖动定位、缩放、旋转、16:9/4:3/1:1 比例切换、重置与二次裁剪；确认前不得修改当前壁纸。
- 裁剪会话结束后释放原图临时 URL，将压缩后的 WebP 裁剪结果上传到 MinIO；`workmeta-oa-wallpaper` 仅用于旧版本数据的一次性迁移。
- 内置主题必须包含：
  - 企业蓝
  - 深青绿
  - 高级紫
  - 石墨灰
  - 暖棕橙
  - 首页风格
  - 黑夜风格
- “首页风格”需要承接营销首页的浅色、暖色、克制质感。
- “黑夜风格”需要承接营销首页夜间模式，所有文字、卡片、顶部栏、边框和 ECharts 主色必须适配。
- 自定义换肤是全局规则；新增主题必须同步更新本 skill、`AGENTS.md` 和 `docs/rules/frontend-rules.md`。

### 图表规范

- 图表使用 ECharts。
- 图表必须封装在 Ant Design `Card` 中。
- 图表组件应处理 `resize` 和 `dispose`。
- 主题切换时，ECharts 主色应同步变化。

## 权限规范

OA 菜单和页面权限必须使用服务端动态 RBAC，不得再使用本地 mock 角色决定可访问页面：

- 角色存储于 `rbac_role`，允许管理员新增任意业务角色。
- 权限存储于 `rbac_permission`，角色与权限通过 `rbac_role_permission` 关联。
- 动态菜单和路由存储于 `rbac_route`，页面节点自动绑定 `route:<routeKey>` 权限。
- 前端通过 `GET /api/navigation` 获取当前登录用户可访问的菜单树。
- 用户角色与权限在每次 JWT 请求时从数据库重新解析，禁止信任 token 中的历史角色。
- `/api/admin/access-control/**` 必须要求 `access:manage` 权限。
- 页面组件使用前端安全注册表，只允许 `DASHBOARD`、`AI_WORKSPACE`、`ACCESS_CONTROL`，不得从数据库执行任意组件名或代码。
- 无权页面不能出现在菜单中，直接输入无权 URL 时也必须重定向到首个可访问页面。
- `AdminLayout` 必须挂载在 `/oa/layout.tsx`，路由切换只更新内容区，不得重新挂载侧栏、主题和全局抽屉。
- 动态菜单首次进入时全部目录折叠；允许同时展开多个目录；选择页面时保留已展开目录并补充其祖先目录；刷新叶子页面时恢复其目录链。
- 侧栏收缩后必须通过 Ant Design 弹出子菜单继续选择任意叶子页面，禁止用空 `openKeys` 锁死交互。
- Menu、Dropdown、Select、Popover 等挂载到 `body` 的 Portal 弹层必须通过 `ConfigProvider` token 与全局 CSS variables 同步主题；壁纸模式下使用与侧栏一致的透明模糊材质。
- 权限、审计和业务数据列表必须使用 Ant Design `Table`；壁纸模式下表头和表体需要保留独立的半透明底、边框与悬停层级，禁止因全局透明规则降低文字对比度。
- `SUPER_ADMIN` 始终拥有全部权限，并且系统必须阻止最后一名有效超级管理员被降级。

普通员工不得执行：

- 批量审批。
- 删除数据。
- 修改权限。
- 敏感导出。
- 未授权审批类 AI 动作。

## AI 操作规范

AI 页面操作主入口必须是 Ant Design `FloatButton`。

通用 AI Chat Workspace 是例外：它必须位于左侧菜单 `/oa/ai-workspace` 并占用独立页面，不使用 FloatButton、Drawer 或 AI MiniPanel；原页面操作助手继续遵循 FloatButton + plan/execute 规范。

AI Drawer 必须包含：

- 当前页面。
- 当前角色。
- 当前数据范围。
- 可执行动作。
- 高风险动作提示。
- 快捷指令。
- 消息区。
- 输入框。
- 执行计划。
- 二次确认。
- 执行结果。

AI 计划生成：

- 调用 `POST /api/ai/tasks/plan`。
- 请求字段：`input`、`pageId`；禁止发送可信 `role`、`tenantId` 或权限声明。
- 请求必须携带 JWT，角色与权限由服务端认证上下文重建。
- 成功后使用 `Steps` 渲染后端返回步骤。
- 失败时不得 fallback mock；必须展示后端错误或能力不可用提示。

AI 确认执行：

- Phase 2 调用 `POST /api/ai/tasks/{taskId}/execute`；L1/L2 在用户确认后先调用 `POST /api/ai/tasks/{taskId}/confirmation-token`。
- 高风险操作必须使用 `Modal.confirm` 二次确认。
- 执行完成后更新首页 Timeline 或审计记录。
- 普通员工敏感操作必须在前端拦截，不调用 execute。
- 前端拦截和 `Modal.confirm` 只用于降低误操作，不是安全边界；服务端仍需在凭证签发、execute 和 Worker 执行前实时鉴权。
- confirmationToken 只允许保存在当前组件内存，禁止写入 localStorage、URL、日志或 SSE。

## 后端实现规范

后端接口：

- `GET /api/system/health`
- `GET /api/navigation`
- `GET /api/admin/access-control`
- `POST /api/admin/access-control/roles`
- `PUT /api/admin/access-control/users/{userId}/role`
- `PUT /api/admin/access-control/roles/{roleCode}/permissions`
- `PUT /api/admin/access-control/routes/{routeKey}`
- `POST /api/ai/tasks/plan`
- `POST /api/ai/tasks/{taskId}/confirmation-token`
- `POST /api/ai/tasks/{taskId}/execute`
- `GET /api/ai/tasks`
- `GET /api/ai/tasks/{taskId}`
- `POST /api/ai/tasks/{taskId}/cancel`
- `GET /api/ai/tasks/{taskId}/events`

实现边界：

- Controller 只做请求校验、服务调用、`Result.ok(data)` 包装。
- Service 接口表达能力。
- 禁止返回确定性 mock 数据或伪造执行成功。
- 没有 `AI_API_KEY` 或真实业务依赖不可用时，必须返回可解释失败。
- 高风险动作必须完成二次确认、权限校验和审计记录后才能执行。
- `SecurityConfig` 仅公开 `/api/system/**`；`/api/ai/tasks/**` 必须认证，不要放开全部接口。

## 验证清单

每次修改 OA 工作台后至少检查：

- `/oa` 能打开，不白屏。
- 左侧菜单固定，页面滚动时不移动。
- 左侧滚动条默认隐藏，hover 显示。
- 角色切换影响菜单。
- 普通员工看不到系统设置。
- ECharts 图表可渲染。
- AI FloatButton 可打开 Drawer。
- AI 任务可生成计划。
- 敏感操作在普通员工角色下被拦截。
- `npm run lint` 可执行。
- `npm run build` 通过。
