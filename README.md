# AI WorkMate — 企业 AI 助手平台

> Spring Boot 3 + Spring AI + Next.js 14 全栈 AI Agent 项目  

## 项目结构

```
ai-workmate/
├── backend/                          # Java Spring Boot 后端
│   ├── pom.xml                       # Maven 依赖
│   └── src/main/
│       ├── java/com/aiworkmate/
│       │   ├── AiWorkMateApplication.java
│       │   ├── config/               # 配置类（AI/CORS/安全/MyBatis）
│       │   ├── controller/           # REST 控制器
│       │   │   ├── AuthController.java      # 登录注册
│       │   │   └── ChatController.java      # AI 对话（SSE流式）
│       │   ├── service/              # 业务逻辑层
│       │   │   ├── AuthService.java
│       │   │   ├── ChatService.java         # AI 对话核心
│       │   │   └── impl/
│       │   ├── mapper/               # MyBatis-Plus 数据访问
│       │   ├── entity/               # 数据库实体
│       │   ├── dto/                  # 请求/响应对象
│       │   ├── common/               # 统一响应 + 异常处理
│       │   └── util/                 # JWT 工具类
│       └── resources/
│           ├── application.yml       # 主配置
│           ├── application-dev.yml   # 开发环境
│           └── db/init.sql           # 数据库初始化
│
└── frontend/                         # Next.js 14 前端
    ├── package.json
    ├── next.config.js                # API 代理配置
    ├── tailwind.config.ts
    └── src/
        ├── app/
        │   ├── layout.tsx
        │   ├── page.tsx              # 主页面（登录/聊天）
        │   └── globals.css           # 全局样式 + Markdown
        ├── components/
        │   ├── ChatInterface.tsx      # 聊天主界面
        │   ├── MessageBubble.tsx      # 消息气泡（Markdown渲染+代码高亮）
        │   ├── Sidebar.tsx            # 侧边栏导航
        │   └── LoginPage.tsx          # 登录注册页
        ├── lib/
        │   └── api.ts                # API 客户端（SSE流式）
        ├── store/
        │   └── chatStore.ts          # Zustand 状态管理
        └── types/
            └── index.ts              # TypeScript 类型定义
```

## 快速开始

### 前置要求

- Java 17+
- Node.js 20+
- Maven 3.9+
- PostgreSQL 16+ (需安装 pgvector 扩展)
- Redis 7+

### 1. 启动基础设施

```bash
# 使用 Docker Compose 一键启动 PostgreSQL + Redis
docker compose -f docker-compose.yml up -d
```

### 2. 配置 AI API Key

获取 DeepSeek API Key（https://platform.deepseek.com），然后：

```bash
# 方式一：环境变量
export AI_API_KEY=sk-your-deepseek-api-key

# 方式二：修改 application.yml 中的 spring.ai.openai.api-key
```

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run
```

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

访问 http://localhost:3000

### 5. 测试 API

```bash
# 注册
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456"}'

# 登录
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456"}'

# AI 对话（需要先获取 token）
curl -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"message":"Hello!"}'
```

## 技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| 前端 | Next.js 14 + React 18 + TypeScript | App Router |
| 样式 | Tailwind CSS | 响应式 + 暗色模式 |
| 状态管理 | Zustand | 轻量级 |
| Markdown | react-markdown + react-syntax-highlighter | 代码高亮 |
| 后端 | Spring Boot 3.3 + Java 17 | REST API |
| AI | Spring AI + DeepSeek（OpenAI 兼容） | SSE 流式输出 |
| ORM | MyBatis-Plus 3.5 | 数据库操作 |
| 数据库 | PostgreSQL + pgvector | 向量检索（第2月） |
| 缓存 | Redis | 对话记忆（第2月） |
| 认证 | Spring Security + JWT | 无状态认证 |
| 部署 | Docker Compose + Nginx | 容器化（第4月） |

## 6 个月开发路线

```
第 1 月：Java 基础 + 第一个 AI 对话接口 ✅（当前阶段）
第 2 月：RAG 知识库 + 向量检索 + 文档解析
第 3 月：AI Agent Tool Calling + 前端流式聊天 UI
第 4 月：Multi-Agent + Docker 部署上线
第 5 月：AI 系统设计 + 简历面试准备
第 6 月：拿 Offer
```

## 模型切换

支持所有 OpenAI 兼容的模型：

```yaml
# DeepSeek（推荐，便宜）
spring.ai.openai.base-url: https://api.deepseek.com
spring.ai.openai.chat.options.model: deepseek-chat

# 通义千问
spring.ai.openai.base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
spring.ai.openai.chat.options.model: qwen-turbo

# OpenAI
spring.ai.openai.api-key: sk-xxx
spring.ai.openai.base-url: https://api.openai.com
spring.ai.openai.chat.options.model: gpt-4o-mini
```
