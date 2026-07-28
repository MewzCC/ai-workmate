# Phase 1 交付与验收记录

## 1. 交付范围

本阶段完成企业权限、组织关系、请假申请、单级审批、待办、时间线和业务审计的真实闭环。营销官网与 OA 仍为两个独立 Next.js 应用；AI plan/execute 未增加模拟成功或自动审批能力。

## 2. 数据升级

- 唯一数据库入口仍为 `backend/src/main/resources/db/init.sql`。
- 新增默认租户 `DEFAULT / AI WorkMate`、部门、岗位、多角色授权、数据范围、工作流、请假和业务审计表。
- 为用户、RBAC、会话、附件、知识文档及既有审计数据增加并回填 `tenant_id`。
- 增加租户索引、唯一待办约束、业务状态约束及乐观锁版本字段。
- 已在 PostgreSQL 16 临时空库中连续执行 `init.sql` 两次，旧结构升级和幂等执行均通过。

## 3. 权限与认证

- JWT 只作为用户身份凭据；每次请求按用户 ID 从数据库重新解析租户、有效角色、权限、数据范围和 `permission_version`。
- 认证响应新增 `tenantId`、`roles[]`、`dataScopes[]` 和 `permissionVersion`，兼容字段 `role` 保留。
- `user_role` 成为真实多角色授权来源，`app_user.role` 仅保留为主角色镜像。
- 权限后台支持多角色、启停状态、部门、岗位、直属审批人和部门默认审批人维护；用户—角色—权限支持关系视图/配置表格切换，新增角色默认不授予任何权限，由管理员在权限矩阵中显式配置。
- 组织权限拆分为 `org:read` 与 `org:manage`：普通成员可查看组织架构，编辑按钮仅对被授权角色显示，服务端同步强制校验。
- 最后一名有效 `SUPER_ADMIN` 受服务端保护，不允许停用或移除超级管理员角色。

## 4. 请假与审批闭环

- 状态机为 `DRAFT → PENDING → APPROVED | REJECTED`，并支持 `PENDING → WITHDRAWN`。
- 日期按连续日历半天计算，周末计入，最小 `0.5` 天；结果由服务端计算并持久化，开始日期不能早于服务端当天。
- 创建只生成草稿；申请人可编辑、提交和撤回自己的申请。
- 每张草稿必须从可搜索候选列表中选择本次审批人；候选人限定为申请人本部门或祖先部门内、同租户、启用且实时拥有 `approval:act` 的非本人用户。
- 直属审批人和部门默认审批人用于生成推荐项；保存草稿及提交时都会重新验证所选审批人，组织或权限变化后无效的审批人不能继续提交。
- 提交、撤回和审批均携带版本号，并通过条件更新保证重复点击或并发操作只有一次成功，其余返回 HTTP 409。
- 审批人必须是当前待办受理人且不能是申请人；通过意见选填，退回意见必填。
- 成功、拒绝、越权和冲突操作均写入带 traceId 的脱敏业务审计。

## 5. 接口与页面

已交付以下后端接口：

- 请假：`POST/PUT/GET /api/leave-applications`、mine、submit、withdraw。
- 待办与审批：`GET /api/todos`、详情、approve、reject、timeline。
- 审计：`GET /api/audit-records`，要求 `audit:read`。
- 权限管理：用户多角色、组织、状态及部门/岗位维护接口；旧单角色接口保留兼容。
- 组织架构：`GET /api/organization` 面向 `org:read`，部门、岗位、成员关系写接口要求 `org:manage`；新增安全删除接口阻止删除仍有关联数据的角色、部门或岗位。
- 审批候选：`GET /api/leave-applications/approver-candidates` 支持姓名、部门和岗位搜索。

OA 新增并接入真实接口：

- `/oa/todo`
- `/oa/leave-application`
- `/oa/my-applications`
- `/oa/approval-tasks/{id}`
- `/oa/audit-center`
- `/oa/org-tree`：组织图/树形目录切换、成员检索、部门详情、审批关系与权限感知编辑。

动态组件只允许从固定注册表解析；页面统一处理加载、空态、401、403、409、网络失败及重复提交。

## 6. 验证结果

- 后端：Java 17 + Maven 测试通过，48 tests，0 failures，0 errors，1 skipped。
- 数据库：本机 PostgreSQL 16 空库连续执行 `init.sql` 两次通过。
- OA：Vitest 2 个测试文件、6 项测试通过；lint 和生产 build 通过。
- 营销官网：lint 和生产 build 通过。
- 真实 E2E：临时 PostgreSQL 数据库和临时后端中完成“员工登录并提交 → 审批人登录并通过 → 员工查看结果与时间线”，Playwright 1 项通过；测试进程和测试库已清理。
- CI：新增 Java 17、PostgreSQL 16、Redis、双前端 lint/build、后端测试和 OA 单测流程；带测试账号的手动任务可执行关键 E2E。

本机没有可用 Docker，因此 Testcontainers 用例自动跳过；数据库幂等和真实审批链路已改用本机 PostgreSQL 16 实测覆盖。

## 7. 已知风险

- 一期仅实现单级审批，不包含余额、附件、工作日历、通知、多级流程、流程设计器或 AI Tool Calling。
- npm 依赖审计仍报告既有及传递依赖风险，未使用可能引入破坏性升级的自动修复；应单独安排依赖升级回归。
- 当前数据模型为多租户兼容，但一期只启用默认租户；租户开通和租户管理后台不在本期范围。

## 8. 回滚说明

数据库变更保持加法兼容。出现应用问题时优先回退应用版本并保留新增表、列和已生成业务数据；不得通过删除工作流、请假或审计数据进行回滚。待修复版本上线后继续使用原数据。
