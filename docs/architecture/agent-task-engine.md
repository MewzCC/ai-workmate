# Agent 持久任务引擎架构

## 1. 边界与不变量

Phase 2A 任务引擎是 PostgreSQL 驱动的受控只读执行器。模型只产生严格 DTO 候选计划；Controller、Planner、Task Service 与 Worker 都不能注入 `ToolHandler`。Worker 唯一允许的业务执行调用为 `ToolGateway.execute(stepId, workerLease)`。

- 客户端只看到服务端生成、不可枚举的 `taskId`（数据库字段 `task_no`），不暴露内部主键和 `stepId`。
- 所有权查询同时限定 `tenant_id`、`user_id` 和 `task_no`。
- 状态迁移、确认消费和任务领取依赖数据库条件更新或行锁，不以 JVM 内存状态作为事实来源。
- Phase 2A 只执行 L0 工具；工具结果是不可信结构化数据，不触发递归规划。
- Registry、权限、审计或策略依赖异常时关闭执行路径，不回退直调 Handler。

## 2. 生命周期与领取

状态机固定为：

```text
RECEIVED -> PLANNING -> PLAN_READY/WAITING_CONFIRMATION -> QUEUED -> RUNNING
RUNNING -> SUCCEEDED/PARTIALLY_SUCCEEDED/FAILED/TIMED_OUT
任意受支持的非终态 -> REJECTED/EXPIRED/CANCELLED
```

入队事务提交后发送本地唤醒事件，定时轮询作为丢失唤醒的兜底。Worker 使用 `FOR UPDATE SKIP LOCKED` 原子领取一条任务，保存随机租约的哈希、Worker ID 和到期时间。明文租约只存在于执行线程内存，不写数据库或日志。

每次执行前，Gateway 从数据库重新装载任务和步骤，校验租户、用户、状态、attempt、租约、计划/工具/Schema/参数哈希、实时权限、风险、确认、预算和输入 Schema。领域 Service 继续校验资源归属与业务状态。

## 3. 超时、重试与结果未知

- L0 步骤在明确的基础设施不可用结果下最多重试到数据库约束允许的 attempt 上限。
- 租约过期且当前运行步骤均为 L0 时，恢复器将步骤和任务原子重排队。
- 任务超时、重试耗尽或出现非 L0 运行步骤时，恢复器先关闭为 `TIMED_OUT`，不会盲目重放。
- 进程在 Handler 返回后、完成审计或状态提交前退出时属于“结果未知”。Phase 2A 查询工具可按租约恢复；Phase 2B 写工具必须依赖领域幂等键和事务证据，禁止仅凭 Agent 状态猜测成功。

恢复次序固定为先 `closeTimedOutOrUnsafe()`，再 `recoverExpiredReadOnly()`，最后仅为本实例仍持有的租约续心跳。

## 4. 保留与清理

- 普通任务保留 90 天，仅清理终态任务。
- 详细事件和工具调用审计保留 30 天。
- 清理器先删除事件，再删除调用审计，最后删除终态任务；按租户、创建时间和主键排序，每租户每次最多删除配置的批量大小。
- 单次运行最多扫描 100 个租户，使用 `FOR UPDATE SKIP LOCKED`，避免清理实例相互阻塞。
- 活跃任务、未到期记录和其他租户记录不会进入候选集。外键级联只用于终态任务最终清理。

配置项：`agent.retention-cleanup-enabled`、`agent.retention-batch-size`、`agent.retention-cleanup-cron`，以及 `agent.limits.task-retention-days`、`agent.limits.event-retention-days`。硬上限仍由配置校验约束。

## 5. 低敏观测与审计查询

运行日志使用固定事件名和有界数值字段：

- `agent_worker_recovery`：关闭、恢复、心跳更新数量；
- `agent_retention_cleanup`：事件、调用、任务删除数量和租户批次数；
- `agent_retention_cleanup_failed`：失败前已完成的租户批次数。

这些日志不得包含 tenant/user/task/step 标识、Prompt、参数、结果、租约、确认凭证或异常消息。工具调用审计查询必须同时限定租户、用户和外部 taskId，最多返回 100 条，只返回决策、工具版本、结果大小、稳定错误类别和时间信息，不返回参数摘要或业务结果。

`com.aiworkmate.agent` 在默认和开发配置中保持 INFO，避免 MyBatis DEBUG 参数日志旁路泄露上述标识或内容；故障诊断优先使用有界聚合查询，不临时打开整个 Agent 包的 DEBUG。

## 6. 验证证据

- 单元测试证明清理顺序、租户分批、批量上限、关闭开关和依赖失败时关闭。
- Worker 测试证明不安全工作先终止、只读过期租约后恢复、执行器拒绝不泄漏本地并发额度。
- PostgreSQL 验收验证空库迁移、历史基线升级、重启 `validate()`、租户隔离审计查询以及保留边界。
- 故障处理步骤见 [Agent 任务引擎恢复运行手册](../qa/agent-task-recovery-runbook.md)。
