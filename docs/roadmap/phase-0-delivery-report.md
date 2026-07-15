# Phase 0 交付与验收记录

## 1. 范围

本阶段对应《AI WorkMate 企业级 AI OA 架构与迭代计划》的 `Phase 0：安全止血与工程基线`。未实施 Phase 1 的 IAM、租户、待办、请假和审批业务。

## 2. 已完成

- 删除前后端 AI plan/execute fallback 与模拟成功服务。
- AI Task 全部要求 JWT；普通用户不能调用 execute。
- plan 请求移除 role，execute 请求移除 type，身份与角色仅从服务端 principal 获取。
- 无真实 Tool 时返回稳定的 `AI_TASK_CAPABILITY_UNAVAILABLE`。
- 建立 `AUTH_*`、`PERMISSION_*`、`AI_TASK_*`、`TOOL_*` 稳定错误码。
- 统一响应增加 requestId/traceId，响应头和日志 MDC 同步。
- 聊天 SSE 固化为 `delta/error/done`，错误不再伪装成助手回答。
- OA 前端携带 JWT，按 401/403/409/429/5xx 展示真实错误和追踪号。
- PostgreSQL 成为唯一生产数据库方向，移除 MySQL runtime driver。
- 生产环境拒绝默认 JWT secret 和数据库密码，新增环境变量模板。
- 增加 JWT、安全接口、权限和生产配置回归测试。

## 3. 当前状态

- Phase 0 实现：完成。
- 后端测试：已通过，11 tests，0 failure，0 error。
- `fronted-main`：lint/build 通过；保留 1 条历史 `<img>` 性能警告。
- `fonted-oa`：lint/build 通过，0 warning，0 error。
- 后端生产包：构建与启动通过。
- API 实测：health 返回 200；匿名 plan 返回 401 + `AUTH_REQUIRED`；requestId/traceId 正常透传。
- Phase 1：未开始。

## 4. Phase 1 输入

- 以 PostgreSQL + Flyway 建立 tenant、IAM、workflow 和 audit 表。
- 将 JWT 中的临时 role claim 升级为数据库权限快照与权限版本。
- 建立“我的待办—请假申请—单笔审批”真实垂直切片。
- 真实 Tool 注册必须复用领域 Service，并接权限、确认、幂等和审计。

## 5. 未关闭风险

- OA 菜单、看板数据和角色切换仍是前端演示能力，只用于界面展示，不能作为后端授权依据。
- 尚无 tenantId 和 dataScope，因此 AI Task 正确保持不可用。
- 现有历史 schema 尚未转为 Flyway baseline，将在 Phase 1 首个数据库任务完成。
