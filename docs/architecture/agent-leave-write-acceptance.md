# 请假工具受控确认与真实写入验收

本节点验证请假领域六个既有写工具，继续保留全页面受控读写的完整目标。
工具目录没有新增条目，仍为 140 个（41 读、99 写）。本节点不是生产写发布，也不代表其余写工具或全页面浏览器操作已验收。

## 六个工具与业务回执

| 工具 | 风险/确认 | 成功业务状态 | 限制 |
| --- | --- | --- | --- |
| leave.createDraft | L1 / 显式确认 | DRAFT | 只保存草稿，不启动审批 |
| leave.updateDraft | L1 / 显式确认 | DRAFT | 本人、当前租户、草稿状态及版本 |
| leave.submit | L2 / 二次确认 | PENDING | 仅提交规划前已存在的本人草稿 |
| leave.apply | L2 / 二次确认 | PENDING | 创建与提交为同一原子领域命令，单个 Agent 步骤 |
| leave.withdraw | L1 / 显式确认 | WITHDRAWN | 取消当前待办和原流程，保留历史 |
| leave.remind | L1 / 显式确认 | PENDING | 保持待审批，更新催办次数/时间并限制频率 |

共享结果 Presenter 根据真实回执生成中英文业务文字，返回可复制的申请 ID、版本，以及原子申请的审批待办 ID。
草稿明确说明尚未提交，申请明确说明等待审批；催办不表述为已审批。
缺少 ID、版本、预期状态或原子申请的审批待办 ID 时不能生成成功文案。
结构化结果及既有 API 类型不变，不把模型规划摘要当作执行结果。

## 发现与修复

1. 原催办工具仅声明 `leave:remind`，领域调用还要求 `leave:read:self`，最小授权用户执行时被领域拒绝。
   代码工具契约与新迁移 `V202610261500` 追加已有读取要求，只收窄可用集合，不给角色授予权限，不启用工具。
   输入/输出 Schema 和 Hash 不变；旧计划在确认、入队及执行时仍重新检查当前权限。
2. 草稿编辑和催办的成功业务审计原使用独立事务。真实 PostgreSQL 事务 ID 探针分别复现业务与审计提交于两个事务。
   两处改用 `recordTransactional`，与业务事务一同提交或回滚；失败/拒绝审计继续保留原独立记录行为。
3. 本人写资源改由 `selectOwnedApplication(tenantId, applicantUserId, id)` 查询，
   不再先按全局 ID 加载再在内存判断。当前调用人来自实时认证上下文，SQL 同时限制租户和申请人；
   不存在、他人及其他租户资源统一返回未找到，拒绝仍写审计。
4. 完整回归复现了提交回调唤醒 Worker 时的领取竞态。原 `mapper.claim` 复用已提交但未清理的事务连接，
   异步执行开始时领取尚未提交，任务可能停留在 RUNNING 而步骤仍待执行。
   新真实回归在原 afterCommit 回调未退出时通过独立连接观测，旧代码稳定返回 QUEUED。
   领取改由 `AgentWorkerTransitionService.claim` 的 REQUIRES_NEW 代理事务提交，返回后才分派 Worker。
   领取 SQL、租约、执行预算、重试和开关不变，没有通过加大超时或静默重试处理竞态。

没有增加绕过 ToolGateway 的入口、通用业务执行接口、任意 SQL 工具或动态 Handler。
测试中的数据库预置、故障触发器和事务探针是开发夹具，只存在于自有临时数据库，不能被 Agent 调用或部署到开发库。

## 原生真实链路

`AgentLeaveWriteIntegrationTest` 复用[测试资源隔离](agent-persistence-integration-isolation.md)：
先验证 JDBC 和 Redis 绑定到本次拥有的独立资源，再仅在该测试上下文打开写运行能力。
随机回环端口、独立 JWT 密钥、合成角色及真实密码登录 Cookie，不修改开发环境变量、产品配置或开发库写开关。

`FrozenWriteTaskFixture` 只预置带生产 Hash 的单写计划，初始状态必须为 WAITING_CONFIRMATION。
它不生成确认凭证、不直接入队、不调用 Handler，也不证明真实模型规划。
确认签发和消费、幂等绑定、Worker、Gateway、领域 Service、数据库更新、事件、业务审计和调用审计均用实际实现。
有效审批人预置复用独立的 `LeaveWriteFixture`，保留领域的人员有效性、角色和直属关系要求。

覆盖场景：

- 六个写工具的真实回执、申请状态和业务审计；创建/编辑的假种、日期、时段、原因及时长。
- 六种成功写的业务与审计事务 ID 一致，探针同时观察业务和审计两种事件。
- 未确认、错误/过期确认、过时计划、串计划令牌、确认重放及相同幂等键并发执行。
- 工具权限撤销、来源路由撤销及确认后关闭写开关，无额外业务写入。
- 他人或其他租户不能确认/执行任务，也不能更新他人或其他租户的申请。
- 草稿版本冲突、缺少审批人和高频催办不会产生额外业务效果。
- Gateway 前置审计不可用时，Handler 不执行、业务写入为零。
- 领域事务审计故障时申请与流程回滚，网关保守记录结果不确定，不自动重复写入。
- 业务已提交后调用审计故障时，任务明确标为结果不确定；再次请求同一幂等操作不会创建第二份申请。
- 提交回调内的领取状态必须对独立连接可见，不能在领取事务未提交时开始异步执行。

结果不确定不能解释为“没有写入”。应先查询有权查看的业务记录或由授权人员核对，禁止据此盲目重放。
故障注入测试通过数据库触发器观察真实事务，没有用模拟 Handler 伪造提交成功。

## 真实模型一句话验证

`AgentLiveLeaveApplyIT` 是显式外部模型验收，不进入默认离线测试扫描。
输入一个包含具体日期、上午到下午、事假和随机原因的中文句子，要求使用系统配置的审批人。
首先经真实意图接口识别为 OPERATION，且分类本身不创建任务。
规划时真实模型只获得当前合成用户有权使用的工具，生成一个 L2 `leave.apply` 步骤：

1. 规划后没有业务写入；未确认 execute 返回 409。
2. 通过实际 confirmation-token 接口签发凭证，再调用实际 execute 接口。
3. 核对计划参数与实际申请的假种、两端日期、时段、原因、审批人、时长、状态。
4. 按真实回执的 applicationId 和 approvalTaskId 关联核对唯一申请、流程、待办及 AGENT_APPLY 业务审计。
5. 结果显示已提交且等待审批，没有把申请当作自动审批通过。

这证明实际模型 HTTP 链路；确认由测试调用者模拟用户请求，不证明浏览器中的人工点击体验或独立人工发布签字。
测试角色仅用于自有临时资源，不能作为产品写权限或生产发布证据。

## 执行与迁移

Java 17、Maven、本地 PostgreSQL/pgvector 及原生 Redis；测试凭据仅通过本机环境变量提供。

```powershell
cd backend
mvn '-Dtest=AgentLeaveWriteIntegrationTest,AgentLeaveWriteResultPresenterTest,LeaveWorkflowServiceImplTest,AgentLeaveWithdrawalToolDefinitionsTest,AgentReadCatalogMigrationIntegrationTest' test
mvn clean test
cd ..
./scripts/verify-agent-live-model.ps1 -EnableLiveModel -LeaveWrite `
  -DatabaseUrl $env:AGENT_TEST_DB_URL `
  -DatabaseUsername $env:AGENT_TEST_DB_USERNAME `
  -RedisExecutable $env:AGENT_TEST_REDIS_BINARY
```

`-LeaveWrite` 与 `-Browser` 互斥，脚本只能选择固定的验收类，默认仍执行已有只读模型场景。
模型配置只在服务端读取，脚本不会回显密钥、地址、Cookie 或确认凭证。
必须核对实际集成类执行且零跳过，再执行 OA lint、test、build。

新增权限元数据迁移保留所有旧迁移与发布开关。旧契约升级验证只在自有临时库恢复变更契约并重放其后版本集合，
避免在已存在后续版本时将旧迁移误判为 Ignored；生产 Flyway 不开启 out-of-order。
本地开发库使用已有显式维护验证器升级、validate 和两次应用启动，定时任务与 Agent 执行关闭，通知使用自有空 Redis。

## 2026-10-26 验证记录

- 定向契约、领域、回执、迁移及原生写测试 68 项零失败、零错误、零跳过。
  领取竞态修复后，Worker、真实 Cookie/HTTP、读执行及请假写整组 51 项再次零跳过通过。
- 最终 Java 17 `mvn clean test`：1740 项零失败、零错误；原生请假写 22 项、读目录/页面来源 167 项、
  旧契约升级 1 项及包含提交可见性用例的读执行 9 项均实际运行。
  2 项既有环境依赖跳过为缺少 Docker 的 `InitSqlIntegrationTest` 和未配置专属观测数据库的 `RuntimeLogMapperPostgresIntegrationTest`。
- 最终 OA 55 个文件、453 项通过，lint 零错误（3 条既有 Hook 警告），构建通过并保留既有分包体积提示。
- 当前代码显式真实模型 L2 HTTP 验收 1 项零失败、零错误、零跳过；同时验证真实意图分类、
  单步骤原子规划、缺确认拒绝、实际确认、精确业务字段、回执关联及成功结果文字。
- 本机开发库升级到 169 条成功版本记录，最新 `202610261500`。当前代码两次真实应用启动，
  validate、零 Pending、重复 migrate 零执行、催办权限契约及所有发布开关未变化均通过。
  自有临时数据库、Redis 子进程和浏览器驱动计数均为零。
- 独立只读审查无剩余阻断；审查指出的实际日期/假种及回执关联缺口已补齐并真实重跑。
  领取可见性问题有稳定 RED→GREEN 证据，不通过放宽时间预算或删除失败断言处理。

以上仅覆盖六个请假写工具的隔离验收及共享领取修复；没有浏览器写确认或人工发布签字证据。

## 发布边界与回滚

仍需按[Phase 2 安全发布门](../roadmap/phase-2-agent-security-boundary.md)完成人工签字；代码生成 Agent 不批准生产写能力。
本节点没有把普通用户、角色管理员或租户管理员提升为无条件写执行者，确认也不产生新权限。
其余写工具、逐页写操作浏览器验收，以及所有工具的实际模型意图覆盖继续推进。

回滚前先关闭受影响工具或执行能力，保留 Flyway history 和已有业务数据，不降低权限要求来兼容旧计划。
回滚事务审计代码会恢复原一致性缺陷；已完成的业务提交不会随代码回滚撤销。
