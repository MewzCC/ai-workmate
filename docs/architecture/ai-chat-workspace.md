# AI Chat Workspace 架构、交付与迭代计划

## 1. 目标与边界

AI Chat Workspace 是 OA 左侧菜单中的独立工作页面，路由为 `/oa/ai-workspace`。它负责多轮聊天、会话管理、附件理解和内容生成，不替代 OA 业务权限系统。

- 聊天、会话、消息和附件接口必须携带 JWT。
- `userId`、角色和资源所有权只从服务端认证上下文获取，不接受前端声明。
- 聊天模型可以分析和建议，但不能声称已完成审批、付款、删除、权限修改或敏感导出。
- OA 写操作继续使用 `/api/ai/tasks/plan` 与 `/api/ai/tasks/execute`，执行前重新鉴权，高风险动作人工确认。
- 未连接真实业务工具时返回能力不可用，不提供 mock 成功。

## 2. 技术方案

### 2.1 前端

- Next.js 14、React 18、TypeScript、Zustand、Ant Design。
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

流式事件类型为 `metadata`、`delta`、`done`、`error`。错误事件包含 `errorCode` 和 `traceId`。

## 5. 数据模型

- **Conversation**：`id`、`userId`、`title`、`model`、`createdAt`、`updatedAt`。
- **Message**：`id`、`conversationId`、`role`、`content`、`status`、`feedback`、`tokenCount`、`createdAt`。
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

### 外部依赖

- 需要 PostgreSQL、Redis 和后端服务可访问。
- 需要有效 `AI_API_KEY`、`AI_BASE_URL` 和 `AI_MODEL`。
- 图像识别要求配置的模型本身支持视觉输入。
- 新库和既有数据库统一执行 `backend/src/main/resources/db/init.sql`；该文件已包含 AI Chat Workspace 表结构与兼容升级。

### 有意保留的边界

- 浏览器不能编辑或读取 API Key、内部 AI 网关地址。
- Chat Workspace 当前不直接调用 OA 写工具；页面操作仍进入受控 plan/execute 链路。
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
| C. OA Tool Calling | NOT_STARTED | 页面能力协议、工具白名单、确认与审计 | 普通员工越权、幂等、回滚 |
| D. RAG 知识库 | NOT_STARTED | 文档入库、分块、向量检索、引用 | tenant/user 过滤、来源与评分 |
| E. 多模型与路由 | NOT_STARTED | 模型注册表、能力标签、成本和降级策略 | 视觉/文本能力匹配、限流 |
| F. 多 Agent | NOT_STARTED | Supervisor、领域 Worker、任务恢复 | 最大轮次、权限继承、全链路 trace |

优先顺序为 B → C → D → E → F。RAG 检索必须先按租户和用户权限过滤；多 Agent 只能继承当前用户权限，不能拥有独立的隐式超级权限。
