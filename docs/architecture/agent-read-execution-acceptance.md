# Agent 真实只读执行链与页面策略复核

## 问题与修复

旧链路在规划时读取页面操作策略，但执行时只校验代码页面能力、实时 RBAC 和平台/租户工具策略。
因此，页面操作在任务排队后被管理员禁用，旧任务仍能调用 Handler。
真实 PostgreSQL、Redis 和 Worker 回归已复现：预期拒绝的任务实际 `SUCCEEDED`，其余七个场景通过。

`DefaultToolGateway` 现在每次执行都复用 `PageActionPolicyResolver`，在实时身份校验之后、预算抢占和 Handler 调用之前，
重新读取冻结任务的租户与源页面策略。关闭时审计 `PAGE_ACTION_DISABLED`；读取异常时审计 `PAGE_POLICY_UNAVAILABLE` 并失败关闭。
两种情况都不调用 Handler，不消耗工具调用预算；拒绝仍须先写审计，审计异常也不能继续。
对外沿用既有错误码，不返回内部策略细节。

策略含义与规划端一致：它收紧该源页面的工具绑定，不代替平台或租户的工具 Kill Switch。
历史页面别名仍由同一解析器规范化；冻结源页面取自服务端任务快照，不能由工具参数覆盖。
本次未新增 API、DTO、工具、Schema Hash 或 Flyway 迁移，未开启产品 Agent 或写能力。
工具目录仍为 140 个（41 读、99 写）。

## 真实执行验证

新增 `AgentReadExecutionIntegrationTest`，复用[持久化测试隔离](agent-persistence-integration-isolation.md)。
仅在独立临时数据库及 Redis 中启用 L0 执行，规划与所有写开关保持关闭。
测试插入带生产 Hash 和真实工具版本的冻结排队计划，再由真实 `AgentTaskWorker.poll()` 领取任务。
Gateway、Handler、Port/Adapter、领域 Service、数据库查询、任务状态、事件和审计都使用实际实现。
没有模型、Handler 或领域服务的模拟替代。

| 场景 | 要求 |
| --- | --- |
| 查询本人通知 | 只返回本人、当前租户的数据；过滤同租户其他人及异租户同用户的记录 |
| 通知结果交付 | 真实任务详情 Service 返回结果；保存成功审计和事件，完成后清空 Worker 租约；其他用户不可读取任务 |
| 查询本人待办 | 真实请假单及流程待办返回实际申请 ID、类型和状态；其他处理人的待办不出现 |
| 排队后撤销业务权限 | Gateway 拒绝，不调用 Handler，无执行结果 |
| 排队后关闭页面操作 | Gateway 拒绝，保存页面策略拒绝审计 |
| 排队后用户更换租户 | 旧租户任务不能继续读取 |
| 持久参数篡改 | 旧 Hash 不再匹配，拒绝调用 |
| 参数夹带身份字段 | 即便重算冻结 Hash，封闭 Schema 仍拒绝身份扩展字段 |
| 平台工具关闭 | 排队后关闭仍生效，不能依靠旧计划继续执行 |

另外两个 Gateway 单测覆盖页面策略关闭和策略读取异常，均断言预算尚未抢占、Handler 未调用。
测试中的排队计划由夹具构造，因此不证明真实模型规划、HTTP 执行入口、JWT Cookie、SSE 浏览器消费或人工确认交互。
该证据覆盖两个真实读工具，不能代替其余页面工具、写流程和全页面浏览器验收。

## 执行命令

Java 17、本机 PostgreSQL + pgvector、显式配置的原生 Redis 可执行文件及测试账号要求见隔离文档。
凭据仅放本地测试环境变量，不写入命令记录或仓库。

```powershell
cd backend
mvn '-Dtest=AgentReadExecutionIntegrationTest,DefaultToolGatewayPolicyTest,ToolGatewayContractTest' test
```

显式验收必须使用 `AGENT_TEST_DB_URL`、`AGENT_TEST_DB_USERNAME`、`AGENT_TEST_DB_PASSWORD` 和 `AGENT_TEST_REDIS_BINARY`，
确认新集成类实际执行且零跳过；全量门槛仍为 OA lint、test、build 和后端干净测试。

## 2026-10-25 验证记录

- 修复前完整集成测试 8 项中仅页面策略撤销场景失败：预期拒绝，实际任务成功。
- 修复后定向 38 项零失败、零错误、零跳过：真实执行 8 项、Gateway 策略 26 项、契约 4 项。
- 最终 Java 17 `mvn clean test`：1477 项零失败、零错误；上述真实执行 8 项再次全部执行通过。
  2 项既有环境测试跳过：初始化测试缺少 Docker、运行日志聚合测试缺少 `OBSERVABILITY_TEST_DB_URL`。
- OA 54 个文件 415 项通过；lint 零错误（3 条既有 Hook 警告），构建通过并保留既有分包体积警告。
- 独立只读审查无 Critical/Important 阻断项；本次没有其他页面、HTTP/JWT/SSE、真实模型及写流程的验收证据。
- 最终临时数据库计数和 Redis 子进程计数均为零；开发库仍为 162 条迁移、版本 `202610242252`。
  13:16 本机开发环境 6379 未监听，本次测试自行使用临时 Redis，不据此宣称开发运行环境已经恢复。

## 回滚

无结构或种子变化，不需要回退数据库版本。回滚前应关闭受影响工具或执行能力，避免旧任务重新进入缺少页面策略复核的版本。
仅回滚代码不会撤销已完成的业务操作；本次集成夹具资源由测试所有者清理，不触碰开发库业务表与用户 Redis。
