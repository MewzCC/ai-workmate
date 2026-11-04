# 访客接待人与人事审批人最小候选查询

积压批次 6B-2，对应 A15/A06 的领域候选查询部分。页面选人控件和 Agent 工具注册另批提交。

## 接口与复用

- `GET /api/admin-assets/visitor-bookings/host-options`：仅具有 `visitor:create` 的当前认证用户查询本租户有效人员。
- `GET /api/hr/employee-changes/reviewer-options`：仅具有 `hr:manage` 的当前认证用户查询本租户有效且实际具有 `hr:manage` 的候选人，并排除当前申请人。
- 两接口都接受 `keyword/page/size`，返回最小 `id/name` 与分页信息，不返回独立联系方式或员工档案字段。重名返回全部匹配候选，不代替用户选人。
- 共享 Mapper 根据已分配角色、无分配角色时的旧角色回退以及有效权限目录中的 SUPER_ADMIN 规则查询，不按前端角色猜测。查询对象的有效状态及当前角色权限每次读取数据库。
- 新增无身份数据的 `BusinessCandidateQuery`，统一搜索归一化、页码 1–10000、每页 1–100、关键词最多 120 字符和偏移计算。资产既有候选查询复用相同校验，领域授权仍分别留在各 Service。
- 同步前端 API 类型和封装。没有新权限、Flyway 迁移、工具开关或通用人员/任意查询接口。

## 验证与限制

从暂存树导出独立后端及前端源码，隔离快照位于本地 `backend/target/chat-server-review-bm2_6heq/`。后端使用测试框架自建 PostgreSQL/schema 和随机端口 Redis，不使用开发库。

新增查询值对象检查覆盖边界、过长搜索、空白归一化及偏移。真实 Cookie/HTTP/PostgreSQL 检查覆盖租户及状态过滤、205 人稳定分页、字段最小化、申请人排除、实时撤权、重名、已分配角色覆盖旧角色、SUPER_ADMIN 回退与非法参数。

候选只表达基础领域权限符合；实际创建和审批仍须由领域 Service 核对资源归属、状态和实时权限。此批不证明所有具体变动单都可交由列表内任意人处理，也不代表前端控件或 Agent 已完成接线。

默认快照保留旧初始化测试及旧运行日志数据库配置的环境限制，结果以本批全量日志记录为准；不扩大数据库目标或放宽权限来消除跳过。前端其余旧页面 Hook 警告和大分块提示独立记录。

最终结果：后端默认全量 **1891 项、零失败/错误、2 跳过，BUILD SUCCESS**，1889 项通过；真实候选接口 **7 项、零跳过**，公共查询参数 **8 项通过**。两项旧跳过为初始化测试缺 Docker，以及旧运行日志测试缺专用 `OBSERVABILITY_TEST_DB_URL`。前端独立快照 **592 项、零失败/跳过**，lint 零错误、3 项其他旧页面 Hook 警告，构建通过（23.01 秒）；大分块提示保留。

日志为该快照的 `backend/target/business-candidates-full.log`、`business-candidates-front-tests.json`、`business-candidates-front-lint.log` 和 `business-candidates-front-build.log`。暂存文件与实际验证快照逐一核对，业务文本差异为零。

## 回滚

可撤销本批接口、公共值对象和调用封装，无结构回滚。其他页面/工具引用后应先回滚调用方。访客和人事权限不能互相替代，也不能扩大到全局员工档案或权限管理。
