# 全页面 Agent 工具交付清单

## 口径与边界

核对日期：2026-09-15。代码依据为 `PageCapabilityCatalog`、`ToolCode`、代码工具契约和对应领域 Port。目录登记不等同于运行启用、接口完整或浏览器验收通过。已登记写工具的全局与租户写开关仍默认关闭，尚未通过生产人工发布门及真实 LLM 端到端验收。

所有工具必须经过进程内 ToolGateway；实时业务权限和独立 Agent 工具权限均须满足。领域再次校验租户、本人或数据范围、业务状态和版本。一个任务最多一个写步骤；全局与租户写开关默认关闭，开发完成不代表人工发布门通过。

本表中的待开发写入是设计候选，不构成启用授权；审批最终决定、财务生效等高风险能力需另行风险评审。删除、批量、付款、敏感导出、外部消息、权限及安全配置写入不纳入本阶段。

## 页面工具矩阵

| 页面 ID | 已登记查询工具 | 已登记写工具 / 后续范围 |
| --- | --- | --- |
| dashboard | TODO_QUERY、NOTIFICATION_MINE | 真实详情导航；不在驾驶舱直接最终审批 |
| ai-workspace | TODO_QUERY、LEAVE_MINE、KNOWLEDGE_SEARCH、NOTIFICATION_MINE | LEAVE_CREATE_DRAFT、LEAVE_SUBMIT、LEAVE_APPLY、LEAVE_WITHDRAW、ATTENDANCE_REISSUE_APPLY、APPROVAL_APPLICATION_CREATE_DRAFT；复用领域工具，不另写业务逻辑 |
| ai-tasks | AGENT_TASK_MINE_QUERY | 任务状态查询；不得创建后台自治链路 |
| todo | TODO_QUERY | 待办详情和受控预审；最终审批保持详情人工确认 |
| messages | NOTIFICATION_MINE | NOTIFICATION_MARK_READ；仅允许标记本人单条消息已读，默认关闭且需显式确认 |
| leave-application | LEAVE_MINE | LEAVE_CREATE_DRAFT、LEAVE_SUBMIT、LEAVE_APPLY、LEAVE_WITHDRAW；默认关闭写开关，撤回禁止自动重试 |
| my-applications | LEAVE_MINE | 复用请假写工具与 APPROVAL_APPLICATION_CREATE_DRAFT；待扩展通用申请查询及其余生命周期 |
| approval-list | APPROVAL_TASK_QUERY | 候选：本人申请撤回；不得把查询权限当审批权限 |
| approval-start | APPROVAL_CONFIGURATION_QUERY | APPROVAL_APPLICATION_CREATE_DRAFT；待开发通用申请原子提交 |
| approval-form | APPROVAL_CONFIGURATION_QUERY | APPROVAL_APPLICATION_CREATE_DRAFT；待开发提交、撤回、重新提交 |
| form-engine | APPROVAL_CONFIGURATION_QUERY | 配置只读；不开放流程定义写入 |
| process-config | APPROVAL_CONFIGURATION_QUERY | 配置只读 |
| approval-rules | APPROVAL_CONFIGURATION_QUERY | 配置只读 |
| org-tree | HR_ORGANIZATION_QUERY | 组织只读 |
| employee-files | HR_ORGANIZATION_QUERY、HR_EMPLOYEE_QUERY | 档案最小字段查询；不输出薪酬或附件内部路径 |
| employee-change | HR_CHANGE_QUERY | 候选：单条员工变动申请；生效需独立评审 |
| asset-ledger | ASSET_QUERY | 候选：领用、归还、维修登记，逐工具交付 |
| meeting-room | MEETING_QUERY | MEETING_BOOK、MEETING_CANCEL；本人单条预约与取消，写开关默认关闭，发布门及真实模型端到端待验收 |
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
| attendance-reissue | ATTENDANCE_QUERY | ATTENDANCE_REISSUE_APPLY；只提交本人单条待审批申请，不直接改打卡证据 |
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

T3 审批边界切片：移除同时实现待办、请假、审批配置和审批任务 Port 的综合适配器，分别建立四个本地适配器。待办、请假和审批任务仍复用 LeaveWorkflowService，审批配置继续复用 ApprovalEngineService；请假写入仍携带稳定操作键或任务 ID 并由领域 Service 执行实时身份、状态及幂等校验。本切片不增加审批决定能力，不改变工具开关、风险等级、确认策略、数据库或现有页面接口。

T6 消息已读切片：增加 `notification.markRead` 单条原子写工具，只接收消息 ID，用户和租户身份来自 ToolGateway 可信上下文。领域 Service 重新解析实时用户权限，并按租户与本人所有权查询后更新；重复执行保持已读状态，返回可核验的消息 ID 与已读标志。工具为 L1、显式确认、业务幂等、单写步骤，平台和租户写开关继续默认关闭；不开放批量已读，也不返回内部业务 ID。

## 服务化验收

T6 请假撤回代码切片：新增 leave.withdraw 封闭契约、Handler 和类型化 Port，Adapter 复用现有 LeaveWorkflowService.withdraw，不复制审批状态机。仅接收申请 ID 和预期版本，要求本人归属与实时 leave:withdraw 权限，风险 L1、显式确认、单写步骤、RetryPolicy.NEVER；未知执行结果不能自动重试。成功审计改为参与业务事务。定向55项及冻结契约测试通过，真实PostgreSQL撤回并发、任务取消、审计失败回滚、权限回收、跨租户及其他申请人失败场景通过；空库、旧库升级、69个迁移validate与重复启动零迁移通过。OA lint无错误（3条既有警告）、89项及构建通过；后端756项零失败、9项既有环境测试跳过。开发库V202609151740二次启动与健康检查通过。写开关保持默认关闭，真实模型端到端、浏览器逐工具和人工发布门待验收，不标记全页面目标完成。

T4 稳定操作键代码切片：四个既有请假及会议写 Handler 统一调用纯函数 StableToolOperationKey.v1，保留已持久化键格式且不包含 Worker attempt 或 traceId。此函数只生成操作标识，不构成执行许可；租户和本人隔离仍由领域查询条件及唯一索引保证。工具 Schema、版本、数据库迁移与业务事务不变。兼容性、重试稳定性、非法可信坐标及既有 Handler 定向共10项通过；OA lint无错误（3条既有警告）、89项测试和构建通过；后端754项零失败、9项既有环境测试跳过。远程 Adapter 的超时结果查询协议仍待后续实现，不能标记T4整体完成。

T6 会议取消代码切片：复用 MeetingBookingService 的事务核心，Agent 专用入口强制本人预约和实时 meeting:cancel 权限；即使幂等重放也重新鉴权。独立取消操作键用于重复调用结果核验，不复用创建操作键；管理员权限不允许 Agent 代取消。封闭命令携带预约 ID、预期版本和可选理由，取消更新与审计同事务。定向 36 项测试通过，真实 PostgreSQL 验证并发重复取消只写一次审计、重放内容冲突拒绝与审计失败事务回滚；空库、旧库升级、68 个迁移 validate 和二次启动零迁移通过。前端 lint 无错误（3 条既有警告）、89 项测试和构建通过；后端 752 项测试零失败、9 项既有环境测试跳过。开发库版本为 V202609151700。写开关继续默认关闭，人工发布门、浏览器逐工具与真实 LLM 端到端仍待验收，不等于全页面交付完成。

Handler 只能转换封闭参数并调用 Port。Port 使用类型化命令及结果，不依赖 Spring、HTTP、数据库实体或通用参数 Map。本地 Adapter 与普通页面接口复用同一领域 Service。远程化仅替换 Adapter，但领域服务仍须从可信身份解析租户与用户，不能信任客户端字段。

远程写超时先按稳定操作键核验结果，不盲目重试。领域业务与审计处于同一事务；跨服务协作确有需要后再设计 Outbox、事件去重和补偿，不共享数据库事务。

## 后续执行节点与复用原则

2026-09-15 补充：采用模块化单体先行，不立即引入 Spring Cloud。以下节点承接已有 T1–T11，不重建第二套目录、不重新交付已经完成的工具；当前全部为剩余工作计划，不代表验收完成。

| 节点 | 剩余交付内容 | 依赖与验收 |
| --- | --- | --- |
| P1 | 完善逐工具领域方法、独立权限、风险和禁止项证据 | 核对页面目录、工具契约与实际领域入口；候选不视为启用授权 |
| P2 | 补齐页面目录、代码契约与运行配置一致性门禁 | 复用既有目录；失效权限与异常失败关闭，不泄露受限工具 |
| P3 | 统一剩余参数解析、可信身份映射和安全错误分类 | 保持冻结 Schema；不由模型提供租户、申请人或操作人 |
| P4 | 类型化原子操作结果核验协议 | 稳定操作键不是授权；未知结果先核验，没有可靠核验时禁止自动重试 |
| P5 | 本人补卡申请原子工具 | 复用考勤申请服务；不直接修改考勤证据；并发防重复与审计回滚 |
| P6 | 通用申请草稿、原子提交、撤回和重新提交 | 每个工具独立切片；复用审批引擎与冻结快照；一个任务最多一个写步骤 |
| P7 | 资产领用、归还和维修登记 | 每工具独立提交；领域校验保管人、数据范围、状态与版本 |
| P8 | 访客申请、签到、离场和用印申请、登记 | 每工具独立提交；登记风险单独评审，不自动增加审批权限 |
| P9 | 员工变动申请 | 仅发起申请，不直接生效或修改员工档案 |
| P10 | 费用草稿及原子提交、合同与供应商候选草稿 | 先证明领域支持草稿；不付款、不签署、不外发；每工具独立提交 |
| P11 | 全页面统一预览、显式确认、真实结果刷新和不可用说明 | 复用 Ant Design 交互；缺信息追问，失败不伪造成功；桌面与移动端验收 |
| P12 | 模块化单体依赖与数据所有权门禁 | Port 不依赖框架、HTTP 或实体；逐步清除跨领域 Mapper，不仅拆 Adapter |
| P13 | 固定目标只读远程 Adapter 协议试点 | 内部可信身份与远端重复鉴权；不提供任意 URL；引入新基础设施前单独评审 |
| P14 | 全页面逐工具安全、业务、浏览器与真实模型回归 | 按原始页面范围逐项证明完成，记录发布门与回滚，不以窄测试代替全量验收 |

实施优先完成 P1–P4 的剩余共性工作，再逐工具推进 P5–P10，最后统一交互和服务化验收。P1–P4 不重复改写已经验证的分页、可用原因或稳定操作键；只有确有重复或缺口时才新增封装。

页面只是入口，同一个业务工具可绑定多个页面。Handler 保持薄且显式，调用类型化 Port；不引入万能 CRUD、通用写 Map 或替代领域状态机的模板。可复用的是参数解析、确认展示、错误分类和结果核验，不可泛化的是领域事务、实时资源授权及合法状态流转。

服务化前明确各领域的数据所有权。远程化只替换 Adapter，目标由服务端固定配置或服务发现确定，远端不得信任转发权限列表；写超时按稳定操作键核验。单体阶段保留领域事务，确有跨服务协作再设计 Outbox、事件去重和补偿，不提前引入分布式事务或共享数据库事务。远程 Adapter 试点不等于已具备独立部署能力。

P12 类型化边界切片：在既有传输中立 Port 门禁之外，禁止 Port 及其嵌套契约依赖 Map 或其实现类，防止后续把领域命令退化为通用参数字典。反向夹具证明 Map 输入输出会被拒绝；现有类型化 Port 保持不变，不新增依赖、接口或数据库迁移。本切片不证明远程结果核验协议、跨领域数据所有权或独立部署已经完成。

P4 会议创建核验协议切片：MeetingToolPort 增加类型化 findCreation，本地 Adapter 映射到领域只读 findAgentCreation。查询复用稳定创建操作键，领域重新解析有效用户和 meeting:book 权限，并在 SQL 中限定实时租户与本人；找到记录后复用已有幂等命令匹配，不调用创建方法。返回当前资源状态，因此已经取消的预约仍可证明创建记录存在，不意味着预约仍有效。Optional.empty 仅表示未观察到已提交记录，不能证明在途写入失败或授权重试。没有新增 HTTP、Handler、工具 Schema、迁移或写开关，也未接入 Worker 自动核验；其他写工具及远程超时协议仍未完成。定向28项通过；真实 PostgreSQL 覆盖取消后结果、审计回滚后空结果、其他申请人、跨租户与权限回收，空库及旧库升级、69个迁移validate与重复启动零迁移通过，数据库测试零跳过。

P4 会议取消核验协议切片：MeetingToolPort 增加类型化 findCancellation，Adapter 只读调用领域 findAgentCancellation，不调用取消写入口。领域重新校验有效用户及 meeting:cancel 权限，SQL 限定实时租户与本人，复用取消操作键及原有预约ID、版本、理由、取消人和状态匹配；管理员不能代核验其他用户。空结果不证明在途取消失败，不授予重试许可。Adapter 测试覆盖成功收据裁剪和拒绝无写入回退；领域测试覆盖空结果和无效用户拒绝。真实 PostgreSQL 覆盖成功核验、命令冲突、审计回滚后空结果及权限回收，空库及旧库升级、69个迁移validate和重复启动零迁移通过，数据库测试零跳过。未新增公共入口、工具契约、迁移、自动恢复或写开关；P4 全工具与远程协议仍未完成。

P4 写操作契约收口切片：新增传输中立的 `ToolOperationKey`、`ToolWriteReceipt` 与 `ToolWriteVerification`，统一稳定操作键、领域专属写收据及显式 `OBSERVED/UNOBSERVED` 核验语义。现有操作键写 Port 改用受限值对象，Adapter 仅在调用本地领域 Service 时解包；会议创建与取消由 Optional 改为显式核验结果，`UNOBSERVED` 继续禁止自动重试。既有领域结果 record 只增加无字段标记，不改变工具 JSON、冻结 Schema、版本 Hash、权限、确认、开关或状态机；未来远程 Adapter 可复用同一可信身份、操作键和只读核验协议，不引入任意 URL、Spring Cloud 依赖或分布式事务。73 项定向契约与冻结 Hash 测试通过；OA lint 无错误（3 条既有警告）、91 项测试与生产构建通过；后端 816 项零失败、9 项既有环境测试跳过。

P3 安全错误分类切片：ToolGateway 新增消息无关的代码白名单分类，将确定性的参数、访问、资源不存在、状态/版本冲突和限流映射为稳定公开错误；未识别异常继续按只读不可用或写入结果未知失败关闭。审计、任务、SSE 和模型上下文不接收异常消息或堆栈，Worker 不会重试确定性拒绝；OA 任务中心提供中英文安全说明，未知错误只显示通用错误码文案。本切片不改变工具 Schema、权限、确认、写开关、领域事务或重试策略，也不新增迁移与远程依赖。44 项网关、Worker 与架构定向测试通过；OA lint 无错误（3 条既有警告）、92 项测试与生产构建通过；后端 823 项零失败、9 项既有环境测试跳过。

P5 补卡领域基础切片：普通页面和后续 Agent 共用 AttendanceService.submitReissue。服务端新增 attendance:reissue:apply 独立业务权限，不再把页面路由当作写权限；在事务中锁定当前租户的有效申请人，串行完成待审重复检查、申请插入和业务审计。申请人必须属于实时租户，直属审批人必须有效、同租户且不是本人。提交只创建 PENDING 申请，不直接补写打卡记录。前端复用实时权限 Hook 控制 Ant Design 创建入口，权限撤销后关闭已打开弹窗，服务端仍逐请求鉴权。真实 PostgreSQL 验证并发只成功一次且只写一次审计、审计失败回滚、打卡记录为零；空库和旧库升级、70个迁移validate及重复启动通过，数据库测试零跳过。

P5 补卡 Agent 工具切片：新增 `attendance.reissue.apply` 冻结契约，输入只含日期、上下班类型和原因，申请人与租户只取 ToolGateway 可信上下文。Handler 通过类型化 AttendanceToolPort 调用既有领域 Service；稳定操作键持久化在申请表，重放相同命令返回原结果，不同命令冲突关闭。工具同时要求实时 `attendance:reissue:apply` 业务权限与独立 Agent 权限，L1、本人范围、单写步骤且显式确认；全局与租户写开关仍默认关闭。真实 PostgreSQL 覆盖相同操作仅一条申请及一条审计、命令冲突、审计失败回滚和考勤记录零直接写入；空库与旧库升级、71 个迁移 validate、重复迁移零变更通过。人工发布门、浏览器逐工具和真实 LLM 端到端仍待验收，因此代码具备受控执行能力不等于生产已开放。

P6 通用审批草稿工具切片：新增 `approval.application.createDraft` 封闭契约，动态表单通过类型化字段列表进入 `ApprovalApplicationToolPort`，不向 Port 暴露 Map、JSON、DTO 或框架类型。Adapter 复用页面的 `GenericApprovalService`；领域重新校验实时用户、租户、`route:approval-start`、独立 `approval:create` 权限、有效表单及字段 Schema。稳定操作键持久化并由本人范围唯一索引约束；相同草稿重放返回原结果，参数或状态改变失败关闭。该工具只保存 DRAFT，不创建流程或待办，L1 显式确认、业务幂等、单写步骤，写开关保持默认关闭。OA lint 无错误（3 条既有警告）、91 项测试和构建通过；后端 785 项零失败、9 项既有环境测试跳过。真实 PostgreSQL 空库与旧库升级、72 个迁移 validate、开发库升级至 V202609152020 及二次启动零迁移均通过。P6 的提交、撤回及重新提交仍须按独立里程碑交付。

P6 通用审批草稿提交工具切片：新增 `approval.application.submitDraft` 封闭契约，只接收本人申请 ID 与预期版本。工具复用同一个 `ApprovalApplicationToolPort` 和 Adapter，领域入口与页面提交共用表单完整校验、流程解析、版本快照、实例、首待办、动作流水及事务审计，不复制审批状态机。网关独立工具权限之外，领域再次检查实时 `route:approval-start`、`approval:submit`、本人归属、DRAFT 状态和乐观锁版本。该操作会启动审批流程，因此设为 L1、显式确认、单写步骤和禁止自动重试；未知结果不得盲目重放。OA lint 无错误（3 条既有警告）、91 项测试和构建通过；后端 792 项零失败、9 项既有环境测试跳过。真实 PostgreSQL 空库与旧库升级、73 个迁移 validate、开发库升级至 V202609152100、健康检查及二次启动零迁移均通过。全局与租户写开关继续默认关闭。P6 的撤回及重新提交仍须按独立里程碑交付。

P6 通用审批撤回工具切片：新增 `approval.application.withdraw` 封闭契约，只接收本人申请 ID 与预期版本，并仅绑定到我的申请和 AI 工作空间。工具继续复用 `ApprovalApplicationToolPort`、Adapter 与通用审批领域事务；领域按可信用户和租户重新校验 `route:approval-start`、独立 `approval:withdraw` 权限、本人归属、PENDING 状态及申请/待办/实例乐观锁，原子取消当前待办和流程实例并写动作流水、事务审计及站内通知。工具为 L1、显式确认、单写步骤和禁止自动重试，写开关保持默认关闭。OA lint 无错误（3 条既有警告）、91 项测试和构建通过；后端 797 项零失败、9 项既有环境测试跳过。真实 PostgreSQL 空库与旧库升级、74 个迁移 validate、开发库升级至 V202609152130、健康检查及二次启动零迁移均通过。P6 的重新提交仍须按独立里程碑交付。

P6 通用审批恢复草稿工具切片：新增 `approval.application.reopen` 封闭契约，只接收本人申请 ID 与预期版本，并仅绑定到我的申请和 AI 工作空间。工具复用既有 `ApprovalApplicationToolPort`、Adapter 与通用审批领域状态机，仅将本人 REJECTED/WITHDRAWN 申请恢复为 DRAFT，并保留旧流程与动作历史。领域再次校验实时 `route:approval-start`、独立 `approval:reopen` 权限、租户、本人归属、状态和版本，申请更新、动作流水与审计同事务。为遵守一任务最多一个写步骤，该工具不直接重新提交；修改后需在另一个显式确认任务中调用已交付的草稿提交工具。工具为 L1、显式确认、单写步骤和禁止自动重试，写开关保持默认关闭。OA lint 无错误（3 条既有警告）、91 项测试和构建通过；后端 803 项零失败、9 项既有环境测试跳过。真实 PostgreSQL 空库与旧库升级、75 个迁移 validate、开发库升级至 V202609152200、健康检查、工具契约 Hash 核对及二次启动零迁移均通过。

R5 类型化写 Handler 模板切片：新增 `TypedWriteToolHandler<C, R>`，统一当前单写工具的代码与版本标识、参数解析、调用、输出序列化和稳定操作键生成；新增 `TypedVersionedWriteToolHandler<R>` 复用单资源 ID 与乐观锁版本命令。现有 12 个写 Handler 全部迁移到模板，仍保留类型化 Port、专属参数解析和领域二次鉴权，不引入 Map、框架 DTO 或远程协议。架构门禁禁止后续具体 Handler 绕开类型化读写模板直接实现执行入口；该切片不改变工具契约、数据库、运行开关或业务状态机。29 项定向测试、OA lint（3 条既有警告）、91 项测试与构建通过；后端 808 项零失败、9 项既有环境测试跳过。

R5 工具契约与封闭 Schema 切片：新增 `ToolDefinitionFactory`，固定受控只读和单一原子写的代码级安全配置；新增 `ClosedToolSchemas`，确定性生成单资源 ID 及资源 ID + 乐观锁版本参数。现有 42 个工具定义全部改由安全工厂创建，业务权限、归属、风险、确认、重试和资源上限仍逐工具显式声明。复用片段保持既有 JSON 节点和 Schema Hash 不变，冻结 Hash、工厂安全配置和非法属性名测试失败关闭；不新增迁移、依赖、公共执行接口或动态注册能力。38 项定向契约测试、OA lint（3 条既有警告）、91 项测试与生产构建通过；后端 812 项零失败、9 项既有环境测试跳过。

R6 页面能力目录收口切片：新增纯 Java `OaPage` 作为导航、权限和 Agent 共同依赖的中立页面契约，删除独立维护的导航组件白名单和 DTO 组件枚举列表；`PageCapabilityCatalog` 只在该契约上附加 UI 指令、页面上下文和工具绑定，领域导航不反向依赖 Agent 包。启用页面必须精确命中 `routeKey + componentKey`，数据库、租户配置和管理接口不能把专属组件挂到任意新路由；历史页面别名只用于输入兼容，不能重新启用为正式菜单。架构门禁保证共享页面契约不依赖 Spring、Agent 或领域实现；前端固定组件注册表仍作为语言边界内的安全渲染白名单，跨端一致性由后续门禁覆盖。本切片不改变工具定义、页面权限、数据库数据和迁移。34 项页面与路由定向测试通过；OA lint 无错误（3 条既有警告）、92 项测试与生产构建通过；后端 827 项零失败、9 项既有环境测试跳过。

权限、租户、安全配置、审计、运行日志和接口配置页面保持受控只读。每页面有工具不等于每页面可写；永久禁止能力及人工发布门不因本计划改变。

每个里程碑验证后独立中文提交到 feature/zcc；较大业务节点每个原子工具一个提交。仅暂存节点相关文件，不使用 git add .。文档更新也遵守既定前后端全量门槛；数据库变更另执行真实 PostgreSQL 迁移及并发验收。

## 每工具证据要求

- 固定输入输出 Schema、版本哈希、独立权限、作用域、结果上限、风险和确认策略。
- 架构门禁证明 Handler 不依赖 Mapper，其他 Agent 组件不绕过 Gateway。
- 参数缺失、未知字段、越权、跨租户、权限回收、非法状态、重复请求和版本冲突测试。
- 写工具增加真实 PostgreSQL 并发、审计回滚和结果核验测试。
- 前端 lint、test、build 与后端 mvn test 全量通过；数据库变更额外执行空库、旧库升级、Flyway validate 和开发库二次启动。
- 页面加载、错误、空态、权限变化和桌面/移动端验收分别记录；没有浏览器证据不得标记已验收。
- 仅暂存本节点相关文件，中文提交到 feature/zcc；回滚优先关闭单工具，保留已发布迁移及业务数据。
