# 全页面只读工具真实执行验收

本节点将真实 Worker/Gateway 执行证据扩展到全部 41 个只读工具，以及当前数据库中 42 个启用 PAGE 的来源绑定。
目录仍为 140 个工具（41 读、99 写），没有新增工具，也没有开启产品、租户或全局写开关。
本节点不是全部写工具、全部页面浏览器交互或全部工具真实模型选择的完成声明。

## 执行矩阵

`AgentReadCatalogIntegrationTest` 使用[独立数据库及 Redis 所有者夹具](agent-persistence-integration-isolation.md)。
每个测试创建独立合成用户及角色，只授予当前工具的业务权限、独立 Agent 权限、声明页面及固定领域前置路由。
冻结排队计划使用生产 Hash；真实 Worker 领取后，只有 ToolGateway 可以调用 Handler。
Handler、类型化 Port/Adapter、领域 Service、PostgreSQL 查询、结果持久化、任务详情、事件与审计均为真实实现。
计划由测试夹具构造，未调用模型规划。

| 工具范围 | 真实数据或结果要求 |
| --- | --- |
| 待办、审批待办、本人请假、通知 | 合成申请/流程待办或本人通知记录 |
| 本人权限、员工档案、组织、员工变动 | 当前合成用户权限、档案、部门或变动记录 |
| 考勤 | 记录查询；页面入口另覆盖今日、异常、补卡申请、统计及设置 |
| 审批配置、表单定义、规则定义 | 合成停用表单或有条件规则，保持原业务校验 |
| 通用申请列表/详情、费用 | 合成本人草稿，不触发业务提交 |
| 资产、会议室、预约、访客、印章 | 合成业务记录；访客允许未填写预计离场时间 |
| 预算、合同、供应商 | 当前租户合成记录 |
| 接口配置、运行日志、沙箱回放 | 合成登记/调用/回放记录，不向外部系统发送请求 |
| 知识库、文档 | 合成元数据；文档处于 PROCESSING，无文件上传或索引任务 |
| 知识检索 | 没有 READY 文档时的真实空结果；不证明 Embedding 或召回能力 |
| 字典、字典项、业务审计 | 合成记录及已授权的脱敏投影 |
| 页面操作、角色/数据/AI 权限、租户配置、系统能力 | 真实管理元数据和安全摘要；系统能力不要求未启动的可选服务在线 |
| 本人 Agent 任务、驾驶舱、平台观测 | 真实任务与聚合；部分聚合只验证契约与结构，不宣称每项业务均有非零数据 |

41 个工具分别执行成功、排队后撤销独立工具权限、夹带 `tenantId` 参数三个场景。
拒绝场景断言无 Handler 调用、无结果、具有拒绝审计；成功场景核对结果证据、可读摘要、一次成功调用、完成事件和租约释放。
另从数据库查询全部 42 个启用 PAGE，核对代码组件和至少一个已绑定读工具，再从各页面执行其工具。
页面覆盖证明源页面绑定与真实执行，不等于浏览器逐页所有操作已验收。

## 修复内容

1. 数据范围查询区分“不存在/失效用户”与“有效但未分配部门的用户”。后者的 SELF、DEPARTMENT、DEPARTMENT_TREE 不会失败，也不会把其他未分配部门用户纳入范围。
   标准 `DEPARTMENT_TREE` 和历史 `DEPARTMENT_AND_CHILDREN` 都保持同租户后代部门查询；无用户仍失败关闭。
   数据权限工具的 `DEPARTMENT_TREE` 筛选映射到领域历史名称后查询，不再把存在的树范围策略误报为空。
2. `approval-form` 路由改为规范的 `route:approval-form`。迁移按该路由原权限的实际角色受众复制授权，仅更新新增授权所影响用户的权限版本，避免把所有发起审批角色误授予新权限。
3. `visitor.query` 输出契约不再强制领域允许为空的 `expectedLeaveAt`，其字段定义、其他必填项、权限及预算不变。
4. 通用申请读工具保留领域原有 `route:approval-start` 前置要求，通过代码页面能力目录显式声明、规划与执行共用。
   路由权限不混入 ToolDefinition 的业务权限层，也不弱化领域鉴权。
5. 角色、数据范围与 AI 权限概览在筛选后稳定排序，每个数组最多返回 50 项。
   分别增加 `roleCount/rolesTruncated`、`policyCount/policiesTruncated`、`toolCount/roleCount/toolsTruncated/rolesTruncated`。
   总数是筛选后的总数，截断会在最终中英文摘要中明确提示；未暴露用户身份、部门 ID、内部授权行或密钥。
6. 考勤个人统计的领域响应允许没有团队列表，Adapter 将其投影为空团队而非报错；
   补卡和设置的读结果契约接受领域已有的乐观锁版本字段，仍不暴露用户 ID 或打卡 IP。
7. 浏览器回归复现纯对话追问误入业务计划接口。意图提示词升级至 `oa-intent-v2`，
   明确对话内复述与真实业务/持久写入的区别，混合请求仍走受控链路。
   不增加关键词硬路由、额外历史输入或失败转聊天的回退；分类本身仍不创建任务或授予权限。
   新增 6 条真实模型 HTTP 分类场景，连同既有两个实际工具选择场景显式运行。

## 迁移与部署

新增 V202610260915–V202610260920；不修改已有迁移或冻结计划。
0917 曾在本轮临时数据库执行过，因此保留原文件，并由 0918 撤销错误的“路由权限混入业务权限层”尝试。
它们按顺序执行后恢复正确分层，不开启任何工具或写开关。
0916、0919 和 0920 仅更新匹配原版本/原 Hash 的工具契约；自定义或未知契约不自动覆盖，Registry 将继续失败关闭。

部署前停止旧 Worker，完成新代码及迁移后再恢复受控执行。受访客、考勤或三个治理概览契约变化影响的旧计划必须重新生成，不能改写计划 Hash 后继续执行。
迁移验收在全新独立数据库完成，并只在该库恢复旧契约及迁移记录来验证增量升级。
角色直接归属、附加角色、旧路由受众、其他租户及重复迁移均须校验，不能在开发库删除 history 模拟旧库。

本地开发库校验入口 `AgentReadCatalogDevelopmentVerifier` 需显式 `AGENT_DEVELOPMENT_VERIFY=true`，只接受精确的本机开发库地址。
它以真实 Spring Boot 应用启动两次，使用随机端口，关闭所有定时任务和 Agent 执行，并使用自有空 Redis，避免消费用户通知队列。
组件扫描显式排除测试输出目录，避免脱离 Spring Test 启动时加载其他测试配置；仅显式注册维护配置。
检查 Flyway validate、零 Pending、再次 migrate 零执行、路由权限、代码/数据库工具 Hash、所有发布开关保持原值；退出后释放自有资源。
该模式是维护验证进程，不是对用户正在运行的服务执行热更新，也不验证开发 Redis 在线情况。

## 验证命令

Java 17、Maven、本地 PostgreSQL/pgvector 与原生 Redis；凭据仅通过本地环境变量提供，不写入文档或命令行参数。

```powershell
cd backend
mvn '-Dtest=AgentReadCatalogIntegrationTest,AgentReadCatalogMigrationIntegrationTest,SecurityGovernanceAgentDomainToolAdapterTest,AgentReadResultPresenterTest,DataPermissionServiceImplTest,PageCapabilityCatalogTest,AgentReadToolDefinitionsTest,AgentSecurityGovernanceToolDefinitionsTest' test
mvn clean test
```

运行前显式配置 `AGENT_TEST_DB_URL`、`AGENT_TEST_DB_USERNAME`、`AGENT_TEST_DB_PASSWORD` 和 `AGENT_TEST_REDIS_BINARY`。
必须核对两个新增集成类实际执行且零跳过，不能仅凭构建通过认定执行了真实数据库验证。
OA 继续执行 lint、test、build；既有真实模型 HTTP/浏览器场景作为连续会话回归，其三种查询证据不能冒充全部 41 工具模型验收。

显式维护验证在上述测试生成报告后执行；预先配置的地址必须是该维护类校验的精确本机开发库，不能用于远端或生产库。
以下命令在 backend 目录执行，不把凭据放进 JVM 参数：

```powershell
$env:AGENT_DEVELOPMENT_VERIFY='true'
try {
    [xml]$verificationSuite=Get-Content target/surefire-reports/TEST-com.aiworkmate.integration.AgentReadCatalogIntegrationTest.xml -Raw
    $verificationClasspath=($verificationSuite.testsuite.properties.property | Where-Object { $_.name -eq 'java.class.path' }).value
    & "$env:JAVA_HOME/bin/java.exe" -cp $verificationClasspath com.aiworkmate.integration.AgentReadCatalogDevelopmentVerifier
    if ($LASTEXITCODE -ne 0) { throw 'Development verification failed' }
} finally { Remove-Item Env:AGENT_DEVELOPMENT_VERIFY }
```

## 2026-10-26 验证记录

- 最终 Java 17 `mvn clean test`：1709 项，零失败、零错误；新增目录集成 167 项、迁移集成 1 项及既有真实读执行 8 项全部零跳过。
  2 项既有环境跳过分别是缺少 Docker 的 `InitSqlIntegrationTest` 和未配置专属观测数据库的 `RuntimeLogMapperPostgresIntegrationTest`。
- 最终 OA：55 个文件、453 项通过；lint 零错误、3 条既有 Hook 警告；生产构建通过，保留既有分包体积提示。
- 显式真实模型 HTTP：8 项、零失败、零错误、零跳过，包含 6 条分类场景及 2 个实际工具选择/读取场景；
  元数据只输出耗时、响应长度和 JSON 形态，不输出模型配置或响应正文。
- 真实浏览器：1 项零跳过通过。小窗实际待办、自动跳页、输入清空、切页保留、连续聊天上下文、
  实际通知/权限码、刷新重开混合会话，以及任务中心实际取消均通过。
  数据库核对仍为 10 条成功消息、3 个成功读取任务和 1 个取消夹具，普通聊天没有额外创建业务任务。
- 本机开发库为 168 条成功版本记录，最新 `202610260920`；使用当前代码完成两次真实应用启动，
  Flyway validate、零 Pending、再次 migrate 零执行及工具/租户发布开关未变化均通过。
- 最终自有临时数据库、Redis 子进程与浏览器驱动计数均为零；42 条启用 PAGE 的规范权限不匹配数为零。
- 独立只读审查发现的树范围筛选与中文标签问题已修复并复核；无剩余静态 Critical/Important。
  曾出现全量上下文缺 Bean 失败，冻结源文件后完整干净重跑通过；没有关闭测试或移除断言。
  浏览器曾复现纯聊天追问误分类，经 v2 修复后以真实模型 HTTP 和浏览器重新验证，没有将失败当作成功。

以上证据不覆盖 99 个写工具的完整事务与发布验收、每页全部浏览器操作、全部工具模型选择或 Embedding 召回。

## 回滚与剩余工作

先关闭受影响工具或执行能力，再回滚代码；保留 Flyway history 与迁移文件，不删除已执行版本。
旧代码不兼容新 Hash，应保持失败关闭，必要时以新的前向迁移恢复已审核契约，不能手改冻结任务。
用户未分配部门时的数据范围修复涉及正常页面共用服务，回滚代码会恢复原查询故障。
接下来仍需逐领域验证写工具真实事务、人工发布门、确认及业务状态/版本/幂等失败路径，并逐页补浏览器交互和模型意图验收。
