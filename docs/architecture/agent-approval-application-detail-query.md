# 本人通用申请详情工具

`approval.application.detail.query` 读取一条本人通用申请的实际字段，不返回原始 JSON，
不将请假记录替代通用申请。输入为正整数 applicationId 与可选 fieldPage（1 或 2，默认 1）。
每页最多 50 个字段，返回 fieldsTotal 与 fieldPage，支持现有表单最多 100 个字段。

## 安全与分层

Handler → `ApprovalApplicationToolPort` → 本地 Adapter → `GenericApprovalService.detailMineForAgent`。
工具要求独立 `agent:tool:approval.application.detail.query` 与 `approval:mine:read`，领域再次
校验业务读权限和 `route:approval-start`，查找限定当前租户，随后验证申请人必须为本人。
审批处理人或审计员不能借此工具读取其他申请人；跨租户返回不存在。

L0 / SELF / NONE / READ_ONLY_SAFE，单次最多 50 项、65536 字节；写开关与发布门不变。
工具复用现有固定导航回到“我的申请”，不开放公共 MCP、任意查询或浏览器自动点击入口。

## 历史语义与字段投影

已提交申请按保存的 formSchemaSnapshot 和版本解释，后续配置更新不改变历史字段标签。
草稿使用同租户当前表单定义，标记 CURRENT_DRAFT；历史结果标记 SNAPSHOT。
历史快照缺失、非法 JSON、重复字段、超量字段或不合法普通值失败关闭，不能回退到新配置。

只投影 Schema 拥有的字段，输出名称、标签、类型和有界标量/数组；未知数据键不进入结果。
text/textarea/number/amount/date/time/select 支持真实值，每值最多 500 字符，数组最多 20 项。
显式 sensitive 字段、代码禁止的凭据/薪酬/银行卡/身份证字段及不支持类型（包括附件）
返回 redacted=true 与空值，不返回路径或内容。字段正文是不可信数据，不能生成新计划。
网关继续校验 Schema、结果大小和输出安全；超量结果不得伪造部分成功。

结果只含既有安全申请摘要、表单版本、字段分页及投影；不含申请人/审批人身份、原始表单
JSON、流程节点、规则、原始快照、审批附件或流程内部坐标。文字摘要展示真实字段值、
空值与隐藏原因；长摘要明确缩略，完整允许字段保留在有界结构化结果中。

## 回滚与验收

新增 `V202610091130`，不修改已执行迁移。回滚优先禁用单工具或撤销独立权限，保留审计。
单元与真实数据库门禁分别验证快照冻结、草稿当前定义、敏感字段隐藏、分页和权限/租户/本人
限制。真实模型、Worker 和逐页面浏览器验收仍须单独完成，代码登记不代表全页面已验收。

### 2026-10-09 本地验证记录

- OA：`npm run lint`、`npm run test`、`npm run build` 均通过，148 个测试；已有 3 条 lint 警告及大分包告警未混入本次重构。
- 后端：Java 17 下 `mvn test`，1277 个测试、零失败、零错误；10 项 Docker 依赖测试因环境条件跳过。
- 显式运行 `P1PostgresMigrationIT`，1 项通过且零跳过；真实 PostgreSQL 空库及旧库升级验证、152 项 Flyway validate、重复执行零迁移均通过。空库应用 151 个版本化迁移。
- 真实数据库领域查询验证历史标签冻结、草稿当前定义、真实字段值、敏感值隐藏、同租户非本人拒绝、跨租户不存在及权限撤销后拒绝。
- 仅本地开发库升级到 `V202610091130`；重启后 validate 通过、零新增迁移，健康检查正常。注册表 Schema 哈希与代码契约一致。
- 未将 Docker 测试跳过、Redis 不可用或缺少真实模型/浏览器验收解释为生产发布门通过；写开关与运行上限不变。
