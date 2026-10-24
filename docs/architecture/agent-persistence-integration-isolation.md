# Agent 真实持久化测试隔离

## 范围

本切片为既有五组 Agent 持久化测试建立真实 PostgreSQL 与 Redis 验证环境：

| 测试类 | 验证内容 |
| --- | --- |
| `GatewayPersistenceIntegrationTest` | 快照重载、预算抢占、追加审计和 Worker 任务领取 |
| `AgentPlanningPersistenceIntegrationTest` | 规划状态的版本条件更新 |
| `AgentWorkerMapperPersistenceIntegrationTest` | 步骤完成、未知写结果与租约更新的真实 SQL |
| `AgentTaskApiPersistenceIntegrationTest` | 确认令牌单次消费、所有权、取消、过期与事件查询 |
| `AgentRetentionPersistenceIntegrationTest` | 审计所有权以及历史清理保留活跃和近期任务 |

这些测试不调用真实模型，不证明完整 Worker 经 Gateway 调用领域 Handler，更不代表全页面读写验收完成。
工具目录仍为 140 个（41 读、99 写），本次没有新增工具、迁移或产品接口。
`P1PostgresMigrationIT` 不在本切片内，其 Schema 隔离也需后续提升为独立数据库，完成前不应指向已有业务库执行。

## 资源边界

`AgentIntegrationEnvironment` 是测试源码中的资源所有者。每类生成一个随机 `agent_it_<32位小写十六进制>` 名称，
以 `template0` 创建独立临时数据库，再由 Flyway 在其中迁移同名 Schema。
控制连接只用于检查、创建和删除本次数据库；所有迁移与业务 SQL 都连接新库。
测试执行前验证实际 `current_database()` 和 `current_schema()`，不符立即失败。

不能只在开发库内新增 Schema：历史基线包含 `public` 检查和未限定 Schema 的旧对象操作，
Flyway 的搜索路径可能保留原库对象。独立数据库消除该风险，且无需修改历史迁移。
测试账号需要本地 `CREATEDB` 权限及创建 `vector` 扩展的权限，PostgreSQL 主机必须已安装 pgvector。

Redis 使用显式提供的本地可执行文件和随机回环端口，关闭持久化。
启动探测同时核对 `PING` 与 `INFO server` 的进程 ID；应用连接也须匹配所持有的子进程。
不连接既有 6379 队列，不执行 `FLUSHDB`、`FLUSHALL`，不停止用户的 Redis。
运行日志仅放在忽略的 `backend/target/agent-it/`，不提交。

全局 Agent、规划、执行及写开关在测试上下文强制关闭，避免后台 Worker 抢占手工测试任务。
定时历史清理、附件清理及员工变动应用关闭；只有保留策略测试显式开启自身清理器并手动调用。
产品数据库及环境配置中的开关不变。

## 生命周期

共享 `ResourceListener` 在依赖注入前保存资源所有权，因此上下文启动失败也能清理。
测试结束时先由 Spring 的 `DirtiesContext` 关闭消费者和连接池，再停止所持有的 Redis 子进程、删除自建数据库。
删除不使用 `FORCE`，存在未关闭连接时必须显式报错；资源名和所有权同时校验。
清理失败保留 JVM 退出钩子及重试能力，不把失败标记为已关闭。
未提供测试环境变量时仍由既有条件明确跳过，构造测试资源不会连接数据库或启动进程。

## 执行

使用 Java 17 与 Maven，在 `backend` 运行。凭据仅在本地环境变量配置，不写入文档或 Git：

```powershell
# AGENT_TEST_DB_URL：本机 PostgreSQL 控制库的 JDBC URL，不含用户名、密码或 query 参数。
# AGENT_TEST_DB_USERNAME / AGENT_TEST_DB_PASSWORD：本地测试账号。
# AGENT_TEST_REDIS_BINARY：现有 redis-server 可执行文件的绝对路径。
mvn '-Dtest=AgentIntegrationEnvironmentTest,IsolatedAgentResourceListenerTest,GatewayPersistenceIntegrationTest,AgentPlanningPersistenceIntegrationTest,AgentWorkerMapperPersistenceIntegrationTest,AgentTaskApiPersistenceIntegrationTest,AgentRetentionPersistenceIntegrationTest' test
```

显式验收必须确认零跳过，并在结束后检查本次临时数据库和 Redis 子进程均已退出。
未清理的资源由失败日志中的精确名称定位，禁止批量删除所有同前缀数据库。

## 2026-10-25 验证记录

- 定向七个测试类共 15 项通过，零失败、零错误、零跳过；五类持久化测试均实际执行。
- Java 17 `mvn clean test`：1467 项，零失败、零错误，2 项既有环境依赖跳过。
  `InitSqlIntegrationTest` 缺少 Docker；`RuntimeLogMapperPostgresIntegrationTest` 缺少 `OBSERVABILITY_TEST_DB_URL`。
  两项均不属于本次五类 Agent 持久化测试，未据此宣称所有数据库门禁完成。
- OA `npm run test`：54 个文件、415 项通过；`npm run lint` 零错误（3 条既有 Hook 警告）；
  `npm run build` 通过，保留既有分包体积警告。
- 定向测试和全量测试结束后，自建数据库与开发库内同前缀 Schema 均为零；仅用户原 Redis 进程存活，6379 PING 正常。
- 只读审查发现的 Spring 清理顺序、空实例、失败清理重试与迁移搜索路径问题均已修正。
  没有新增产品 API、开关或 Flyway 迁移，开发库仍为 162 条记录、版本 `202610242252`。

## 回滚

回滚本次测试与文档提交即可，不涉及已发布迁移和业务数据；不改变 Agent 人工发布门。
本切片不修改产品的通知投递语义，也不修复应用关闭期间的既有 Redis 连接警告。
