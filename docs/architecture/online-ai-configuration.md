# AI 在线配置

平台全局配置位于 OA `/oa/system-config`，仅超级管理员（实时 SUPER_ADMIN + access:manage）可管理。普通用户可以在聊天中选择 `default` 跟随服务端模型，或输入网关支持的模型 ID。配置作用于聊天与 Agent Planner，不修改工具权限、执行策略、嵌入、OCR 或重排服务。

## 接口

- `GET /api/admin/ai-configuration`：baseUrl、model、keyConfigured、version、source；不返回密钥。未保存时 source=ENVIRONMENT，地址、模型不回显，version=0。
- `PUT /api/admin/ai-configuration`：baseUrl、model、apiKey、version。首次必填密钥，后续密钥留空保留原值。地址可带 `/v1`，保存时去除该后缀。禁止 URL 凭据、查询串、fragment 或完整 chat/completions 路径。version 不符返回 409。
- `POST /api/admin/ai-configuration/test`：对已保存配置执行真实流式请求（最多 60 秒），有文本才返回成功；上游错误统一脱敏。修改后先保存再测试。

在线配置优先于旧环境变量；保存后新请求立即生效，无需重建或重启。旧会话显式选择的模型保持原选择，想跟随后台需选择 `default`。不自动探测网关模型列表。

## 密钥与隔离

API Key 使用 AES-256-GCM 随机 IV 加密，仅模型客户端解密；加密键以 JWT_SECRET 加域分离 SHA-256 派生。JWT_SECRET 必须固定、保密并在生产使用随机强值；轮换 JWT_SECRET 后需重新输入 AI 密钥，不能恢复旧密文。备份必须同时妥善保管该部署密钥。密钥不进入日志、审计明细、GET 响应或浏览器存储。地址由受信任的平台超级管理员选择，支持内网 HTTP 网关；该管理能力不提供给 Agent、普通用户或租户管理员。

配置为单行平台资源，含乐观锁、更新者和时间；更新与业务审计同事务。新增 Flyway 迁移，不修改已发布迁移；回退旧应用可保留新表。

## 验证

执行 OA lint/build，后端单元测试；空 PostgreSQL 数据库执行全部迁移，随后重放新增迁移验证幂等。部署后通过真实 JWT 校验未登录/员工拒绝、密钥不回显、版本冲突、真实流式聊天和测试请求。环境未提供模型时返回真实不可用。

## 模型目录与能力（2026-11-15）

Base URL 统一持久化并回显完整 `/v1` 前缀；省略时补齐。模型客户端显式使用 `/chat/completions` 路径，避免重复 `/v1`。新增迁移将旧地址和单模型配置升级为版本化地址与 JSONB 模型清单。

- `POST /api/admin/ai-configuration/models`：baseUrl、apiKey（可留空复用已保存密钥），请求当前草稿的 `{baseUrl}/models`。只允许超级管理员；不跟随重定向，连接 10 秒、读取 20 秒，响应最多 1 MiB / 5000 模型。只使用明确返回的 context_length、max_output_tokens、supports_images；缺失时以 32768/4096/false 初始化并要求管理员核对。
- `POST /api/admin/ai-configuration/test`：允许提交上述草稿字段及 model，保存前可逐个测试；不带 body 时测试已保存配置。流式收到真实非空文本才成功，60 秒超时，不回显上游错误或密钥。
- `PUT /api/admin/ai-configuration` 新增 models 数组：每项 id、contextWindow（1024–2097152）、maxOutputTokens（16–65536）、supportsImages。1–200 项，不重复，默认模型必须已启用，输出上限必须小于上下文。
- `GET /api/ai/models`：所有已认证用户读取启用的模型与能力；不包含供应商地址、密钥或内部版本。聊天及个人偏好使用可搜索下拉。已禁用的旧会话模型请求会被拒绝，用户可改选默认或其他已启用模型。

模型调用服务端按已保存名单重新验证，输出上限固定使用配置值。文本上下文预算使用 UTF-8 字节数加消息/图片预留的保守估算，超预算明确拒绝，不悄悄截断系统提示词或 Agent 计划；该预算并非上游分词器的精确 token 数，图片 token 仍由上游实际限制最终校验。图片开关控制向模型转发原图；关闭时沿用现有 OCR 文本路径，OCR 不可用则明确失败。设置不能让不支持视觉的上游模型获得视觉能力。

仅扩展平台管理员的模型配置能力，不把此配置注册为 Agent 工具，不新增其他协议或后台自动发现任务。
