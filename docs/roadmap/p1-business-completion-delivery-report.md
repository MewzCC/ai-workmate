# P1 业务闭环交付验收报告

## 1. 交付结论

- 交付状态：`PASS`
- 验收日期：2026-08-30
- 分支：`zcc/feat/p1-business-completion`
- 范围：通用审批、人事、资产、会议室、访客、印章、用户设置、能力检查、前端分包与回归门禁
- API 明细：[P1 业务闭环 API](../api/p1-business-api.md)

P1 的 18 个纵向切片均已按顺序完成、验证并独立提交。业务写操作继续使用实时数据库 RBAC、认证租户、Bean Validation、国际化错误消息与乐观锁；没有扩展 Phase 2 Agent 永久禁止能力，也没有绕过 Tool Gateway。

## 2. 提交线

| 顺序 | 提交 | 主题 |
| ---: | --- | --- |
| 1 | `493bf08c` | `feat(approval): 完成通用审批草稿生命周期` |
| 2 | `0834048e` | `feat(approval): 完成通用申请撤回与重新提交` |
| 3 | `32656567` | `feat(approval): 增加审批转交与抄送能力` |
| 4 | `bd2dedc9` | `feat(approval): 增加审批加签能力` |
| 5 | `7a33c244` | `feat(approval): 增加审批催办与时效跟踪` |
| 6 | `7686c034` | `feat(approval): 冻结审批流程与表单版本` |
| 7 | `9eee2419` | `feat(hr): 完成入转调离业务流程` |
| 8 | `8c14ee1a` | `feat(hr): 完善员工任职历史与档案附件` |
| 9 | `c74ee8e2` | `feat(assets): 完成资产领用归还与调拨` |
| 10 | `c6b9c5c0` | `feat(assets): 增加资产维修盘点与报废流程` |
| 11 | `867453a9` | `feat(meeting): 完成会议室预约与冲突检测` |
| 12 | `b1801c2a` | `feat(visitor): 完成访客签到与离场流程` |
| 13 | `5ef5bf39` | `feat(seal): 完成实际用印与归还登记` |
| 14 | `6b02950e` | `feat(settings): 将用户聊天偏好持久化到服务端` |
| 15 | `0c9bc89` | `feat(settings): 增加系统能力状态检查` |
| 16 | `3fb5cee7` | `perf(oa): 优化工作台页面加载体积` |
| 17 | `503d352` | `test(p1): 补齐业务闭环与数据库回归测试` |
| 18 | 本报告所在提交 | `docs(p1): 更新业务流程与交付验收文档` |

## 3. 状态机与权限摘要

| 模块 | 主状态流 | 核心权限 |
| --- | --- | --- |
| 通用审批 | `DRAFT -> PENDING -> APPROVED/REJECTED/WITHDRAWN`，草稿可 `CANCELLED`，拒绝或撤回可重新打开 | `route:approval-start`、`approval:read/manage/act` |
| 人事变动 | `PENDING -> APPROVED -> EFFECTIVE` 或 `REJECTED/WITHDRAWN` | `hr:read`、`hr:manage` |
| 资产 | `IDLE/IN_USE/REPAIRING -> SCRAPPED`，报废为终态 | `assets:read`、`asset:write` |
| 会议预约 | `BOOKED -> CANCELLED` | `meeting:book`、`meeting:read:self`、`meeting:write` |
| 访客 | `PENDING -> APPROVED -> CHECKED_IN -> VISITED -> LEFT/NO_SHOW` | `visitor:create/read:self/withdraw/register/register:any`、`approval:act` |
| 印章 | `PENDING -> APPROVED -> USED -> RETURNED` | `seal:create/read:self/withdraw/register/register:any`、`approval:act` |
| 用户设置 | 服务端用户级配置覆盖旧浏览器配置 | 已认证用户 |
| 能力检查 | 只读实时状态 | `access:manage` |

所有资源查询和写入同时限定租户。本人权限不会扩展为全租户权限；转交、抄送、加签、催办、资产操作、到访登记和实际用印均记录审计。

## 4. 数据库迁移

本阶段新增迁移如下，历史迁移未修改：

- `V202608292144__generic_approval_draft_lifecycle.sql`
- `V202608292205__generic_approval_resubmission.sql`
- `V202608292220__approval_transfer_and_cc.sql`
- `V202608300030__approval_add_sign.sql`
- `V202608300055__approval_reminder_tracking.sql`
- `V202608300125__approval_definition_snapshots.sql`
- `V202608300325__employee_change_workflow.sql`
- `V202608300340__employee_documents.sql`
- `V202608300410__asset_lifecycle.sql`
- `V202608301420__asset_maintenance_inventory.sql`
- `V202608301440__meeting_room_booking.sql`
- `V202608301520__visitor_visit_lifecycle.sql`
- `V202608301540__seal_execution_archive.sql`

真实 PostgreSQL 16.14 验证结果：

| 场景 | 结果 |
| --- | --- |
| 隔离空 schema 首次迁移 | 35 个版本成功，最新版本 `202608301540` |
| V1–V4 已有 schema 升级 | 31 个增量版本成功 |
| Flyway validate | 空库与升级库均成功校验 36 个迁移描述 |
| 重启 | 0 个待执行迁移，无 checksum 或重复版本错误 |
| 清理 | 仅删除随机 `p1_empty_*`、`p1_upgrade_*` 隔离 schema；业务 schema 未清理 |

显式验证入口：

```powershell
./scripts/verify-p1-postgres.ps1 `
  -DatabaseUrl 'jdbc:postgresql://<host>:<port>/<database>' `
  -DatabaseUsername '<username>' `
  -DatabasePassword '<password>'
```

入口缺少参数或目标不是 PostgreSQL 时直接失败；`P1PostgresMigrationIT` 不使用条件注解或 Assumption，因此不会静默跳过。

## 5. 部署依赖

- Java 17、Maven 3.9、Node.js 20。
- PostgreSQL 16 且已安装 pgvector；结构由 Flyway 独占管理。
- Redis 用于通知、登录保护等能力；不可用时能力检查返回 `UNAVAILABLE`。
- MinIO 用于员工档案、印章留档和用户图片；需要配置 `MINIO_ENDPOINT`、访问凭据和存储桶。
- OCR 为可选依赖；通过 `OCR_ENABLED` 和 `OCR_BASE_URL` 控制，不可用时不得伪造识别结果。
- AI 与 Embedding 的地址和密钥只通过服务端环境变量配置，不允许由设置页读取或回显。
- OA 与营销站保持两个独立 Vite SPA：`fonted-oa` 运行于 3001，`fronted-main` 运行于 3000。

## 6. 验证证据

```powershell
cd fonted-oa
npm run lint
npm run test
npm run build

cd ../backend
mvn test
```

| 验证 | 结果 |
| --- | --- |
| OA lint | 0 errors；4 个既有 React Hooks warnings |
| OA tests | 8 files / 30 tests 通过 |
| OA build | 通过；主包由 `4295.07 kB` 降至 `3202.56 kB`，约减少 25.4% |
| 后端全量 | 412 tests，0 failures，0 errors，9 个既有环境条件跳过 |
| P1 真实 PostgreSQL 门 | 1 test，0 failures，0 errors，0 skipped |

全量 Maven 的 9 个跳过项是既有 Testcontainers/Agent 环境条件用例；P1 的迁移验收由独立真实 PostgreSQL 门补充，并明确禁止跳过。

## 7. 已知限制

- OA 主包已显著下降，但仍约 3.2 MB；AI Workspace 与组织架构图独立分包仍超过 500 kB，Vite 会保留大 chunk 警告。
- AI 能力状态表示服务端配置就绪，不主动调用外部模型，以避免健康检查产生费用或数据外发；不等同于一次真实推理成功。
- 人事变动未来生效由服务端定时任务执行；部署时需要确保单实例或后续增加分布式调度锁。
- 会议冲突在服务端锁定会议室后检查；跨服务多写实例依赖数据库锁和事务，不能由前端日历结果替代。
- 文件业务依赖 MinIO；数据库回滚不会自动删除或恢复已经写入的对象。
- 本阶段验证以服务、控制器安全、前端组件和真实迁移回归为主，未新增完整浏览器端跨模块 E2E 套件。

## 8. 回滚方法

1. 应用代码按提交逆序使用 `git revert <commit>`，不要使用 `reset --hard`，也不要改写已发布迁移。
2. 只回滚后端代码前先确认数据库新增列和表对旧版本代码向后兼容；若不兼容，部署前恢复已验证的数据库备份。
3. Flyway 迁移不可重命名、删除或修改 checksum。结构修正必须新增更高版本的前向迁移；需要数据级回退时从备份恢复到新实例后切换。
4. MinIO 对象与数据库元数据分别备份；回滚附件功能不会自动清理对象，避免误删仍被旧记录引用的文件。
5. 用户设置完成一次性 localStorage 迁移后，回滚不会恢复浏览器旧值；服务端 `user_setting` 数据应在回滚前导出。
6. 不建议跳过中间切片单独回滚早期提交，因为后续 API、页面和迁移可能依赖前一状态机；应先在预发布环境演练目标提交组合。

文档和示例只使用占位连接信息，不包含真实账号、密钥、生产地址或数据库转储。
