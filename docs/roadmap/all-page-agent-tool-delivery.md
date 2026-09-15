# 全页面 Agent 工具交付清单

## 口径与边界

核对日期：2026-09-15。代码依据为 `PageCapabilityCatalog`、`ToolCode`、代码工具契约和对应领域 Port。目录登记不等同于运行启用、接口完整或浏览器验收通过。会议预约代码已在 7f8b44e0 提交，写开关仍默认关闭，尚未通过生产人工发布门及真实 LLM 端到端验收。

所有工具必须经过进程内 ToolGateway；实时业务权限和独立 Agent 工具权限均须满足。领域再次校验租户、本人或数据范围、业务状态和版本。一个任务最多一个写步骤；全局与租户写开关默认关闭，开发完成不代表人工发布门通过。

本表中的待开发写入是设计候选，不构成启用授权；审批最终决定、财务生效等高风险能力需另行风险评审。删除、批量、付款、敏感导出、外部消息、权限及安全配置写入不纳入本阶段。

## 页面工具矩阵

| 页面 ID | 已登记查询工具 | 已登记写工具 / 后续范围 |
| --- | --- | --- |
| dashboard | TODO_QUERY、NOTIFICATION_MINE | 真实详情导航；不在驾驶舱直接最终审批 |
| ai-workspace | TODO_QUERY、LEAVE_MINE、KNOWLEDGE_SEARCH、NOTIFICATION_MINE | LEAVE_CREATE_DRAFT、LEAVE_SUBMIT、LEAVE_APPLY；后续复用领域工具，不另写业务逻辑 |
| ai-tasks | AGENT_TASK_MINE_QUERY | 任务状态查询；不得创建后台自治链路 |
| todo | TODO_QUERY | 待办详情和受控预审；最终审批保持详情人工确认 |
| messages | NOTIFICATION_MINE | 候选：本人单条已读 |
| leave-application | LEAVE_MINE | LEAVE_CREATE_DRAFT、LEAVE_SUBMIT、LEAVE_APPLY；候选撤回 |
| my-applications | LEAVE_MINE | 复用请假写工具；待扩展通用申请查询与生命周期 |
| approval-list | APPROVAL_TASK_QUERY | 候选：本人申请撤回；不得把查询权限当审批权限 |
| approval-start | APPROVAL_CONFIGURATION_QUERY | 待开发通用申请原子提交 |
| approval-form | APPROVAL_CONFIGURATION_QUERY | 待开发通用草稿、提交、撤回、重新提交 |
| form-engine | APPROVAL_CONFIGURATION_QUERY | 配置只读；不开放流程定义写入 |
| process-config | APPROVAL_CONFIGURATION_QUERY | 配置只读 |
| approval-rules | APPROVAL_CONFIGURATION_QUERY | 配置只读 |
| org-tree | HR_ORGANIZATION_QUERY | 组织只读 |
| employee-files | HR_ORGANIZATION_QUERY、HR_EMPLOYEE_QUERY | 档案最小字段查询；不输出薪酬或附件内部路径 |
| employee-change | HR_CHANGE_QUERY | 候选：单条员工变动申请；生效需独立评审 |
| asset-ledger | ASSET_QUERY | 候选：领用、归还、维修登记，逐工具交付 |
| meeting-room | MEETING_QUERY | MEETING_BOOK 已提交、默认关闭；候选本人取消 |
| visitor-booking | VISITOR_QUERY | 候选：申请、签到、离场，逐工具交付 |
| seal-usage | SEAL_QUERY | 候选：申请、实际用印登记，逐工具交付 |
| expense | EXPENSE_QUERY | 候选：本人草稿和原子提交；不付款 |
| budget | BUDGET_QUERY | 保持受控只读；预算生效不自动开放 |
| contracts | CONTRACT_QUERY | 候选：单条合同草稿；不签署、付款或对外发送 |
| suppliers | SUPPLIER_QUERY | 候选：单条资料草稿；需明确敏感字段与业务权限 |
| api-center | INTEGRATION_ENDPOINT_QUERY | 安全元数据只读；不得任意 URL 调用 |
| page-actions | PAGE_ACTION_QUERY | 配置只读；不得修改自己的执行能力 |
| runtime-logs | RUNTIME_LOG_QUERY | 脱敏只读；不返回密钥、堆栈或完整参数 |
| sandbox-replay | SANDBOX_REPLAY_QUERY | 历史只读；不增加通用工具试运行 |
| attendance-clock | ATTENDANCE_QUERY | 保持只读；不让 Agent 伪造位置或打卡证据 |
| attendance-exception | ATTENDANCE_QUERY | 受权限和数据范围控制的异常查询 |
| attendance-reissue | ATTENDANCE_QUERY | 候选：本人补卡申请 |
| attendance-statistics | ATTENDANCE_QUERY | 受限统计只读 |
| attendance-settings | ATTENDANCE_QUERY | 配置只读 |
| access-control | ACCESS_GOVERNANCE_QUERY | 权限治理只读，写入永久禁止 |
| data-permission | DATA_PERMISSION_QUERY | 数据权限只读，写入永久禁止 |
| ai-permission | AI_PERMISSION_QUERY | 策略摘要只读，工具开关写入永久禁止 |
| knowledge-base | KNOWLEDGE_SEARCH | 检索只读；不让工具接受路径或任意 URL 导入 |
| audit-center | AUDIT_QUERY | 审计只读，不修改或删除审计 |
| tenant-config | TENANT_CONFIGURATION_QUERY | 安全摘要只读，配置写入永久禁止 |
| dictionary | DICTIONARY_QUERY | 字典查询；后续写入须确认非安全配置并单独评审 |
| system-config | SYSTEM_CAPABILITY_QUERY | 安全状态只读；不读取环境变量、内部地址或密钥 |

## 顺序与独立提交

1. T1：维护本清单，补齐每个候选工具的领域方法、权限、风险和失败用例证据。
2. T2：收口页面能力、工具契约、分页和可用原因；不新增第二套注册表。
3. T3：按审批、人事、行政、财务、治理拆分类型化 Port 与 Adapter，避免巨型适配器；不做万能 CRUD。
4. T4：复用稳定操作键、确认、错误分类及结果核验，领域事务与状态机不得被通用模板取代。
5. T5：会议原子预约已提交，开发库迁移及二次启动通过；发布门和真实 LLM 端到端验收仍待完成。
6. T6–T8：按上述候选逐个工具实施，每个工具单独测试、单独中文提交，不并行混合领域。
7. T9：统一页面入口、表单预填、预览、受控确认、结果刷新及不可用原因。
8. T10：模块化单体先行，形成远程 Adapter 的固定目标、可信身份和幂等协议；不立即引入 Spring Cloud 或分布式事务。
9. T11：全页面与逐工具安全回归、浏览器验收、迁移和回滚记录。

T2 当前切片：`PageToolContractValidator` 在 Spring 启动时交叉校验现有页面目录与工具目录，拒绝未注册引用、读写副作用错配及未绑定页面的工具。它不读取数据库、不授予权限、不取代运行时策略；保留工具契约和迁移哈希不变。新增 5 个定向测试覆盖正常目录及上述失败分支。分页与可用原因的进一步收口尚未完成，不能将 T2 整体标记完成。

T2 分页切片：21 个查询 Handler 统一复用 `BoundedToolArguments.pageNumber/pageSize`，默认页码 1、每页 20、每页硬上限 50；已有 10000 页上限的工具保持该上限，旧版无页码上限工具显式保留其冻结契约。Schema、版本、输出结构、领域权限及数据库迁移不变。共享参数测试覆盖默认值、边界、旧契约及数值类型混淆；各领域仍使用自身类型化查询模型，不引入万能参数 Map。工具不可用原因仍待后续收口。

T2 可用性内部切片：`ToolRegistry.resolveAvailability` 与旧执行解析复用单一实现，返回 AVAILABLE、DISABLED、UNAVAILABLE；只有 AVAILABLE 可携带领域契约。结果仅表示租户工具配置解析，不代表用户权限、页面权限、Worker 租约或确认通过，不能作为执行许可。未注册、非法契约及范围不符不返回定义；策略依赖异常仍传播并失败关闭。没有新增公共诊断或执行接口。页面级权限安全过滤、国际化原因和前端展示尚未接入，T2 保持未完成。

T2 页面提示切片：既有受认证页面能力接口在实时页面权限与注册表过滤后，仅为空工具集合返回 `unavailableReason=NO_AVAILABLE_TOOLS`；有工具时为 null。不会列举受限工具、内部开关、权限策略或异常。AI Drawer 复用独立 Ant Design 提示组件，提供中英文安全通用说明；旧后端缺字段时仍显示空工具提示，未知原因不回显。请求失败继续走错误与重试态，不转换成空集合或假成功。此字段不授予执行权限，也不证明模型或外部依赖健康；实际执行仍由 Gateway 复核。浏览器响应式验收仍待完成。

T3 会议边界切片：会议查询与预约移入独立 `MeetingAgentDomainToolAdapter`，综合行政适配器不再实现 MeetingToolPort 或依赖预约 Service。Port、Handler、领域方法、工具 Schema 和权限均保持不变，不复制业务状态机。会议室基础查询暂时复用现有 AdminAssetsService，完整领域 Service 拆分仍待实施，不能宣称已经可以独立部署。新增适配器测试验证本人身份和稳定操作键映射、领域拒绝无回退、会议室查询上限和本人预约分页；一 Port 一适配器门禁继续生效。

T3 行政边界切片：移除同时实现资产、访客和印章 Port 的综合适配器，分别建立 Asset、Visitor、Seal 本地适配器。三个适配器当前仍复用 AdminAssetsService，因此只完成 Agent 适配层边界，不代表领域 Service 或数据库已拆分。工具契约、查询结果裁剪、本人或待办队列语义、实时权限和数据库均不变；一 Port 一适配器门禁阻止后续重新聚合。

T3 人事边界切片：移除同时实现组织、员工档案、员工变动和考勤 Port 的综合适配器，分别建立四个本地适配器。组织与员工档案仍复用 HrService，员工变动与考勤继续复用各自现有领域 Service；本切片只收口 Agent 适配层替换点，不改变工具契约、数据裁剪、实时权限、业务状态机或数据库。一 Port 一适配器门禁用于防止这些边界再次合并。

## 服务化验收

Handler 只能转换封闭参数并调用 Port。Port 使用类型化命令及结果，不依赖 Spring、HTTP、数据库实体或通用参数 Map。本地 Adapter 与普通页面接口复用同一领域 Service。远程化仅替换 Adapter，但领域服务仍须从可信身份解析租户与用户，不能信任客户端字段。

远程写超时先按稳定操作键核验结果，不盲目重试。领域业务与审计处于同一事务；跨服务协作确有需要后再设计 Outbox、事件去重和补偿，不共享数据库事务。

## 每工具证据门槛

- 固定输入输出 Schema、版本哈希、独立权限、作用域、结果上限、风险和确认策略。
- 架构门禁证明 Handler 不依赖 Mapper，其他 Agent 组件不绕过 Gateway。
- 参数缺失、未知字段、越权、跨租户、权限回收、非法状态、重复请求和版本冲突测试。
- 写工具增加真实 PostgreSQL 并发、审计回滚和结果核验测试。
- 前端 lint、test、build 与后端 mvn test 全量通过；数据库变更额外执行空库、旧库升级、Flyway validate 和开发库二次启动。
- 页面加载、错误、空态、权限变化和桌面/移动端验收分别记录；没有浏览器证据不得标记已验收。
- 仅暂存本节点相关文件，中文提交到 feature/zcc；回滚优先关闭单工具，保留已发布迁移及业务数据。
