# 本人补卡申请原子工具

`attendance.reissue.apply` 为当前认证用户创建一条待审批补卡申请。它属于 L1、`SINGLE_WRITE`、`BUSINESS_IDEMPOTENT`、`EXPLICIT` 工具，不提供代申请、审批、批量补卡或直接修改考勤记录能力。

## 调用边界

`ToolGateway -> AttendanceReissueApplyToolHandler -> AttendanceToolPort -> AttendanceAgentDomainToolAdapter -> AttendanceService`

- Handler 仅解析冻结的日期、上下班类型和原因，不接受租户、申请人、审批人、角色或权限参数。
- Port 使用类型化命令和结果，不依赖 Spring、HTTP、数据库实体或通用参数 Map；后续拆分为 Spring Cloud 服务时只替换 Adapter。
- Adapter 只负责 DTO 映射，不复制权限、审批人解析、重复申请检查、事务或审计逻辑。
- 领域 Service 每次重新解析有效用户和业务权限，锁定当前租户申请人并校验有效的同租户直属审批人。

## 写入与重放

- 写入只产生 `PENDING` 申请与业务审计，不写 `attendance_record`。
- 稳定操作键由可信 taskId、stepId 和工具代码生成，并以租户、申请人、操作键建立唯一约束。
- 相同操作键和相同命令返回原申请；参数或状态不一致返回幂等冲突，不执行第二次写入。
- 申请和审计位于同一事务，审计失败时申请回滚。

## 发布与回滚

代码登记不等于生产开放。执行仍须同时满足独立 Agent 权限、业务权限、确认令牌、Worker 租约、预算、Kill Switch，以及默认关闭的全局和租户写开关。

回滚应用版本即可停止使用 Handler；数据库迁移不回退。新增列、唯一索引、工具定义与权限可保留为兼容数据，工具写开关保持关闭。
