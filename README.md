# AI WorkMate — 企业 AI 助手平台


## Frontend Split

- ronted-main: marketing website, runs on http://localhost:3000.
- onted-oa: OA workbench, runs on http://localhost:3001/oa.
- The old single rontend app has been split into two independent programs.

> Spring Boot 3 + Spring AI + Vite 5 全栈 AI Agent 项目  

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
├── fronted-main/                     # 营销官网 Vite SPA，端口 3000
└── fonted-oa/                        # OA 工作台 Vite SPA，端口 3001
    ├── package.json
    ├── vite.config.ts                # API 代理配置
    ├── tailwind.config.ts
    └── src/
        ├── main.tsx                  # 应用入口
        ├── App.tsx                   # React Router 路由（/oa、/oa/:pageId）
        ├── components/               # 页面与业务组件（首页、OA 工作台等）
        ├── lib/                      # API 客户端
        ├── store/                    # Zustand 状态管理
        └── types/                    # TypeScript 类型定义
```

## 快速开始

### 前置要求

- Java 17+
- Node.js 20+
- Maven 3.9+
- PostgreSQL 16+ (需安装 pgvector 扩展)
- Redis 7+
- Python 3.10+（仅 OCR 图片识别服务需要，可选）

### 1. 启动基础设施

```bash
# 使用 Docker Compose 一键启动 PostgreSQL + Redis


## Frontend Split

- ronted-main: marketing website, runs on http://localhost:3000.
- onted-oa: OA workbench, runs on http://localhost:3001/oa.
- The old single rontend app has been split into two independent programs.
docker compose -f docker-compose.yml up -d
```

### 2. 配置 AI API Key

获取 DeepSeek API Key（https://platform.deepseek.com），然后：

```bash
# 方式一：环境变量


## Frontend Split

- ronted-main: marketing website, runs on http://localhost:3000.
- onted-oa: OA workbench, runs on http://localhost:3001/oa.
- The old single rontend app has been split into two independent programs.
export AI_API_KEY=sk-your-deepseek-api-key

# 方式二：修改 application.yml 中的 spring.ai.openai.api-key


## Frontend Split

- ronted-main: marketing website, runs on http://localhost:3000.
- onted-oa: OA workbench, runs on http://localhost:3001/oa.
- The old single rontend app has been split into two independent programs.
```

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run
```

### 4. 启动前端

```bash
cd fronted-main
npm install
npm run dev
```

访问 http://localhost:3000 和 http://localhost:3001/oa

### 5. 测试 API
```bash
# 注册


## Frontend Split

- ronted-main: marketing website, runs on http://localhost:3000.
- onted-oa: OA workbench, runs on http://localhost:3001/oa.
- The old single rontend app has been split into two independent programs.
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456"}'

# 登录


## Frontend Split

- ronted-main: marketing website, runs on http://localhost:3000.
- onted-oa: OA workbench, runs on http://localhost:3001/oa.
- The old single rontend app has been split into two independent programs.
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456"}'

# AI 对话（需要先获取 token）


## Frontend Split

- ronted-main: marketing website, runs on http://localhost:3000.
- onted-oa: OA workbench, runs on http://localhost:3001/oa.
- The old single rontend app has been split into two independent programs.
curl -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"message":"Hello!"}'
```

### 6. 启动 OCR 识别服务（可选，图片/扫描版 PDF 对话与知识库需要）

聊天上传图片或扫描版 PDF 时，纯文本模型（deepseek-v4-flash / pro）需要 OCR 提取
文字；知识库也支持图片与扫描版 PDF 通过 OCR 入库（RAG）。
OCR 微服务位于 `deploy/ocr-service`（PaddleOCR PP-OCRv4 + PyMuPDF，接口契约见
`docs/architecture/ocr-chat-pipeline.md`）：

> 提示：使用一键启动脚本（`start.bat`）时无需手动安装依赖。
> 首次运行会询问是否自动创建 venv 并安装依赖，之后每次启动自动拉起 OCR 服务。

```bash
cd deploy/ocr-service
python -m venv .venv
.venv/Scripts/activate            # Windows
# source .venv/bin/activate       # Linux / macOS

pip install -r requirements.txt   # 国内加速: -i https://pypi.tuna.tsinghua.edu.cn/simple

uvicorn app:app --host 0.0.0.0 --port 8686
```

- 首次识别会自动下载 PP-OCRv4 模型（约 40MB）并完成加载，之后单张约 1~3 秒（CPU）。
- 可选环境变量：`OCR_SERVICE_API_KEY`（开启鉴权，需与后端 `OCR_API_KEY` 一致）、
  `OCR_USE_GPU=1`（GPU 加速）、`OCR_MAX_PAGES=20`（单个 PDF 最多识别页数）。
- 后端默认连接 `http://127.0.0.1:8686`，无需额外配置；OCR 不可用时图片对话会
  明确返回 `OCR_CAPABILITY_UNAVAILABLE`，不会静默失败。
- Docker 部署时由 `docker compose` 自动启动 `ocr-service`，无需手动安装。

## 技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| 前端 | Vite 5 + React 18/19 + TypeScript | 独立 Vite SPA（3000 / 3001） |
| 样式 | Tailwind CSS | 响应式 + 暗色模式 |
| 状态管理 | Zustand | 轻量级 |
| Markdown | react-markdown + react-syntax-highlighter | 代码高亮 |
| 后端 | Spring Boot 3.3 + Java 17 | REST API |
| AI | Spring AI + DeepSeek（OpenAI 兼容） | SSE 流式输出 |
| OCR | PaddleOCR PP-OCRv4 + PyMuPDF（FastAPI 微服务） | 图片 / 扫描版 PDF 文字提取 |
| ORM | MyBatis-Plus 3.5 | 数据库操作 |
| 数据库 | PostgreSQL + pgvector | 向量检索（第2月） |
| 缓存 | Redis | 对话记忆（第2月） |
| 认证 | Spring Security + JWT | 无状态认证 |
| 部署 | Docker Compose + Nginx | 容器化（第4月） |

## 模型切换

支持所有 OpenAI 兼容的模型：

```yaml
# DeepSeek（推荐，便宜）


## Frontend Split

- ronted-main: marketing website, runs on http://localhost:3000.
- onted-oa: OA workbench, runs on http://localhost:3001/oa.
- The old single rontend app has been split into two independent programs.
spring.ai.openai.base-url: https://api.deepseek.com
spring.ai.openai.chat.options.model: deepseek-chat

# 通义千问


## Frontend Split

- ronted-main: marketing website, runs on http://localhost:3000.
- onted-oa: OA workbench, runs on http://localhost:3001/oa.
- The old single rontend app has been split into two independent programs.
spring.ai.openai.base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
spring.ai.openai.chat.options.model: qwen-turbo

# OpenAI


## Frontend Split

- ronted-main: marketing website, runs on http://localhost:3000.
- onted-oa: OA workbench, runs on http://localhost:3001/oa.
- The old single rontend app has been split into two independent programs.
spring.ai.openai.api-key: sk-xxx
spring.ai.openai.base-url: https://api.openai.com
spring.ai.openai.chat.options.model: gpt-4o-mini
```
