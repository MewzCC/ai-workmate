# 资产部门与保管人最小候选接口

积压批次 6B-1，对应 A05 的领域查询接口部分。资产页面接线和 Agent 工具注册另批处理；本批不扩展人事、权限管理或 Agent 写权限。

## 接口与边界

- `GET /api/admin-assets/assets/assignment/departments`：仅持有 `asset:write` 的当前有效用户可读取本人租户的有效部门标签。
- `GET /api/admin-assets/assets/assignment/custodians?departmentId=...&page=1&size=100`：核对当前租户、部门启用状态、用户有效状态，按编号稳定分页，返回 `id/name/departmentId` 和总数，不返回员工档案或独立联系方式字段。
- 内部 `assetDepartmentOptions`、`assetCustodianOptions` 提供受权限保护的分页名称查询，供后续固定工具适配复用；并非开放任意查询。重名结果保留多个候选，不自动选人。
- 查询用户身份来自 JWT Cookie，实时数据库 RBAC 复核；人事/权限管理授权不能代替 `asset:write`，跨租户部门拒绝。页码不超过 10000，每页最多 100，关键词最多 120 字符；SQL 使用绑定参数，数值分页经过边界检查。
- 同步前端 API 类型与调用封装；不含资产页控件、访客或人事候选功能。无数据库结构或种子变更。

## 验证

通过暂存树导出独立后端及前端快照，本地目录 `backend/target/chat-server-review-6fo2e362/`。候选检查使用真实 HTTP Cookie 与测试框架自建 PostgreSQL/随机 Redis，不读取或修改开发库。

新增真实检查覆盖有效候选最小字段、禁用用户/部门、205 人三页完整读取、重名、分页关键词、注入式关键词作为数据处理、缺少资产权限、同 Cookie 撤权后拒绝、跨租户部门和非法分页。

前端快照全量 592 项通过、零失败/跳过，构建通过（17.88 秒）；lint 零错误，保留其他旧页面三项 Hook 警告及构建大分块提示。

拆分首轮误带两个已有业务常量，独立编译发现重复定义；修正后又发现测试的本地认证配置助手尚未提交。已只补入必要助手方法和符合正式用户角色关联的测试账号夹具，未带入其他浏览器逻辑或修改业务断言。

后端最终默认全量 **1870 项，零失败/错误，2 跳过，BUILD SUCCESS**（1868 项通过），其中候选真实 HTTP/PostgreSQL **11 项、零跳过**。两项旧环境跳过仍为缺 Docker 的初始化测试，以及快照保留旧实现而缺 `OBSERVABILITY_TEST_DB_URL` 的运行日志测试。日志为 `backend/target/asset-candidates-full-final.log`，前端报告为快照根目录的 `asset-candidates-front-tests.json`。暂存文件与验证快照逐一比较，业务文本差异为零。

## 限制与回滚

候选接口提交时发现既有写入辅助 `requireDepartment` 只检查部门存在/租户，直接写请求可使用禁用部门，登记为 A22。后续已单独补齐新增/分配目标校验并验证，见 [资产分配状态修复](20261102-asset-assignment-state.md)；候选过滤仍不能替代领域校验，也不代表完整资产状态闭环已验收。

无迁移或发布开关变化，可撤销本批接口及调用封装。后续页面或工具引用后，应先撤销调用方。角色/用户候选授权不能扩大为人事档案或权限管理读取。
