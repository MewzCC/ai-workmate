# AI 在线配置

导航入口位于「系统设置 → AI 配置」。目录下包含「模型与偏好」`/oa/system-config`、「AI 操作权限」`/oa/ai-permission` 和「知识库管理」`/oa/knowledge-base`，各页面仍按原有权限显示。「模型与偏好」提供「AI 渠道」「调用分析」「AI 使用设置」三个独立页签。用户可以在「AI 使用设置」中调整自己的对话和 OCR 设置，或管理自己的聊天记录。

## 渠道模型分类

在渠道抽屉中从上游获取模型，再在模型目录选择用途并勾选启用。历史未分类模型按对话模型读取。只有对话模型可作为默认聊天模型，独立的 Embedding、Rerank、OCR 等专用渠道无需指定默认对话模型。TTS、ASR 和图像生成支持分类登记。

渠道抽屉只管理启用的模型及其分类。保存时，如果现有默认对话模型仍启用就继续使用它，否则使用所选的第一个对话模型。

知识库选择 Embedding 和 Rerank 后使用渠道配置。「AI 使用设置」中的 OCR 模型用于聊天附件与知识库文档解析。选择渠道 OCR 会发送图片或渲染后的 PDF 页面，当前支持兼容图片输入的 `POST /chat/completions`，单份 PDF 上限为 10 页。本地 PaddleOCR 仍作为默认识别方式供未选择渠道或内部 OA 业务上传使用。 [Chat Completions API](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create)

新模型的 Base URL、密钥和模型 ID 只在渠道中维护。旧远程 Embedding、Rerank 环境变量仅用于兼容尚未切换到渠道的部署。服务端按分类校验模型引用，前台选项不包含凭据或内部连接地址。

## 调用分析（2026-11-16）

统计响应增加 `channelName`，按当前渠道配置解析名称；渠道改名后，历史统计随刷新展示新名称，无需重写历史调用。筛选和图例展示渠道名，内部仍按 channelId 隔离；同名渠道追加编号区分，环境配置显示“环境变量配置”，缺失的历史渠道回退显示编号。

模型上下文输入旁提供 32K、64K、128K、256K、512K、1M、2M 快捷选项，按 1024 进制填写 Token 数，仍允许手动输入。选项只是编辑捷径，不代表上游模型具备该上下文能力，需按供应商能力配置。

最大输出输入旁提供 4K、8K、16K、32K、64K、128K、256K、384K 快捷选项；384K 对应 393216 tokens。选择只修改当前模型的草稿，不自动调整上下文或保存；输出必须小于上下文的保存校验保持不变。

系统配置“调用分析”页签使用 `GET /api/admin/ai-configuration/usage?days=1|7|30`，仅实时 SUPER_ADMIN + access:manage 可读。按滚动时间窗口筛选、北京时间自然日/渠道/模型聚合：calls、successes、cancelled、durationMs（耗时总和）、reportedCalls（有上游 Token 用量的调用数）、inputTokens/outputTokens/totalTokens（缺失为 null）。统计范围为统一 ChatClient 的聊天与 Planner 调用，不包含配置测试、模型发现、嵌入/OCR/重排或网关外的调用。

`ai_call_usage` 仅存模型、渠道、完成/失败/取消状态、耗时和真实 Usage，不存提示词、回复、用户身份或密钥。流式请求启用标准 `stream_options.include_usage`，每次订阅结束仅记一次，保留最后一份非空累计用量而非将每帧累加；上游缺失用量不估算。准备阶段被策略拒绝的请求不计作模型调用。上游错误/取消可能已产生费用但未返回 Usage，因此图表展示的是已报告用量，非供应商账单；缓存、费用和剩余额度未接入，明确显示未知。存储异常仅记录低敏告警，不中断聊天，该情况下报表可能漏记。需后续接入独立告警和保留周期治理。新迁移不回填历史。

## 多渠道（2026-11-16）

渠道下拉提供 SiliconFlow、DeepSeek、MiMo、Qwen（百炼北京地域）、OpenAI、OpenRouter、Ollama 预设，最后一项为自定义。选择预设仅填写默认 Base URL（仍可编辑），不发起上游请求；切换时清空模型选择与密钥输入并要求重新输入密钥。自定义允许编辑渠道名和地址；旧配置只有名称及地址均匹配预设时才识别为预设，否则保留为自定义，不覆盖既有网关地址。Ollama 默认 localhost 是后端所在环境，Docker 部署需自行改为可达的宿主机或服务地址。

预设地址参考：[硅基流动](https://docs.siliconflow.cn/docs/userguide/quickstart)、[DeepSeek](https://api-docs.deepseek.com/quick_start/agent_integrations/)、[MiMo](https://mimo.mi.com/docs/en-US/quick-start/summary/first-api-call)、[百炼](https://help.aliyun.com/zh/model-studio/base-url)、[OpenAI](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create)、[OpenRouter](https://openrouter.ai/blog/insights/model-routing/)、[Ollama](https://github.com/ollama/ollama/blob/main/docs/api/openai-compatibility.mdx)。

渠道卡片展示默认渠道标识、连接地址、密钥配置状态，以及各分类的启用模型数量。卡片上的连接测试只传渠道编号、地址和模型，由服务端读取该渠道密钥。点击配置或新增后进入二级“AI 服务配置”抽屉，关闭后回到列表；渠道名称使用最大 320px 宽的可搜索下拉框，允许选择常用名称或搜索后选用自定义名称。新增渠道的密钥与连接字段保持空白。测试结果仅代表最近一次人工测试，不作为持续健康状态。

`GET /api/admin/ai-configuration/channels` 返回渠道列表（id、name、configuration），不包含密钥。
`PUT /api/admin/ai-configuration/channels/{id}` 接收 name 与 configuration（沿用原配置 DTO）。渠道编号范围 1–999999，version=0 创建，后续按渠道乐观锁更新，事务内记录审计。密钥留空仅复用该渠道密钥，新渠道必须输入密钥。
探测和测试请求可携带 channelId，空密钥仅复用指定渠道密钥。渠道 1 为固定默认渠道，兼容原单配置接口和旧模型 ID；其他渠道在 `/api/ai/models` 中以 `channelId::modelId` 暴露，调用时服务端解析并验证该渠道模型清单，发送上游前去掉渠道前缀。未配置渠道明确拒绝，不回退到其他供应商。新增迁移保留已有配置与密钥。当前支持 OpenAI-compatible 协议，不自动故障转移。切换渠道前需保存草稿。

平台全局配置位于 OA `/oa/system-config`，仅超级管理员（实时 SUPER_ADMIN + access:manage）可管理。聊天使用 `default` 时跟随服务端默认对话模型。Embedding、Rerank、OCR 来自各自的分类目录，工具权限和执行策略由原有鉴权流程管理。

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

### 合并分支后的既有库升级

本次合并保留双方已发布的迁移版本。若既有库已经执行 `V202611161000`，但还没有 AI 配置分支的 `V202611152015`、`V202611152200`、`V202611160900`，默认校验会提示存在尚未应用的较早版本。不要改名、修改历史脚本、删除 history 或执行 repair 来绕过。

先备份并核对目标库、校验和及待执行清单，在维护窗口仅为这次升级启用 Flyway 的 out-of-order。PowerShell 示例：

```powershell
cd backend
mvn spring-boot:run '-Dspring-boot.run.arguments=--spring.flyway.out-of-order=true'
```

确认迁移成功后停止此次临时进程，再用普通 `mvn spring-boot:run` 启动；不要把该临时开关加入 `.env` 或通用配置。此路径在一次性 PostgreSQL 中从合并前 `202611161000` 实际升级验证：应用上述三个较早迁移及 `V202611162130` 共 4 项，validate 成功、待迁移为零，恢复默认配置后重复 migrate 为零。空库仍直接按版本顺序迁移，不需要该开关。本次冲突处理没有升级或改写实际开发库。

## 模型目录与能力（2026-11-15）

Base URL 统一持久化并回显完整 `/v1` 前缀；省略时补齐。模型客户端显式使用 `/chat/completions` 路径，避免重复 `/v1`。新增迁移将旧地址和单模型配置升级为版本化地址与 JSONB 模型清单。

- `POST /api/admin/ai-configuration/models`：baseUrl、apiKey（可留空复用已保存密钥），请求当前草稿的 `{baseUrl}/models`。只允许超级管理员；不跟随重定向，连接 10 秒、读取 20 秒，响应最多 1 MiB / 5000 模型。只使用明确返回的 context_length、max_output_tokens、supports_images；缺失时以 32768/4096/false 初始化并要求管理员核对。
- `POST /api/admin/ai-configuration/test`：允许提交上述草稿字段及 model，保存前可逐个测试；不带 body 时测试已保存配置。流式收到真实非空文本才成功，60 秒超时，不回显上游错误或密钥。
- `PUT /api/admin/ai-configuration` 新增 models 数组：每项 id、contextWindow（1024–2097152）、maxOutputTokens（16–393216）、supportsImages。1–200 项，不重复，默认模型必须已启用，输出上限必须小于上下文。

2026-11-16：输出上限扩大到 DeepSeek 官方公布的 384K（393216）。输入框固定允许输入到 393216，不再用当前上下文减一限制编辑；草稿超过上下文时标红，保存时仍校验输出必须小于上下文，便于按任意顺序编辑两项。模型发现优先读取官方 `context_window`，兼容 `context_length`；不再将输出截到上下文的一半。已有保存值保持不变，管理员按实际上游能力调整后保存。参考：https://api-docs.deepseek.com/api/list-models/ 。
- `GET /api/ai/models`：所有已认证用户读取启用的模型与能力；不包含供应商地址、密钥或内部版本。聊天及「AI 使用设置」使用可搜索下拉。已禁用的旧会话模型请求会被拒绝，用户可改选默认或其他已启用模型。
- `GET /api/ai/models?category=EMBEDDING|RERANK|OCR`：读取对应分类启用的模型，使用带渠道编号的稳定引用。
- `PUT /api/settings/chat`：可选传入 `ocrModelRef`，设为 `default` 时使用默认 OCR 引擎。省略时保留原选择。

模型调用服务端按已保存名单重新验证，输出上限固定使用配置值。文本上下文预算使用 UTF-8 字节数加消息/图片预留的保守估算，超预算明确拒绝，不悄悄截断系统提示词或 Agent 计划；该预算并非上游分词器的精确 token 数，图片 token 仍由上游实际限制最终校验。图片开关控制向模型转发原图；关闭时沿用现有 OCR 文本路径，OCR 不可用则明确失败。设置不能让不支持视觉的上游模型获得视觉能力。

仅扩展平台管理员的模型配置能力，不把此配置注册为 Agent 工具，不新增其他协议或后台自动发现任务。
