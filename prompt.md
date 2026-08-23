# AI WorkMate 流程审批模块实现 Prompt（按当前项目适配版）

为 AI WorkMate 企业级 OA 系统设计并实现「流程审批」模块。本模块必须构建在
当前仓库已有代码之上，不能脱离现有架构重写；实现前先读取并遵循
`AGENTS.md`、`docs/rules/*`、`docs/skills/oa-workbench-skill.md`。

## 〇、现状对齐（实现前必须确认）

以下内容已存在于当前仓库，必须复用、扩展而非重建：

前端（`fonted-oa`，React 18 + TypeScript + Vite + Ant Design 5 + ECharts + i18next + dayjs）：

- 菜单：`流程审批` 位于「业务系统」分组下（`fonted-oa/src/mock/oaMenus.ts`，
  真实菜单由 `GET /api/navigation` 返回），现有页面：
  - `approval-list` 审批列表（componentKey `APPROVAL_LIST` → `ApprovalListPage.tsx`）
  - `form-engine` 表单引擎（`FORM_ENGINE` → `FormEnginePage.tsx`，表单定义 CRUD）
  - `process-config` 流程配置（`PROCESS_CONFIG` → `ProcessConfigPage.tsx`，
    节点类型已有 `DIRECT_MANAGER / ROLE / DEPARTMENT / USER`）
  - `approval-rules` 审批规则（`APPROVAL_RULES` → `ApprovalRulesPage.tsx`，
    规则类型已有 `AMOUNT_THRESHOLD / LEAVE_TYPE / EMPLOYEE_LEVEL / LIMIT_OVERRIDE`）
  - 员工侧请假链路：`LeaveFormPage`（发起）、`MyApplicationsPage`（我的申请）、
    `ApprovalDetailPage`（路由 `/oa/approval-tasks/:id`）、`LeaveWorkflowPanel`
    （Steps 流程预览 + SLA 进度）、`TodoListPage`（我的待办）
- 复用组件：`ApprovalConfigShell`（配置页统一页头 + 满高内容布局）、
  `StatusTag`（FormEnginePage 导出）、`OaIcon`（语义图标，禁止硬编码 Symbol ID）、
  `approvalEngineApi` / `approvalApi` / `todoApi` / `leaveApi`（`src/lib`）
- 布局规范：`leave-list-workbench` 满高工作台模式（hero 页头 + 指标条 + Card 紧邻页头
  填充剩余工作区），组件注册表为 `fonted-oa/src/lib/navigationApi.ts` 的
  `componentKey` 联合类型 + `AdminLayout.tsx` 内的渲染分发

后端（Spring Boot 3 + Java 17 + MyBatis-Plus + PostgreSQL + Flyway + JWT）：

- `ApprovalConfigController`：`/api/approval-config/forms`、`/processes`、`/rules` 全量 CRUD
  （JWT 认证，读 `approval:read`、写 `approval:manage`，Service 层按认证 userId 实时解析）
- `ApprovalTaskController`：`/api/approval-tasks`（列表）、`/stats`、`/{id}/approve|reject`、
  `/{id}/timeline`；`/api/todos` 待办；`/api/leave-applications` 请假申请
- `ApprovalEngineServiceImpl` + 实体 `ApprovalForm / ApprovalProcess / ApprovalRule`；
  表结构见 `V202608221200__approval_engine_pages.sql`（`approval_form` /
  `approval_process` / `approval_rule`），`approval_form.schema_json` 是字段定义的
  JSON 载体，`approval_process` 的节点 JSON 是流程定义载体
- 页面的菜单/路由由 `V202608211700__approval_process_pages.sql` 等 Flyway 迁移管理；
  **任何新增页面/表/种子数据必须新增时间戳版本迁移脚本（`VYYYYMMDDHHMM__*.sql`），
  脚本幂等（IF NOT EXISTS / ON CONFLICT），禁止回退版本**

## 一、产品定位

AI WorkMate 是现代企业级 SaaS OA（参考飞书 / Notion / 钉钉 / Linear），
本模块延续现有 OA 工作台的统一 Design System，不要设计成传统政府 OA / 老式 ERP。

整体要求：

- 简洁、专业、高信息密度但不拥挤，现代 SaaS 风格
- 大量使用 Ant Design `Card`、`Badge`、`Tag`、`Tabs`、`Segmented`、`Drawer`、`Modal`
- 圆角适中（卡片 8~12px，小控件沿用 Ant Design 默认），细边框，柔和阴影，清晰视觉层级
- 支持 Light / Dark Mode（Dark 即现有 `home-night` 主题，通过
  `workmeta-oa-theme` 切换，必须同步 `ConfigProvider` token 与 CSS variables）
- Desktop First，同时兼容 Tablet（≤720px 侧栏自动收起）
- 不要使用过度夸张的渐变、不要大面积彩色背景、不在业务页堆砌图标；
  壁纸质感只允许走现有全局壁纸系统（`workmeta-oa-wallpaper-*`），业务组件不得自带廉价玻璃拟态

## 二、模块信息架构

流程审批一级菜单（位于「业务系统」分组下）目标结构：

```
流程审批
├── 审批中心        （升级现有 approval-list / APPROVAL_LIST）
├── 发起审批        （新增模板中心；现有请假模板对应 LEAVE_FORM）
└── 流程管理
    ├── 流程列表    （现有 process-config / PROCESS_CONFIG，改名增强）
    ├── 表单管理    （现有 form-engine / FORM_ENGINE，改名增强）
    └── 审批规则    （现有 approval-rules / APPROVAL_RULES，增强为条件 Builder）
```

角色边界（由后端 rbac + `GET /api/navigation` 实时解析，不可写死在 mock 里）：

- 普通员工（`employee`）：只见「审批中心」「发起审批」「我的申请」「我的待办」，
  不可见系统设置与流程管理
- `process_admin` / `system_admin` / `super_admin`：可见「流程管理」全套；
  流程管理的写操作必须校验 `approval:manage`，读操作 `approval:read`
- 「表单引擎」不得作为普通用户菜单出现

## 三、审批中心

升级现有 `ApprovalListPage`（可保留其路由 `/oa/approval-list`），结构：

顶部 Hero（沿用 `leave-list-hero` 模式）：

- 标题：审批中心；右侧展示当前数据范围 Badge

指标条（沿用 `leave-metric-strip`，5 格对齐现有 `/stats` 口径）：

- 待我审批 / 我发起的 / 已处理 / 抄送我的 / 全部单据
- 指标数据来自 `GET /api/approval-tasks/stats`（前端已有 `ApprovalStatusCount` 类型）；
  「待我审批/我发起的/抄送我的」若当前 stats 没有对应维度，属于后端扩展项，
  扩展 DTO 与接口时同步更新前端类型，禁止前端用假数据补齐

筛选区（Card 内 toolbar）：

- 状态用 `Tabs` 或 `Segmented`：全部 / 待审批 / 已通过 / 已驳回 / 已撤回
  （对应 `PENDING / APPROVED / REJECTED / WITHDRAWN`，草稿 `DRAFT` 单列）
- 搜索（关键字）、时间筛选（`RangePicker`，注意现有 dayjs 时间边界处理）、
  审批类型筛选、状态筛选

列表（Ant Design `Table`，列文案全部走 i18n）：

- 审批类型图标（OaIcon）、审批标题 + 编号、发起人（Avatar）、部门、
  金额/核心信息、当前审批节点、提交时间、状态 `Tag`
- 点击行进入审批详情（Drawer 快速预览 + 「查看完整详情」跳转
  `/oa/approval-tasks/:id` 独立路由，路由页必须保留）
- 空态用 `Empty`，加载用 Table loading，接口失败用 `formatOaApiError` 提示真实错误

## 四、审批详情

复用并增强现有 `/oa/approval-tasks/:id`（`ApprovalDetailPage`），并支持列表点击
时以 `Drawer` 快速预览同一套内容。

顶部：

- 返回按钮（按来源返回待办/审批中心/我的申请）、审批名称、状态 Tag、
  申请人（Avatar + 姓名 + 部门）、发起时间

中间：

- 审批信息：`Descriptions` 结构化展示业务数据（对齐现有请假示例）
- 审批流程：现有 `LeaveWorkflowPanel`（Steps + SLA 进度条）继续作为流程预览范式；
  新增通用流程时保留「当前节点高亮、已完成节点打勾、超时标红」语义
- 审批记录：`Timeline` 展示 审批人 / 操作 / 时间 / 审批意见，状态颜色映射
  （通过 → 绿、驳回 → 红、撤回 → 橙、其余 → 蓝）

底部操作：

- 待审批且 `canApprove`：底部决策栏（同意 / 驳回，弹 `Modal` 填写意见，
  驳回必填意见，沿用现有 409 版本冲突处理）；转交 / 加签 / 评论为可选扩展，
  需要后端新增接口，未接入前不允许用前端假成功
- 非受理人 / 已结束状态：只读，展示对应 Alert，不出操作按钮

## 五、发起审批

新增「发起审批」模板中心页面（新页面需走组件注册表 + 迁移脚本 + 菜单接入）：

- 顶部标题 + 「搜索审批事项」搜索框
- 常用模板横排：请假、出差、报销、加班、采购、付款
- 分类区域：人事 / 财务 / 行政 / 采购 / 其他
- 模板 Card：OaIcon 图标、名称、简短描述、使用次数
- 点击模板进入对应审批表单页；「请假」直接复用现有 `LEAVE_FORM` 页面，
  其余模板需先有对应的表单定义（`approval_form`）与流程定义（`approval_process`），
  由 Flyway 种子数据提供，点击时按 `form_key` 路由

## 六、审批表单

两栏布局（对齐现有 `LeaveFormPage` 的左表单 + 右 `LeaveWorkflowPanel` 流程预览）：

左侧表单：按 `schema_json` 渲染字段（类型：单行/多行文本、数字、金额、
日期、日期范围、时间、单选、多选、下拉、人员、部门、文件/图片上传、明细表、
分割线），带必填校验、金额千分位、日期范围与时长自动核算

右侧：审批流程预览（申请人 → 直属主管 → 部门负责人，当前节点高亮）

底部：取消 / 保存草稿 / 提交审批；“提交审批”前弹确认 `Modal`
（展示时长与流程摘要，沿用现有 `submitConfirm*` 交互）。提交走真实后端
（现有 `/api/leave-applications` 或新增通用提交接口），失败显示真实错误。

## 七、流程管理

升级现有 `ProcessConfigPage`（`/oa/process-config`，复用 `ApprovalConfigShell`）：

- 顶部：搜索、分类筛选、状态筛选（已启用 / 草稿 / 已停用）、「新建流程」按钮
- 表格：流程名称、流程 Key、分类、关联表单、审批节点数、状态（`StatusTag`）、
  创建人、更新时间、操作（编辑 / 复制 / 启用停用 / 删除，`Popconfirm` 确认）
- 新建/编辑进入流程设计器
- 数据走 `/api/approval-config/processes` CRUD；状态枚举
  `ENABLED / DISABLED`（草稿态可沿用现有字段或扩展 `DRAFT`，需后端同步）

## 八、流程设计器

在现有流程配置的节点编辑能力（`DIRECT_MANAGER / ROLE / DEPARTMENT / USER`）
基础上升级为节点式流程设计器。**技术选型：用 React + Ant Design 自绘轻量画布
（绝对定位节点 + SVG/`div` 连线），禁止引入 Vue Flow 或大型图编辑库**。

- 画布节点类型：开始节点、审批节点、条件节点、抄送节点、延迟节点、结束节点
- 左侧工具栏：审批人、条件、抄送、延迟；中间自绘无限画布（拖移、缩放、连线、
  删线）；右侧节点属性面板
- 审批节点属性：
  - 审批人类型：指定成员 / 指定部门负责人 / 直属主管 / 发起人自己 / 连续多级主管
  - 审批方式：会签 / 或签 / 依次审批
  - 超时设置：是否启用、超时时间、自动操作（提醒 / 转交 / 自动通过）
- 底部：保存草稿 / 发布流程；发布状态切换调用真实接口，流程发布后发起页可选
- 节点/连线 JSON 必须与 `approval_process` 存储格式兼容，保存即落库

## 九、表单管理

升级现有 `FormEnginePage`（`/oa/form-engine`）：

- 表格：表单名称、表单 Key、关联流程、字段数量（从 `schema_json` 解析）、
  使用状态、版本、更新时间、操作
- 新建/编辑表单进入 Form Builder；表单定义保存至
  `/api/approval-config/forms`，`schema_json` 结构向后兼容

## 十、Form Builder

新增三栏式表单设计器页（注册表 + 迁移 + 菜单接入）：

- 左侧基础组件库：单行文本、多行文本、数字、金额、日期、日期范围、时间、
  单选、多选、下拉选择、人员选择、部门选择、文件上传、图片上传、明细表、分割线
- 中间实时表单画布：支持拖拽、排序、删除、复制、设置必填、字段宽度、分组
- 右侧字段属性：字段名称、字段 Key、是否必填、默认值、Placeholder、
  校验规则、显示条件
- 顶部：撤销、重做、预览、保存、发布（预览与发布后表单立即可用于发起审批）
- 拖拽实现优先使用轻量方案（原生 HTML5 DnD 封装或受控列表），不引入重型 DnD 库

## 十一、审批规则

升级现有 `ApprovalRulesPage` 为条件规则 Builder：

示例：当 报销金额 > 5000 并且 部门 = 技术部 那么 部门负责人 → 财务负责人 → 总经理

- 支持：AND / OR、条件嵌套、条件排序、审批人选择、审批方式选择、
  规则优先级（现有 `priority` 字段）
- UI 必须是可视化条件构建器，不要使用 SQL 或代码配置界面
- 规则 JSON 与 `approval_rule` 存储兼容，CRUD 走 `/api/approval-config/rules`

## 十二、视觉规范（使用现有设计令牌，页面内禁止硬编码色值）

统一通过现有 CSS 变量与 Ant Design token 取色，主题切换由
`workmeta-oa-theme`（6 套主题 + `home-night` 暗色）驱动，挂载到
`body` 的 Portal 弹层（Drawer/Modal/下拉）必须同步主题。

Light 默认（enterprise-blue）：

- 背景 `var(--oa-surface)` #f4f7fb；卡片 `var(--oa-card)` #ffffff；
  顶栏 rgba(255,255,255,0.86)
- 主色 `var(--oa-primary)` #1677ff；侧栏 #0f1f3d、侧栏文字 #d7e7ff
- 文字 `var(--oa-text)` #111827；次级 `var(--oa-muted)` #64748b；
  边框 `var(--oa-border)` rgba(15,23,42,0.08)，边框非常轻

Dark（home-night 主题）：

- 背景 #0b0c12；卡片 #14151f；文字 #f8fafc；次级 #a6adbb；
  边框 rgba(148,163,184,0.18)；主色 #8b5cf6

状态色（Ant Design Tag 语义色，深浅主题自动适配）：

- 待审批：warning 橙（#fa8c16）
- 审批中：processing 蓝（#1677ff）
- 已通过：success 绿（#52c41a）
- 已驳回：error 红（#ff4d4f）
- 草稿：default 灰（#8c8c8c）

文字层级：Primary（`--oa-text`）→ Secondary（`--oa-muted`）→
Tertiary（opacity 更低），标题用 Ant Design `Typography`。

## 十三、交互要求

所有页面必须是真实可交互的实现，不是静态截图：

- Tab/Segmented 切换、搜索、筛选、Drawer、Modal、表单校验、节点选择、
  节点属性配置、表单字段拖拽、条件规则编辑、Dark Mode、响应式布局
- 后端已存在的接口必须真实调用；**接口失败时展示真实错误或能力不可用，
  禁止 mock 成功 / fallback 伪造**（见 AGENTS.md 硬约束）
- 新交互（转交、加签、抄送统计等）若后端未实现，UI 可先禁用或标注「能力不可用」
- 所有可见文案（按钮、标题、表头、placeholder、tooltip、message、空态）
  必须走 `t('...')`，同步补充 `zh-CN` 与 `en-US` 双语资源（`approval` 命名空间），
  缺一不可；前端请求携带 `Accept-Language`

## 十四、技术要求（严格遵循现有技术栈）

前端（`fonted-oa`）：

- React 18/19 + TypeScript + Vite + Ant Design 5（业务控件必须用 AntD：
  Button/Table/Drawer/Modal/Tag/Form/Select/DatePicker/Timeline/Steps）+ ECharts + dayjs
- 状态按现有模式（组件内 state + `src/lib` API 客户端），需要跨页共享时用 Zustand
- 图标统一 `OaIcon` 语义名（Iconfont），业务页禁止硬编码 Symbol ID
- **禁止引入 Vue 3 / Element Plus / Pinia / Lucide / Vue Flow 及任何大型不必要依赖**
- 新页面接入三件套：`navigationApi.ts` componentKey 联合类型 + `AdminLayout.tsx`
  渲染分发 + 菜单（mock `oaMenus.ts` 演示 + 后端 `rbac_route` 迁移脚本种子数据）

后端（Spring Boot 3 / Java 17 / MyBatis-Plus / PostgreSQL / Flyway / JWT）：

- 新增接口沿用 `/api/approval-config/*` 与 `/api/approval-tasks/*` 风格，
  `Result<T>`/`PageResponse` 统一响应，`@Valid` 校验
- 错误文案走 `MessageSource` + `Accept-Language`，禁止 controller/service 拼中文
- 权限：读 `approval:read`、写 `approval:manage`，按认证 userId 实时解析
- 结构/种子变更一律新增时间戳版本 Flyway 迁移（幂等写法），禁止改已执行迁移

## 十五、最终要求（验收清单与验证）

完整实现以下页面（模块化、统一 Design System）：

1. 审批中心（升级 `ApprovalListPage`）
2. 审批详情 Drawer + 详情路由页（现有 `ApprovalDetailPage` 增强）
3. 发起审批模板中心（新增）
4. 审批表单（含流程预览，复用 `LeaveWorkflowPanel` 范式）
5. 流程管理 = 流程列表（升级 `ProcessConfigPage`）
6. 流程设计器（自绘节点画布，新增）
7. 表单管理（升级 `FormEnginePage`）
8. 表单设计器 Form Builder（新增）
9. 审批规则 Builder（升级 `ApprovalRulesPage`）

重点保证：普通员工使用简单、管理员配置强大、流程设计专业、视觉风格现代，
不出现传统 OA 的廉价感。

验证命令（本地必须运行并汇报结果）：

```bash
cd fonted-oa && npm run lint && npm run build   # 前端
cd backend && mvn test                          # 后端（无 Java 17/Maven 则说明原因）
```

完成标准：前端状态/错误/加载/空态齐备；后端鉴权/校验/异常映射齐备；
i18n 双语同步；Flyway 迁移在空库首跑 + 重复执行均通过；
不破坏营销官网、旧登录、SSE 聊天与现有请假审批链路。