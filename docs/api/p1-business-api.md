# P1 业务闭环 API

## 1. 通用约定

- 所有接口使用 `/api` 前缀并返回 `Result<T>`；除公开健康检查外均要求 JWT Cookie 或 Bearer Token。
- 身份、租户、角色与权限只从服务端认证上下文解析，客户端不得提交可信 `userId`、`tenantId` 或权限声明。
- 写请求使用 Bean Validation；错误消息按 `Accept-Language` 返回 `zh-CN` 或 `en-US`。
- 可变业务记录的写操作携带 `version`，状态或版本不匹配分别返回业务状态错误或乐观锁冲突。
- 附件接口返回受控内容 URL，不返回 MinIO 对象键、内部地址或连接信息。

## 2. 审批申请与待办

### 2.1 申请生命周期

基础路径：`/api/approval-applications`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/drafts` | 创建草稿，不启动流程 |
| `PUT` | `/{id}/draft` | 编辑草稿或重新打开后的草稿 |
| `POST` | `/{id}/submit` | 提交草稿并冻结表单、流程和规则快照 |
| `POST` | `/{id}/cancel` | 取消草稿 |
| `POST` | `/{id}/withdraw` | 申请人撤回待审批申请 |
| `POST` | `/{id}/reopen` | 将被拒绝或已撤回申请重新打开为草稿 |
| `POST` | `/{id}/remind` | 催办当前有效待办，受频率限制 |
| `POST` | `/` | 兼容直接创建并提交 |
| `GET` | `/mine` | 查询本人申请 |
| `GET` | `/{id}` | 按快照返回申请详情和审批历史 |

申请状态机：

```text
DRAFT -> PENDING -> APPROVED
                 -> REJECTED -> DRAFT（重新打开）
                 -> WITHDRAWN -> DRAFT（重新打开）
DRAFT -> CANCELLED
```

`reopen` 保留原流程历史；再次提交会创建新流程实例。提交后配置版本变化不得影响历史申请的展示和执行。

### 2.2 审批待办

基础路径：`/api/approval-tasks`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/`、`/stats` | 查询当前人的待办和统计 |
| `POST` | `/{id}/approve`、`/{id}/reject` | 审批或驳回当前有效待办 |
| `GET` | `/{id}/participant-candidates` | 查询可转交、抄送或加签的有效用户 |
| `POST` | `/{id}/transfer` | 转交当前待办，原处理人失去处理权 |
| `POST` | `/{id}/copy` | 产生只读抄送通知，不授予审批权 |
| `POST` | `/{id}/add-sign` | 前加签或后加签 |
| `GET` | `/{id}/timeline` | 查询审批、转交、抄送、加签和催办时间线 |

只有流程顺序中的当前有效待办可写；审批、转交和加签均校验租户、处理人、状态与版本。

## 3. 人事变动与员工档案

基础路径：`/api/hr/employee-changes`

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/`、`/{id}` | `hr:read` | 查询入职、转正、调岗、离职申请 |
| `POST` | `/` | `hr:manage` | 创建待审批变动并冻结当前任职信息 |
| `POST` | `/{id}/approve`、`/{id}/reject` | `hr:manage` | 指定审批人处理申请 |
| `POST` | `/{id}/withdraw` | `hr:manage` + 申请人 | 撤回待审批申请 |

状态机为 `PENDING -> APPROVED -> EFFECTIVE`，或从 `PENDING` 进入 `REJECTED/WITHDRAWN`。到达生效日后由服务端任务更新员工部门、岗位、直属审批人和任职状态。

档案附件基础路径为 `/api/hr/employees/{employeeUserId}/documents`，支持列表、上传和 `/{documentId}/content` 下载。本人可查看自己的附件；上传和跨员工访问要求实时人事权限及同租户校验。

## 4. 资产、会议室、访客与印章

### 4.1 资产

基础路径：`/api/admin-assets/assets`

- 台账：列表、详情、创建、编辑、删除。
- 生命周期：`/{id}/claim`、`return`、`transfer`、`repairs`、`repairs/complete`、`inventories`、`scrap`。
- 主要状态：`IDLE -> IN_USE -> IDLE`、`IDLE -> REPAIRING -> IDLE`、任一允许状态到 `SCRAPPED`。
- `SCRAPPED` 为终态；所有操作按 `asset:write`、租户、状态和版本校验并写入操作历史。

### 4.2 会议室预约

基础路径：`/api/admin-assets/meeting-bookings`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/` | 按会议室锁定并检查时间段冲突后预约 |
| `GET` | `/mine` | 查询本人预约 |
| `GET` | `/admin` | 管理员查询租户预约 |
| `POST` | `/{id}/cancel` | 预约人或管理员按版本取消 |

预约状态为 `BOOKED -> CANCELLED`；结束时间必须晚于开始时间，人数不得超过会议室容量。

### 4.3 访客

基础路径：`/api/admin-assets/visitor-bookings`

- 申请与审批：创建、详情、本人列表、待审批列表、撤回、按任务审批或驳回。
- 到访登记：`/{id}/check-in`、`arrive`、`leave`、`no-show`。
- 状态机：`PENDING -> APPROVED -> CHECKED_IN -> VISITED -> LEFT`；审批也可进入 `REJECTED/WITHDRAWN`，已批准且超过到访时间的记录可进入 `NO_SHOW`。
- 本人或接待人使用 `visitor:register`；全租户代登记额外要求 `visitor:register:any`。

### 4.4 印章

基础路径：`/api/admin-assets/seal-usages`

- 申请与审批：创建、详情、本人列表、待审批列表、撤回、按任务审批或驳回。
- 实际执行：`/{id}/use` 登记份数和经办人，`/{id}/return` 登记归还。
- 状态机：`PENDING -> APPROVED -> USED -> RETURNED`，或进入 `REJECTED/WITHDRAWN`。
- 实际份数不得超过审批份数；跨申请登记要求 `seal:register:any`。
- 受控文件路径：`/{sealUsageId}/documents`，支持列表、上传和 `/{documentId}/content` 下载。

## 5. 用户设置与系统能力

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET/PUT` | `/api/settings/chat` | 已认证 | 统一读取或保存模型、上下文轮数、流式开关和 OCR 偏好 |
| `GET/PUT` | `/api/settings/ocr` | 已认证 | 保留的 OCR 单项兼容接口 |
| `GET` | `/api/admin/system/capabilities` | `access:manage` | 查询 AI、Embedding、OCR、MinIO、Redis 状态与安全摘要 |

服务端设置是登录后的权威来源；浏览器旧设置仅迁移一次。能力状态接口不返回密钥、连接串、内部地址或异常堆栈，AI 状态只检查服务端配置，不发起计费推理请求。
