# OCR 识别接入（图片 / 扫描版 PDF → 纯文本 LLM）

## 目标

AI WorkMate 的聊天支持上传图片附件，知识库支持上传图片与扫描版 PDF。当前
`TikaFileParserServiceImpl` 对图片与无文本层的 PDF 只能通过 OCR 提取文字：
`ChatServiceImpl` 在纯文本模型（`deepseek-v4-flash` / `deepseek-v4-pro`）下把 OCR
文本注入 system prompt（伪多模态），多模态模型仍走 `user.media()` 原图直传；知识库
上传则把 OCR 文本作为文档内容分块向量化。

本方案只允许通过服务端环境变量接入 OCR 引擎，API Key 不进入前端或数据库；OCR
服务不可用时明确报错，不存在本地 mock 或失败后伪造识别成功。

## 技术选型：PaddleOCR 本地 HTTP 微服务

### 候选对比

| 方案 | 中文准确率 | Java 集成成本 | 成本 | 数据安全 | 结论 |
| --- | --- | --- | --- | --- | --- |
| PaddleOCR PP-OCRv4 本地 HTTP 微服务 | 93~97% | 低（HTTP，与现有 `LocalEmbeddingServiceImpl` 同款架构） | 免费，GPU 可选 | 数据不出内网 | 推荐 |
| Tesseract 5 + tess4j | 85~92% | 最低（JVM 进程内） | 免费 | 不出内网 | 备选 |
| 云 OCR API（百度/腾讯/阿里） | 98%+ | 低 | 按量收费 | 图片出境 | 不推荐 |

### 选型理由

1. **中文识别效果本地最强**：PP-OCRv4 中文场景在公开评估上明显优于 Tesseract
   5，与企业 OA 场景（中文单据、截图、表格）匹配；企业数据不因 OCR 而离开内网，
   规避云 OCR 的数据出境与合规问题。
2. **与项目既有架构模式一致**：嵌入能力已采用「本地 HTTP 模型
   `Qwen3-Embedding-0.6B` 或 OpenAI 兼容 API」双模式（见
   `docs/architecture/embedding-pgvector.md`），OCR 微服务沿用同一模式：
   Java 侧只依赖一个 HTTP 客户端，Python 侧独立部署、可按需加载模型、可选 GPU
   加速，不向 JVM 注入原生库依赖（避免 tess4j 的 JNI/系统库维护成本）。
3. **诚实降级符合工程规范**：OCR 服务未就绪时上传仍可保存图片（多模态模型路径
   不受影响），纯文本模型对话引用图片时返回稳定错误码，不静默丢图、不伪造文本。
4. **独立演进**：OCR 引擎、预处理或版面分析升级只影响 Python 服务，Java 契约
   （`OcrService`）不变；将来接云 OCR 或换引擎只需换一个实现。

## 架构总览

```text
用户 ──上传图片/扫描版 PDF──> AttachmentController / KnowledgeController
                                    │
                                    ▼
                         TikaFileParserServiceImpl.parse()
       图片分支 / 无文本层 PDF 分支 ──> OcrService（HTTP）
                                            │ POST /ocr/recognize
                                            ▼
                            PaddleOCR 微服务（FastAPI，内网 :8686）
                                            │ PP-OCRv4 + PyMuPDF
                                            ▼
                            返回逐行文本（PDF 按页拼接，`[Page N]` 分隔）
                                            │
                                            ▼
                     attachment.extracted_text / 知识库文档内容（零 DDL）
                                            │
                                            ▼
            聊天：ChatServiceImpl.buildSystemPrompt 附件文本注入（伪多模态）
            知识库：KnowledgeChunker 分块 → embedding → pgvector 检索
```

## OCR 微服务契约（Python 侧，内部接口）

```http
POST /ocr/recognize
Content-Type: application/octet-stream
X-API-Key: <服务端配置，可选>
Body: 图片二进制（jpeg / png / webp，≤ 10MB）
      或 PDF 二进制（≤ 30MB，≤ OCR_MAX_PAGES 页，默认 20）

200 {
  "text": "识别全文（按视觉行序，行间以换行分隔；PDF 按页拼接并带 [Page N] 标记）",
  "blocks": [
    { "text": "...", "confidence": 0.98, "box": [x1, y1, x2, y2, x3, y3, x4, y4] }
  ],
  "pageCount": 1,
  "language": "ch",
  "engine": "ppocr-v4",
  "latencyMs": 152
}

400 非图片/PDF、读取失败、PDF 页数超限
429 超过并发上限
503 模型未加载 / 引擎不可用
```

约定：

- `text` 是 Java 侧唯一必读字段；`blocks` 仅供前端展示，PDF 场景返回空列表。
- PDF 识别流程：PyMuPDF 按 2x 缩放将每页渲染为位图 → 逐页 PaddleOCR → 文本以
  `[Page N]` 标记拼接，知识库分块与引用可据此保留页码信息。
- `confidence` 低于服务端 `min-confidence` 的 block 不出现在 `text` 中（由 Python
  侧过滤，Java 侧不再二次过滤）。
- 文件为空白或无可识别文本时返回 `200` 且 `text` 为空字符串，不报错。
- 微服务启动时懒加载 PP-OCRv4 检测+识别模型；`GET /healthz` 返回模型就绪状态。
- 环境变量：`OCR_SERVICE_API_KEY`、`OCR_USE_GPU`、`OCR_MIN_CONFIDENCE`、
  `OCR_MAX_PAGES`（单个 PDF 最多 OCR 页数）。

## Java 后端接口

### 新增 `OcrService`（接口）

```java
public interface OcrService {
    /** 识别图片文本；引擎不可用或识别结果为空时返回 null */
    String recognize(Path imageFile, String filename);
    /** 引擎是否可用（健康检查、前端状态展示用） */
    boolean isAvailable();
}
```

### 新增 `HttpOcrClient`（实现）

- 基于 `RestClient`/`WebClient` 调用 `POST /ocr/recognize`，携带 `OcrProperties`。
- 超时、非 200、响应解析失败均按「不可用」处理并记录 WARN 日志（不含图片内容）。
- 3xx/5xx 不重试（识别幂等但成本高，避免拖慢上传链路）。

### 新增 `OcrProperties`

```yaml
app:
  ocr:
    enabled: true                      # false 时视为引擎不可用
    base-url: http://127.0.0.1:8686
    api-key: ${OCR_API_KEY:}
    timeout: 30s                       # CPU 单张约 1~3s，留裕量
    min-confidence: 0.6                # 低于阈值的 block 丢弃（Python 侧执行）
```

环境变量模板同步写入 `.env.example` 与 `.env.docker.example`。

### 修改 `TikaFileParserServiceImpl`

```java
if (IMAGE_TYPES.contains(mimeType)) {
    String text = ocrService == null || !ocrService.isAvailable()
            ? null : ocrService.recognize(path, filename);
    return new ParsedFile(mimeType, text, true);
}
if (isPdf(mimeType)) {
    String text = tika.parseToString(path).strip();
    if (text.isBlank()) {
        // 扫描版 PDF 无文本层：交由 OCR 服务渲染页面逐页识别
        String ocrText = ocrService.recognize(path, filename);
        if (ocrText == null || ocrText.isBlank()) {
            throw new IOException("No extractable text");
        }
        return new ParsedFile(mimeType, limit(ocrText), false);
    }
    return new ParsedFile(mimeType, limit(text), false);
}
```

- 图片分支在 OCR 不可用或识别为空时保持 `extractedText=null`，**上传不阻塞**：
  多模态模型仍可用原图，纯文本模型在对话时才明确报错。
- PDF 分支：默认优先使用文本层（带文字层的 PDF 不消耗 OCR 资源）；仅当文本层为空
  （扫描版）才整份 PDF 走 OCR。OCR 仍无文本时保持原失败语义（上传报错）。
- 用户可开启「PDF 始终 OCR」（`PUT /api/settings/ocr`，按 userId 存入 `user_setting`
  表）：强制模式下所有 PDF 都先走 OCR，OCR 失败/无文本时回退文本层，两者皆空才报错，
  避免上传因 OCR 抖动失败。
- `ParsedFile` 与 `attachment` 表结构不变（复用 `extracted_text`）。

## 用户 OCR 设置接口（前端设置页可调）

```http
GET /api/settings/ocr          # 返回 { forcePdfOcr: boolean }
PUT /api/settings/ocr          # body { forcePdfOcr: boolean }，保存后立即可用
```

- 开关按用户维度存储（`user_setting` 表，key = `ocr.forcePdfOcr`），聊天附件与
  知识库上传共用；接口受 JWT 保护，写入当前登录用户自己的设置。
- 前端入口：AI Workspace 设置对话框（SettingsDialog）新增「PDF 文件始终通过 OCR
  识别」Switch，打开对话框时拉取、保存时提交。

### 修改 `AiModelCatalog`

```java
/** 模型是否支持直接接收图片（多模态） */
public static boolean isMultimodal(String model) {
    // deepseek-v4-flash / deepseek-v4-pro 返回 false；
    // 将来接入 qwen-vl 等视觉模型时在此登记
}
```

### 修改 `ChatServiceImpl.buildPrompt`

```java
List<Attachment> images = attachments.stream()
        .filter(item -> "image".equals(item.getType())).toList();
if (AiModelCatalog.isMultimodal(selectedModel)) {
    images.forEach(image -> addImage(user, image));        // 原图直传（现状）
} else {
    // 不调 media()；图片 OCR 文本已在 buildSystemPrompt 按附件注入（现有逻辑）
    // 若存在 extractedText 为 null 的图片 → 抛 OCR_CAPABILITY_UNAVAILABLE
}
```

- `buildSystemPrompt` 的附件文本注入逻辑（`MAX_ATTACHMENT_CONTEXT` 截断）完全复用，
  零改动；图片 OCR 文本与 PDF/Word 附件同路径。
- 纯文本模型 + 无 OCR 文本的图片：请求失败并返回 `OCR_CAPABILITY_UNAVAILABLE`，
  不携带该图片继续对话，防止模型对缺失内容产生幻觉。

### 新增错误码

`ErrorCode` 增加 `OCR_CAPABILITY_UNAVAILABLE`，纳入 `GlobalExceptionHandler` 现有
错误码映射，前端沿用统一错误展示与 traceId。

## 模型路由规则

| 场景 | 处理 |
| --- | --- |
| 多模态模型 + 图片 | `user.media()` 原图直传 |
| 纯文本模型 + 图片（有 OCR 文本） | 文本注入 system prompt，图片不直传（伪多模态） |
| 纯文本模型 + 图片（无 OCR 文本） | 返回 `OCR_CAPABILITY_UNAVAILABLE` |
| 扫描版 PDF 附件（OCR 兜底成功） | 按普通附件文本注入，与 PDF/Word/TXT 同路径 |
| 非图片附件（PDF/Word/TXT） | 现有 `extractedText` 注入，不受影响 |

## 知识库 OCR（RAG）

- 知识库上传允许图片（jpeg/png/webp）与扫描版 PDF：`KnowledgeServiceImpl.upload`
  通过 `FileParserService` 拿到 OCR 文本后按文档流程分块、embedding、入库；
  `FILE_TYPE_BY_MIME` 已登记 `JPG/PNG/WEBP`。
- 图片 OCR 无结果时返回 `error.knowledge_image_no_text`（三语言资源已同步），不会
  静默入库空文档。
- 前端上传控件 `accept` 已包含图片扩展名，拖拽提示说明图片自动 OCR。

## 前端改动

- `AttachmentResponse.hasContent` 已等于 `image || extractedText != null`：OCR 成功后
  图片自动有内容，前端类型无需变更。
- `fonted-oa/src/config/aiModels.ts`：模型条目增加 `multimodal: boolean` 标记，与
  后端 `AiModelCatalog.isMultimodal` 保持一致（仅用于界面提示）。
- `AttachmentPreview.tsx`：图片附件有 `extractedText` 时展示「已通过 OCR 解析」小
  标签（可选，不改变现有交互）。
- 设置页/对话错误展示复用现有 `OCR_CAPABILITY_UNAVAILABLE` 文案与 traceId 面板。

## 数据库

零 DDL。附件沿用 `attachment.extracted_text`，与文档附件共用同一列；知识库沿用
`knowledge_document.content`（OCR 文本作为文档内容分块存储）；不需要新增引擎或
置信度列，避免过度设计。

## 错误与降级策略

- OCR 微服务不可用：聊天图片上传成功（图片可预览），纯文本模型对话引用该图时
  明确报错；知识库图片上传返回 `error.knowledge_image_no_text`。
- 识别结果为空（空白图/纯装饰图）：`text=""`，对话时按无 OCR 文本处理并提示。
- PDF 页数超过 `OCR_MAX_PAGES`（默认 20）：OCR 服务返回 400，Java 侧按解析失败
  报错，避免 CPU 长尾占用。
- 超时/429/503：全部按不可用处理，不重试、不缓存失败结果。
- 多模态模型路径永远不受 OCR 可用性影响。

## 验证计划

1. 单元测试（Mock `OcrService`）：
   - 纯文本模型：图片 OCR 文本注入 prompt，不调用 `media()`；
   - 多模态模型：走 `media()`，不注入 OCR 文本；
   - OCR 不可用 + 纯文本模型：返回 `OCR_CAPABILITY_UNAVAILABLE`；
   - 超时/5xx：按不可用降级，上传仍成功；
   - 扫描版 PDF（无文本层）：走 OCR 兜底，OCR 文本进入 `extractedText`；
   - OCR 仍无文本的扫描版 PDF：保持解析失败语义；
   - 知识库：图片有 OCR 文本 → 入库成功（fileType=PNG）；图片无 OCR 文本 → 拒绝。
2. 集成测试：本地启动 PaddleOCR 微服务，上传中文截图与扫描版 PDF → 对话与知识库
   验证识别内容进入回答/检索。
3. 回归：带文本层 PDF/Word 附件（不消耗 OCR）、知识库普通文档、多模态路径
   （如配置视觉模型）不受影响。
4. 验证命令：`cd backend && mvn test`；两个前端 `npm run lint && npm run build`；
   `python -m py_compile docker/ocr-service/app.py`。

## 实施清单

### 后端

- `config/OcrProperties.java`（新增）
- `service/OcrService.java`（新增）
- `service/impl/HttpOcrClient.java`（新增）
- `service/impl/TikaFileParserServiceImpl.java`（修改：图片分支调 OCR + PDF 扫描版兜底）
- `service/model/AiModelCatalog.java`（修改：新增 `isMultimodal`）
- `service/impl/ChatServiceImpl.java`（修改：模型路由分流）
- `service/impl/KnowledgeServiceImpl.java`（修改：允许图片入库、图片类型映射、空文本错误 key）
- `common/ErrorCode.java`（修改：新增 `OCR_CAPABILITY_UNAVAILABLE`）
- `i18n/messages*.properties`（修改：新增 `error.knowledge_image_no_text`）
- `application*.yml`、`.env.example`、`.env.docker.example`（修改：`app.ocr.*`、`OCR_MAX_PAGES`）

### Python 微服务（`docker/ocr-service/`）

- `app.py`：FastAPI + PaddleOCR（PP-OCRv4）+ PyMuPDF（PDF 逐页渲染 OCR），
  懒加载模型，`/ocr/recognize`、`/healthz`
- `requirements.txt`（新增 `PyMuPDF`）、`Dockerfile`
- `scripts/install-ocr.ps1`：本地可选安装到用户指定目录，安装产物不进入 Git
- `docker-compose.yml`：`ocr-service` 位于可选 `ocr` profile，注入 `OCR_MAX_PAGES`
  （内网暴露，不映射公网）

### 前端（`fonted-oa`）

- `src/config/aiModels.ts`（修改：多模态标记）
- `src/components/ai-chat/AttachmentPreview.tsx`（可选：OCR 标签）
- `src/components/oa/KnowledgeBaseDetail.tsx`（修改：上传 `accept` 增加图片扩展名）
- `src/i18n/locales/{zh-CN,en-US}/knowledge.ts`（修改：拖拽提示支持图片 OCR）
