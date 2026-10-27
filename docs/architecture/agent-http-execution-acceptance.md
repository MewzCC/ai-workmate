# Agent 真实会话、执行入队与 SSE 验收

## 问题与修复

上一节点已保证 Gateway 执行时复核页面策略，但执行入口仍只复核业务权限和工具配置。
真实 HTTP 回归复现：源页面操作已关闭，`POST /api/ai/tasks/{taskId}/execute` 仍返回 202，随后才由 Worker 拒绝。
这不会绕过 Gateway 执行业务操作，但会把不可执行的请求错误地接收入队。

`AgentTaskApiService.requireCurrentToolAccess` 现在统一复用 `PageActionPolicyResolver`，
在执行入队、确认令牌签发与消费前读取冻结源页面的当前策略。
关闭返回既有 403 权限错误，读取异常或无结果返回既有 503 能力不可用错误，均不继续签发、消费或入队。
页面能力、实时用户权限、工具版本及 Schema Hash 校验继续保留，Gateway 的最终复核不变。
不新增公开接口、DTO、权限码、工具、迁移或产品开关；目录仍为 140 个（41 读、99 写）。

## 验证边界

新增 `AgentHttpExecutionIntegrationTest`，在随机本机回环端口启动真实 Web 服务，使用[隔离数据库与 Redis](agent-persistence-integration-isolation.md)。
测试独立生成 JWT 签名密钥，使用不同于产品的固定 Cookie 名 `oa_http_it`，不复用产品会话。
只为本机明文 HTTP 设置测试 Cookie Secure=false；这不是对浏览器或生产 Cookie 安全策略的修改。
用户密码随机生成，数据库保存 BCrypt Hash，通过真实密码登录接口取得 HttpOnly、SameSite=Strict Cookie。
Java HTTP 客户端每次手工携带 Cookie，不使用伪造 Principal、MockMvc 或模拟 Handler/领域 Service。

只有独立测试上下文启用只读执行，规划与全部写开关继续关闭，模型配置显式设为未配置，避免意外外呼。
共享 `FrozenReadTaskFixture` 只接受代码注册的 L0/NONE 定义，使用生产 Hash 构造冻结计划，支持 PLAN_READY 与 QUEUED 两种测试状态。
原有 Worker 回归也复用该夹具，减少哈希、版本和持久化字段的重复维护。
夹具不是生产规划路径，因此本节点不证明真实模型规划、一句话聊天、浏览器行为或写工具确认流程。

| 场景 | 证据 |
| --- | --- |
| 登录、执行与结果 | Cookie 登录后经真实 Controller 入队，Worker 经 Gateway 查询本人通知，HTTP 详情返回实际内容及成功审计 |
| SSE 实时推送 | 执行前打开订阅，收到步骤开始、步骤完成、任务完成事件，事件 ID 严格递增 |
| SSE 断点续传 | 使用 Last-Event-ID 重新请求，只返回游标之后的事件，终态连接正常结束 |
| 匿名及无效 Cookie | 详情、execute、events 均返回 401，冻结任务未入队、未调用工具 |
| 跨用户与跨租户 | 详情和 execute 返回 404；SSE 返回错误帧并关闭，不返回任务事件或任务编号 |
| 同 Cookie 权限回收 | 不重新登录，`/api/auth/me` 反映新权限，execute 返回 403 |
| 页面操作关闭 | 在入队前返回 403，计划维持 PLAN_READY，没有工具执行审计 |
| 重复及幂等域 | 同时发起两次同键请求，仅一次工具调用；同键换任务返回 409，第二份计划未入队 |
| 旧计划 Hash | 请求与冻结 Hash 不一致返回 409，任务保持原状态 |

并发场景证明两个客户端请求及重复处理结果，不保证服务器事务必然重叠，不代替强制数据库竞争实验。
SSE 读取限制为 10 秒及 64 KiB，关闭流和读取执行器；测试结束后等待正在运行的夹具任务结束，再由 Spring 关闭服务并清理自建资源。
策略读取异常的确认签发/消费由单测注入覆盖；未声称真实数据库故障注入已验收。

## 执行

Java 17、本机 PostgreSQL/pgvector、测试账号及原生 Redis 可执行文件的环境要求同隔离文档。
凭据只通过本地测试环境变量配置，不写入记录或返回客户端。

```powershell
cd backend
mvn '-Dtest=AgentHttpExecutionIntegrationTest,AgentReadExecutionIntegrationTest,AgentTaskApiServiceTest' test
```

显式真实验收必须确认两个集成类实际执行，禁止用环境跳过代替通过。
全量门槛仍为 OA lint、test、build 与后端干净测试。

## 2026-10-25 验证记录

- 修复前两组集成测试 15 项中仅页面策略关闭场景失败：预期 403，实际 202；其余 14 项通过。
- 修复后定向 26 项零失败、零错误、零跳过：HTTP 7 项、原 Worker 8 项、入口单测 11 项。
- 补充确认消费异常及匿名/坏 Cookie 请求组合后，最终 Java 17 `mvn clean test` 为 1486 项零失败、零错误。
  HTTP 7 项和 Worker 8 项再次实际通过、零跳过。2 项既有环境跳过为 Docker 初始化及未配置观测数据库的日志聚合测试。
- 前端首次全量有 1 项资产页用例在一秒内未等到已授权操作入口，原整文件定向 10 项通过。
  该用例改为先等待认证快照，并在三秒内等待按钮；保留关闭窗口后迟到结果不能重开的断言，未改资产业务代码。
  修改后定向 10 项及最终 54 个文件 415 项全部通过；lint 零错误（3 条既有警告），构建通过并保留既有体积警告。
- 独立只读审查没有 Critical/Important 阻断项；客户端同时请求不代替强制事务竞争，浏览器与真实模型仍未验收。
- 最终临时数据库和所持有的 Redis 子进程均无残留；开发库维持 162 条迁移、版本 `202610242252`。

## 回滚与剩余目标

没有数据库结构变化。回滚前应关闭受影响页面操作或执行能力，避免恢复错误的接收入队行为；Gateway 的最终保护仍应保留。
真实模型、浏览器 CORS/SameSite/EventSource、其他页面工具、实际写确认和全页面验收仍是后续目标。
