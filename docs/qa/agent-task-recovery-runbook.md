# Agent 任务引擎恢复运行手册

## 1. 使用范围

本手册用于 Phase 2 任务堆积、Worker 重启、租约过期、审计写入异常和保留任务失败。操作人员必须拥有数据库只读诊断权限；任何数据修复、重新执行或功能开关变更都需要独立审批。

## 2. 首要止损

1. 若存在越权、审计缺失或策略服务异常，设置 `AGENT_EXECUTION_ENABLED=false` 并滚动重启实例。
2. 保持 `AGENT_WRITE_TOOLS_ENABLED=false`。Phase 2A 不应存在启用的写工具。
3. 不删除任务、不手工改为成功、不复制租约或确认凭证，不通过 Controller/脚本直接调用 Handler。
4. 记录事故时间窗、部署版本和受影响数量，禁止复制 Prompt、参数、结果或用户标识到工单。

## 3. 只读诊断

以下查询只返回聚合数量：

```sql
SELECT status, count(*)
FROM agent_task
WHERE created_at >= :incident_start AND created_at < :incident_end
GROUP BY status ORDER BY status;

SELECT decision, outcome, count(*)
FROM agent_tool_invocation
WHERE started_at >= :incident_start AND started_at < :incident_end
GROUP BY decision, outcome ORDER BY decision, outcome;

SELECT count(*) AS expired_running
FROM agent_task
WHERE status = 'RUNNING' AND lease_until <= CURRENT_TIMESTAMP;
```

定位单个用户报告时，必须使用已核验的 `tenant_id + user_id + task_no` 三元组查询。不要按内部 `step_id` 或模糊 Prompt 搜索。

## 4. 场景处置

### Worker 重启或租约过期

恢复调度每 5 秒先关闭超时/不安全任务，再恢复可重试 L0 任务。确认 `agent_worker_recovery` 只出现数量字段，观察任务从 `RUNNING` 转为 `QUEUED` 后被新租约领取。若 attempt 耗尽，任务必须停留在终态，不得手工归零。

### 队列持续堆积

核对全局和执行 Kill Switch、数据库连接、线程池饱和度与每用户并发上限。执行器拒绝后，持久租约会留给恢复器处理；不要创建第二条任务绕过幂等约束。

### 审计或 Registry 不可用

预期行为是 Gateway 在 Handler 调用前拒绝，`handler_invoked=false`。保持执行关闭，修复依赖并完成审计查询隔离验证后再申请恢复。禁止建立跳过审计的临时执行路径。

### 结果未知

Phase 2A 只读调用可等待租约恢复。未来写工具不得自动重放未知结果；必须检查领域幂等记录和业务事务证据，经人工判断后处置。

### 保留清理失败

清理日志只给出完成批次数。核对数据库可用性、锁等待和迁移索引；修复后等待下一调度或在受控运维窗口调用同一清理入口。清理器仅处理到期详细记录和终态任务，禁止扩大 SQL 时间范围。

## 5. 恢复验收

- `AGENT_ENABLED`、`AGENT_PLANNING_ENABLED`、`AGENT_EXECUTION_ENABLED` 的实际值符合发布审批；写工具仍关闭。
- Flyway `validate()` 通过，没有 checksum mismatch 或失败迁移。
- 过期 L0 租约已恢复或进入明确终态，不存在不安全写步骤被自动重放。
- 跨租户、跨用户审计查询返回空；所属用户查询不包含参数或结果正文。
- 定向 Worker/保留/审计测试和后端全量测试通过。
- 恢复后只逐步开启计划和只读执行；若安全门失败，立即重新关闭执行。
