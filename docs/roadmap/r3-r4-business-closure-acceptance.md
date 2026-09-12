# R3–R4 全界面真实业务闭环验收记录

## 1. 验收结论

- 状态：`PASS`
- 日期：2026-09-12
- 分支：`feature/zcc`
- 数据库最新迁移：`V202609121000__sandbox_replay_workflow.sql`
- 页面范围：开发库中 41 个启用 `PAGE` 路由

R3 已将动态路由、后端组件白名单和前端组件注册表收口；启用页面不能再配置为 `WORKBENCH_MODULE`，也不能用非驾驶舱路由回退到 `DASHBOARD`。R4 已将页面操作权限切换为 `/api/auth/me` 的实时权限快照，驾驶舱接入真实聚合、审批导航、受控导出和用户指标偏好，并移除正式 OA 页面中的业务 Mock、假成功与可点击占位操作。

“PASS”表示代码、接口、权限和自动化门禁已闭环，不表示每个部署环境的 AI、Embedding、OCR、Redis 或 MinIO 一定在线。依赖不可用时页面必须展示真实错误或不可用状态，禁止伪造成功。

## 2. R3–R4 提交线

| 顺序 | 提交 | 主题 |
| ---: | --- | --- |
| R3-1 | `3aaeb084` | `refactor(navigation): 收口业务页面组件注册表` |
| R3-2 | `3e2494c6` | `fix(navigation): 禁止正式路由回退占位组件` |
| R3-3 | `acf765c5` | `test(navigation): 完成全菜单运行态一致性门禁` |
| R4-1 | `effbb6d5` | `feat(permission): 接入页面操作实时权限` |
| R4-2 | `1026f7c7` | `feat(dashboard): 接入企业驾驶舱真实数据` |
| R4-3 | `64ba26d8` | `feat(dashboard): 完成驾驶舱审批入口闭环` |
| R4-4 | `37e7d041` | `feat(dashboard): 完成看板数据受控导出` |
| R4-5 | `43adf8e9` | `feat(dashboard): 完成工作台指标偏好配置` |
| R4-6 | `c3a76b22` | `fix(oa): 清理生产页面占位操作与假成功反馈` |
| R4-7 | `d2c8a3e9` | `test(oa): 完成全页面真实业务回归门禁` |
| R4-8 | 本文档所在提交 | `docs(roadmap): 更新真实业务闭环验收记录` |

## 3. 全页面验收表

说明：所有页面先要求对应的 `route:<routeKey>`。表中“动作权限”是额外业务权限；未单列时仍由认证用户、租户、资源所有权、数据范围和领域状态机共同校验。`—` 表示页面为只读、导航入口，或不提供该类写操作，不代表绕过鉴权。

| 页面 / routeKey | 组件 | 主要读取接口 | 主要写入接口 | 动作权限与验收 |
| --- | --- | --- | --- | --- |
| 企业驾驶舱 `dashboard` | `DASHBOARD` | `GET /api/dashboard/overview`、`GET /api/settings/dashboard` | `POST /api/dashboard/export`、`PUT /api/settings/dashboard` | `dashboard:read`；导出另需 `data:export`；本人/数据范围聚合，空态与失败态通过 |
| AI 工作空间 `ai-workspace` | `AI_WORKSPACE` | `/api/conversations/**`、`/api/knowledge/**` | 会话、消息、附件与流式聊天接口 | 资源按当前用户隔离；AI/Embedding/OCR 不可用时失败关闭 |
| AI 任务中心 `ai-tasks` | `AI_TASK_CENTER` | `GET /api/ai/tasks`、`GET /api/ai/tasks/{taskId}`、SSE events | `POST /api/ai/tasks/{taskId}/cancel` | 仅本人任务；Tool Gateway、租约、确认和预算边界不变 |
| 我的待办 `todo` | `TODO_LIST` | `GET /api/todos`、`GET /api/todos/{id}` | —（处理跳转审批详情） | 待办按当前处理人与租户过滤；空态通过 |
| 消息中心 `messages` | `MESSAGE_CENTER` | `GET /api/notifications`、`GET /unread-count` | `PATCH /api/notifications/{id}/read`、`PATCH /read-all` | 仅本人消息 |
| 请假申请 `leave-application` | `LEAVE_FORM` | `GET /api/leave-applications/approval-context`、候选审批人 | `POST/PUT /api/leave-applications/**`、提交/撤回/催办 | 申请人、租户、状态与版本校验 |
| 我的申请 `my-applications` | `MY_APPLICATIONS` | `GET /api/leave-applications/mine` | 提交、撤回、催办 | 仅本人申请，乐观锁冲突关闭 |
| 审批中心 `approval-list` | `APPROVAL_LIST` | `GET /api/approval-tasks/all`、统计 | —（查看进入详情） | 管理读取受审批权限与数据范围约束 |
| 发起审批 `approval-start` | `APPROVAL_START` | `GET /api/approval-config/forms` | —（选择模板后导航） | 不在列表页伪造提交 |
| 申请 `approval-form` | `APPROVAL_FORM` | 表单定义、申请详情 | `/api/approval-applications` 草稿、更新、提交、取消、撤回、重新打开 | 申请人、租户、状态和版本校验；提交冻结快照 |
| 表单管理 `form-engine` | `FORM_ENGINE` | `GET /api/approval-config/forms` | 表单新增、更新、删除 | `approval:manage`；真实 Ant Design 设计弹窗 |
| 流程列表 `process-config` | `PROCESS_CONFIG` | `GET /api/approval-config/processes` | 流程新增、更新、删除 | `approval:manage`；流程节点真实持久化 |
| 审批规则 `approval-rules` | `APPROVAL_RULES` | `GET /api/approval-config/rules` | 规则新增、更新、删除 | `approval:manage`；规则服务端校验 |
| 组织架构 `org-tree` | `ORG_TREE` | `GET /api/hr/organization` | — | `hr:read`；专属组件、失败态通过 |
| 员工档案 `employee-files` | `EMPLOYEE_FILES` | `GET /api/hr/employees/{id}`、档案附件列表 | 员工附件上传 | `hr:read`，附件访问再校验员工、租户与 MinIO 元数据 |
| 入转调离 `employee-change` | `EMPLOYEE_CHANGE` | `GET /api/hr/employee-changes`、详情 | 创建、审批、驳回、撤回 | 写入需 `hr:manage`；状态、租户与版本校验 |
| 资产台账 `asset-ledger` | `ASSET_LEDGER` | `GET /api/admin-assets/assets`、详情与历史 | 资产维护、领用、归还、调拨、维修、盘点、报废 | 读取 `assets:read`，写入 `asset:write`；报废终态关闭 |
| 会议室 `meeting-room` | `MEETING_ROOM` | 会议室、本人/管理员预约列表 | 创建/维护会议室、预约、取消 | `meeting:book`、`meeting:read:self` 或管理权限；服务端冲突检测 |
| 访客预约 `visitor-booking` | `VISITOR_BOOKING` | 本人、待审批和详情 | 创建、撤回、审批、签到、到访、离场、失约 | `visitor:*` 与 `approval:act`；全生命周期审计 |
| 印章用印 `seal-usage` | `SEAL_USAGE` | 本人、待审批、详情与留档 | 创建、撤回、审批、实际用印、归还、文件上传 | `seal:*` 与 `approval:act`；对象路径不回显 |
| 费用报销 `expense` | `EXPENSE` | `GET /api/approval-applications/mine?formKey=expense-application`、申请详情 | `/api/approval-applications/**` 草稿、更新、提交、撤回、重开、催办 | 通用审批的申请人/租户/状态/版本校验；真实审批关联 |
| 预算中心 `budget` | `BUDGET` | `GET /api/budgets`、详情与选项 | 创建、更新、状态调整 | `budget:manage`；租户与版本校验 |
| 合同管理 `contracts` | `CONTRACT` | `GET /api/contracts`、详情与选项 | 创建、更新、状态调整 | `contract:manage`；供应商和租户关联校验 |
| 供应商 `suppliers` | `SUPPLIER` | `GET /api/suppliers`、详情 | 创建、更新、状态调整 | `supplier:manage`；租户和版本校验 |
| 接口联调中心 `api-center` | `API_CENTER` | `GET /api/integration/endpoints`、详情与选项 | 创建、更新、启停、受控执行 | 管理与执行权限分离；仅服务端白名单上游，禁止任意 URL |
| 页面操作配置 `page-actions` | `PAGE_ACTIONS` | `GET /api/admin/page-actions` | `PUT /api/admin/page-actions/{pageId}/{toolCode}` | `route:page-actions` + 管理校验；只能收紧代码能力 |
| 运行日志 `runtime-logs` | `RUNTIME_LOGS` | `GET /api/admin/runtime-logs`、详情 | — | `runtime-log:read`；安全摘要，不返回异常堆栈或密钥 |
| 沙箱回放 `sandbox-replay` | `SANDBOX_REPLAY` | 回放列表、基线、详情 | `POST /api/integration/replays` | 读取 `integration:replay:read`，执行另需 `integration:replay:execute`；不调用任意外域 |
| 打卡 `attendance-clock` | `ATTENDANCE_CLOCK` | 今日状态、记录 | `POST /api/attendance/clock` | 本人身份与租户校验 |
| 异常考勤 `attendance-exception` | `ATTENDANCE_EXCEPTION` | 异常记录 | — | 数据范围过滤 |
| 补卡申请 `attendance-reissue` | `ATTENDANCE_REISSUE` | 本人补卡记录 | 创建、撤回/审批相关接口 | 申请人、审批人、状态校验 |
| 考勤统计 `attendance-statistics` | `ATTENDANCE_STATISTICS` | 统计接口 | — | 本人或授权团队数据范围 |
| 考勤设置 `attendance-settings` | `ATTENDANCE_SETTINGS` | `GET /api/attendance/settings` | `PUT /api/attendance/settings` | 管理角色；租户级配置 |
| 角色权限与路由 `access-control` | `ACCESS_CONTROL` | `GET /api/admin/access-control` | 角色、成员、组织、权限和路由配置 | `access:manage`；最后一名有效超级管理员不可降级 |
| 数据权限 `data-permission` | `DATA_PERMISSION` | `GET /api/admin/data-permissions`、预览 | 策略维护、角色绑定、用户例外 | `data-scope:manage`；只处理当前租户 |
| AI 操作权限 `ai-permission` | `AI_PERMISSION` | `GET /api/admin/ai-operation-permissions` | 租户策略、工具、角色策略更新 | `agent-permission:manage`；不能放宽永久禁止项 |
| 知识库管理 `knowledge-base` | `KNOWLEDGE_BASE` | 知识库、文档、检索、Embedding 状态 | 知识库/文档新增、上传、重建索引、删除 | 知识库成员与租户隔离；文件和向量依赖失败不伪造 |
| 审计中心 `audit-center` | `AUDIT_CENTER` | `GET /api/audit-records` | — | `audit:read`；只读且按租户/范围过滤 |
| 租户配置 `tenant-config` | `TENANT_CONFIG` | `GET /api/admin/tenant-config`、历史 | profile/features/business/security 更新 | `tenant:config:manage`；不回显连接串与密钥 |
| 数据字典 `dictionary` | `DICTIONARY` | 字典类型、条目与公开条目读取 | 类型/条目新增、更新、启停、删除 | 管理需 `dictionary:manage`；业务读取仍认证 |
| 系统配置 `system-config` | `SYSTEM_CONFIG` | `GET /api/admin/system/capabilities`、用户聊天/OCR 设置 | `PUT /api/settings/chat`、`PUT /api/settings/ocr` | 能力摘要需 `access:manage`；用户偏好仅写本人，密钥只在服务端环境变量 |

## 4. 核心接口契约

### 4.1 导航与实时权限

- `GET /api/auth/me`：返回当前用户、角色、实时权限、数据范围与 `permissionVersion`。
- `GET /api/navigation`：只返回当前认证用户有权访问的页面及祖先目录。
- 登录恢复、窗口重新聚焦、权限配置成功和服务端 403 会触发权限快照刷新。
- 数据库只提供受信任组件键；前端仅从 `OA_PAGE_REGISTRY` 渲染，未知键进入明确错误态。

### 4.2 驾驶舱

- `GET /api/dashboard/overview?days=7`：返回 `generatedAt`、指标、待办摘要、近七日趋势、业务分布、最近活动及可选健康摘要。
- `POST /api/dashboard/export`：请求时间范围和筛选；返回 `fileName`、`contentType`、UTF-8 CSV 内容与行数。最多 1000 条，防止 CSV 公式注入并写审计。
- `GET /api/settings/dashboard`、`PUT /api/settings/dashboard`：在既有 `user_setting` 中保存有序 `metricCodes`；未知、重复或无权指标不能生效。
- 驾驶舱审批按钮只导航到审批详情；AI 预审只传任务 ID 给受控 Agent，不直接作出审批决定。

## 5. 自动化验收证据

```powershell
cd fonted-oa
npm run lint
npm run test
npm run build
npx playwright test e2e/oa-pages.spec.ts

cd ../backend
mvn test
```

| 门禁 | 结果 |
| --- | --- |
| OA lint | 0 errors；3 个既有 React Hooks warnings |
| OA Vitest | 26 files / 75 tests 通过 |
| OA production build | 通过；主入口约 3.25 MB，AI Workspace 与组织图独立分包 |
| Chromium 页面回归 | 7 tests 通过；41 页面逐条进入，且组件集合与注册表一致 |
| 响应式 | `1440×900`、`1920×1080`、`2560×1080`、`390×844` 无 body 横向截断 |
| 权限变化 | 失去当前页面权限后无需重新登录，自动回到仍可访问页面 |
| 失败与空态 | 注入 503 时无白屏；空待办显示 Ant Design 空态并填充工作区 |
| 后端 Maven | 538 tests，0 failures，0 errors，9 skipped |

后端 9 个跳过项是既有、需要独立外部运行环境的 Agent/Testcontainers 集成测试，未被静默计为通过。数据库路由数量由 `P1PostgresMigrationIT` 的强制真实 PostgreSQL 门验证：启用 `PAGE` 必须恰为 41，且不得存在正式 `WORKBENCH_MODULE` 或错误 `DASHBOARD` 回退。

## 6. 部署与运行依赖

- Java 17、Maven 3.9+、Node.js 20+。
- PostgreSQL 16 + pgvector；Flyway 是唯一 Schema 入口，禁止修改已发布迁移。
- Redis 用于验证码、登录保护、通知及运行时辅助；不可用时能力状态返回不可用。
- MinIO 用于聊天附件、员工档案、印章留档、头像与壁纸；数据库不保存原始文件。
- AI、Embedding、OCR 均通过服务端环境变量配置；API Key、连接串、内部地址不得返回前端。
- `fronted-main` 和 `fonted-oa` 必须继续独立部署；OA 的 Vite base 为 `/oa/`。
- 生产反向代理应把 `/oa/**` 回退到 OA `index.html`，把 `/api/**` 转发到后端，并保留 SSE 所需的长连接配置。
- 首次安装 Playwright 回归运行时执行 `npx playwright install chromium`；该浏览器二进制不进入 Git。

## 7. 已知限制

- 浏览器门禁以受控网络响应验证全部路由、错误恢复和布局；真实多账号跨角色业务 E2E 仍需提供测试环境账号后运行 `e2e/leave-approval.spec.ts`。
- OA 主入口仍超过 Vite 500 kB 建议阈值；大型 AI Workspace 和组织图已独立分包，后续可继续拆分 Ant Design/ECharts 公共依赖。
- 运行日志和能力状态只提供安全摘要，不替代基础设施监控。
- 外部 AI、OCR、Embedding、MinIO 或 Redis 故障时，对应业务会失败关闭；本阶段不引入离线伪成功。
- Agent 写能力仍是单任务最多一个写步骤、默认关闭、人工发布；权限修改、删除、批量操作、敏感导出、任意 URL/SQL/文件系统等永久禁止能力未开放。

## 8. 回滚方法

1. 代码按 R4-8 到 R3-1 的逆序执行 `git revert <commit>`；禁止使用 `git reset --hard` 改写共享历史。
2. R3–R4 没有新增迁移。若连同更早业务提交回滚，已发布 Flyway 文件仍不得删除、重命名或改 checksum；结构修正使用更高版本前向迁移。
3. 回滚驾驶舱代码前保留 `user_setting` 中的指标偏好；旧版本会忽略未知设置键，无需删除用户数据。
4. 回滚权限前先验证目标版本仍能识别开发库中的组件键，否则先停用不兼容路由。
5. MinIO 对象、数据库元数据和审计记录分别备份；代码回滚不会自动删除对象或审计。
6. 在预发布环境重新执行 lint、单元测试、生产构建、41 页面 Chromium 回归和真实 PostgreSQL 门后再切换流量。

本文档只使用占位域名和接口路径，不包含真实账号、密钥、生产地址或连接信息。
