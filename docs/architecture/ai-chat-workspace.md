# AI Chat Workspace 架构、交付与迭代计划

## 1. 目标与边界

AI Chat Workspace 是 OA 左侧菜单中的独立工作页面，路由为 `/oa/ai-workspace`。它负责多轮聊天、会话管理、附件理解和内容生成，不替代 OA 业务权限系统。

- 聊天、会话、消息和附件接口必须携带 JWT。
- `userId`、角色和资源所有权只从服务端认证上下文获取，不接受前端声明。
- 聊天模型可以分析和建议，但不能声称已完成审批、付款、删除、权限修改或敏感导出。
- OA 写操作继续使用 `/api/ai/tasks/plan` 与 `/api/ai/tasks/execute`，执行前重新鉴权，高风险动作人工确认。
- 未连接真实业务工具时返回能力不可用，不提供 mock 成功。
- 工作空间提供“对话 / OA 操作”双模式。OA 操作模式只暴露代码注册、租户启用且当前用户实时有权使用的固定工具；执行结果来自 Tool Gateway 调用的真实领域服务。
- L0 只读计划可以直接执行；L1/L2 单步写计划必须先展示冻结计划并由用户确认，再签发仅绑定该计划的一次性确认凭证。

## 2. 技术方案

### 2.1 前端

- Vite 5 SPA、React 18/19、TypeScript、Zustand、Ant Design。
- `react-markdown` 渲染 Markdown，`react-syntax-highlighter` 渲染代码块。
- 状态按会话分桶，支持切换会话时保持其他会话的生成任务。
- 每个生成会话拥有独立 `AbortController`，停止操作不会影响其他会话。
- 设置保存在 `workmeta-ai-chat-settings`，API Key 和接口地址不进入浏览器。

### 2.2 后端

- Spring Boot 3.3、Java 17、Spring Security、Spring AI、MyBatis-Plus。
- PostgreSQL 持久化 `conversation`、`message`、`attachment`。
- Apache Tika 3.3.1 检测真实 MIME 并解析 PDF、Word、Excel、CSV、Markdown 和文本。
- 图片通过 Spring AI multimodal media 发送给支持视觉能力的模型。
- 文档解析文本在上传时写入 `attachment.extracted_text`，后续提问直接复用。
- 多轮上下文按会话从数据库读取最近 N 轮，支持重启和多实例部署。

## 3. 项目目录

```text
fonted-oa/src/
├── components/ai-chat/
│   ├── AiChatWorkspace.tsx
│   ├── ChatSidebar.tsx
│   ├── ChatWindow.tsx
│   ├── MessageList.tsx
│   ├── MessageItem.tsx
│   ├── ChatInput.tsx
│   ├── AttachmentPreview.tsx
│   ├── SettingsDialog.tsx
│   └── WorkspaceAuthGate.tsx
├── lib/chatApi.ts
├── store/aiChatStore.ts
└── types/chat.ts

backend/src/main/java/com/aiworkmate/
├── controller/AttachmentController.java
├── controller/ChatController.java
├── controller/ConversationController.java
├── service/AttachmentService.java
├── service/ChatService.java
├── service/ConversationService.java
├── service/FileParserService.java
├── service/impl/AttachmentServiceImpl.java
├── service/impl/ChatServiceImpl.java
├── service/impl/ConversationServiceImpl.java
├── service/impl/TikaFileParserServiceImpl.java
├── entity/Attachment.java
├── entity/Conversation.java
└── entity/Message.java
```

## 4. API 契约

| 方法 | 路径 | 用途 | 鉴权 |
|---|---|---|---|
| GET | `/api/conversations?search=` | 搜索并按最近更新时间返回会话 | JWT + userId 过滤 |
| POST | `/api/conversations` | 新建会话 | JWT |
| PATCH | `/api/conversations/{id}` | 重命名本人会话 | JWT + owner |
| DELETE | `/api/conversations/{id}` | 删除本人会话、消息和附件 | JWT + owner |
| GET | `/api/conversations/{id}/messages` | 加载完整消息和附件 | JWT + owner |
| PATCH | `/api/conversations/messages/{id}/feedback` | 点赞、点踩或取消 | JWT + owner |
| POST | `/api/attachments` | 上传并解析附件 | JWT + conversation owner |
| GET | `/api/attachments/{id}/content` | 读取图片或文件 | JWT + attachment owner |
| POST | `/api/chat` | 非流式聊天 | JWT + conversation/attachment owner |
| POST | `/api/chat/stream` | SSE 流式聊天 | JWT + conversation/attachment owner |
| POST | `/api/conversations/{id}/agent-results/{taskId}` | 将已完成的受控 Agent 任务转为可读回复并写入会话 | JWT + 会话/任务所有权 + 实时页面与工具权限 + 成功任务校验 |

流式事件类型为 `metadata`、`delta`、`done`、`error`。错误事件包含 `errorCode` 和 `traceId`。

## 5. 数据模型

- **Conversation**：`id`、`userId`、`title`、`model`、`createdAt`、`updatedAt`。
- **Message**：`id`、`conversationId`、`role`、`content`、`status`、`feedback`、`tokenCount`、`sourceTaskNo`、`sourceToolCode`、`createdAt`。两个来源字段只由服务端写入，用于证明任务来源并选择受权限保护的业务页面入口。
- **Attachment**：`id`、`userId`、`conversationId`、`messageId`、`type`、`name`、`storageName`、`size`、`mimeType`、`extractedText`、`createdAt`。

原始文件名仅用于展示；磁盘文件名使用 UUID。附件在发送时绑定消息，已绑定附件不能被其他消息重复引用。

## 6. 安全设计

1. 所有聊天资源默认受 Spring Security 保护。
2. 会话、消息、附件操作同时匹配认证用户 ID，阻止水平越权。
3. 上传限制：图片 10MB，其他文件 20MB，单消息最多 10 个附件。
4. 服务端检测真实 MIME，不信任浏览器 `Content-Type`。
5. 存储路径由配置根目录和 UUID 组成，读取时再次校验规范化路径。
6. API Key 仅由 `AI_API_KEY` 注入；设置页不读取或保存密钥。
7. 系统提示词禁止权限提升和伪造业务执行结果。
8. 删除会话只删除当前用户拥有的资源，并同步清理物理附件。
9. OA 操作先规划并通过 Tool Gateway 执行；结果接口只读取属于当前用户、来源页面为 `ai-workspace` 且状态成功的冻结任务，不直接调用工具处理器，也不接受前端传入的结果。
10. 写任务仍受一个任务最多一个写步骤、实时权限、确认凭证、Kill Switch、领域状态机和业务审计约束；结果落入聊天不等于重新执行。
11. 结果写入在数据库行锁和唯一索引下幂等；越权、跨租户、权限已回收、未完成任务及当前已不可用工具均拒绝，不把模型文本当作成功凭据。

## 7. 当前交付状态

### 已完成

- OA 左侧菜单和独立聊天页面。
- JWT 登录门禁，OA 端口可独立获取 token。
- 新建、选择、搜索、重命名、删除、自动排序会话。
- 消息持久化、发送状态、失败状态、重试、重新生成、复制和反馈。
- SSE 流式输出、停止生成、多会话并发生成。
- 图片/文件选择、粘贴、拖拽、缩略图与消息附件绑定。
- PDF、Word、Excel、CSV、Markdown、文本解析缓存。
- Markdown、代码高亮、亮暗主题和移动端会话抽屉。
- 模型、上下文轮数、流式开关和清空记录设置。
- AI 工作空间统一接入全部代码注册工具上界；实际列表继续按租户策略、工具开关、角色授权、业务权限和数据范围收窄。
- 只读任务自动执行并展示经过限量、转义的真实结果；写任务显示计划卡片，经独立确认后执行。回复提供有权限时的对应业务页面入口。

### 外部依赖

- 需要 PostgreSQL、Redis 和后端服务可访问。
- 需要有效 `AI_API_KEY`、`AI_BASE_URL` 和 `AI_MODEL`。
- 图像识别要求配置的模型本身支持视觉输入。
- 新库和既有数据库均由 Flyway 自动迁移；本次会话结果来源字段由 `V202609281000__chat_agent_result_source.sql` 增加，不修改历史迁移。

### 有意保留的边界

- 浏览器不能编辑或读取 API Key、内部 AI 网关地址。
- Chat Workspace 的 OA 操作模式复用受控 plan/confirmation/execute 链路；聊天结果接口只持久化已经完成的任务结果，绝不直接写业务数据。
- 写工具平台和租户开关默认关闭，必须通过独立发布门开启；永久禁止能力不会因工作空间入口或用户确认而开放。
- 附件当前使用本地磁盘，生产环境应迁移对象存储并增加病毒扫描。

## 8. 启动方式

```powershell
docker compose -f docker-compose.yml up -d

$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot'
$env:AI_API_KEY = '<provider-key>'
$env:AI_BASE_URL = 'https://api.deepseek.com'
$env:AI_MODEL = 'deepseek-chat'
cd backend
C:\tools\apache-maven-3.9.9\bin\mvn.cmd spring-boot:run

cd ..\fonted-oa
npm install
npm run dev
```

访问 `http://localhost:3001/oa/ai-workspace`。生产配置参考根目录 `.env.example`。

## 9. 后续迭代计划

| 阶段 | 状态 | 目标 | 验收重点 |
|---|---|---|---|
| A. Chat Workspace 基线 | DONE | 对话、会话、附件、解析、鉴权、SSE 闭环 | 越权测试、停止生成、重启后恢复 |
| B. 附件生产化 | NOT_STARTED | MinIO/S3、病毒扫描、异步解析、配额 | 大文件、恶意文件、失败重试 |
| C. OA Tool Calling | IN_PROGRESS | 42 页面能力清单、固定工具白名单、工作空间统一入口、确认与审计 | 普通员工越权、幂等、结果呈现、全工具回归 |
| D. RAG 知识库 | NOT_STARTED | 文档入库、分块、向量检索、引用 | tenant/user 过滤、来源与评分 |
| E. 多模型与路由 | NOT_STARTED | 模型注册表、能力标签、成本和降级策略 | 视觉/文本能力匹配、限流 |
| F. 多 Agent | NOT_STARTED | Supervisor、领域 Worker、任务恢复 | 最大轮次、权限继承、全链路 trace |

优先顺序为 B → C → D → E → F。RAG 检索必须先按租户和用户权限过滤；多 Agent 只能继承当前用户权限，不能拥有独立的隐式超级权限。
