# OCR 图片识别接入（图片 → 纯文本 LLM）

## 目标

AI WorkMate 的聊天支持上传图片附件。当前 `TikaFileParserServiceImpl` 对图片只标记
`image=true` 并返回空文本，`ChatServiceImpl` 只能通过 `user.media()` 原图直传——这条
路径仅对多模态模型有效。本项目默认模型（`deepseek-v4-flash` / `deepseek-v4-pro`）为
纯文本模型，图片必须先在服务端做 OCR 提取文本，再以附件文本形式注入提示词，才能
被理解。

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
用户 ──上传图片──> AttachmentController
                        │
                        ▼
             TikaFileParserServiceImpl.parse()
             图片分支 ──> OcrService（HTTP）
                              │ POST /ocr/recognize
                              ▼
                    PaddleOCR 微服务（FastAPI，内网 :8686）
                              │ PP-OCRv4 模型
                              ▼
                  返回逐行文本 + 分块置信度
                        │
                        ▼
             attachment.extracted_text（复用现有字段，零 DDL）
                        │
                        ▼
             ChatServiceImpl.buildSystemPrompt 附件文本注入（现有逻辑）
```

## OCR 微服务契约（Python 侧，内部接口）

```http
POST /ocr/recognize
Content-Type: application/octet-stream
X-API-Key: <服务端配置，可选>
Body: 图片二进制（jpeg / png / webp，≤ 10MB）

200 {
  "text": "识别全文（按视觉行序，行间以换行分隔）",
  "blocks": [
    { "text": "...", "confidence": 0.98, "box": [x1, y1, x2, y2, x3, y3, x4, y4] }
  ],
  "language": "ch",
  "engine": "ppocr-v4",
  "latencyMs": 152
}

400 非图片或读取失败
429 超过并发上限
503 模型未加载 / 引擎不可用
```

约定：

- `text` 为 blocks 按视觉行序（从上到下、从左到右）拼接的纯文本，是 Java 侧唯一
  必读字段；`blocks` 仅供前端展示或将来做版面还原。
- `confidence` 低于服务端 `min-confidence` 的 block 不出现在 `text` 中（由 Python
  侧过滤，Java 侧不再二次过滤）。
- 图片为空白或无可识别文本时返回 `200` 且 `text` 为空字符串，不报错。
- 微服务启动时懒加载 PP-OCRv4 检测+识别模型；`GET /healthz` 返回模型就绪状态。

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
```

- 图片分支在 OCR 不可用或识别为空时保持 `extractedText=null`，**上传不阻塞**：
  多模态模型仍可用原图，纯文本模型在对话时才明确报错。
- `ParsedFile` 与 `attachment` 表结构不变（复用 `extracted_text`）。

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
| 纯文本模型 + 图片（有 OCR 文本） | 文本注入 system prompt，图片不直传 |
| 纯文本模型 + 图片（无 OCR 文本） | 返回 `OCR_CAPABILITY_UNAVAILABLE` |
| 非图片附件（PDF/Word/TXT） | 现有 `extractedText` 注入，不受影响 |

## 前端改动

- `AttachmentResponse.hasContent` 已等于 `image || extractedText != null`：OCR 成功后
  图片自动有内容，前端类型无需变更。
- `fonted-oa/src/config/aiModels.ts`：模型条目增加 `multimodal: boolean` 标记，与
  后端 `AiModelCatalog.isMultimodal` 保持一致（仅用于界面提示）。
- `AttachmentPreview.tsx`：图片附件有 `extractedText` 时展示「已通过 OCR 解析」小
  标签（可选，不改变现有交互）。
- 设置页/对话错误展示复用现有 `OCR_CAPABILITY_UNAVAILABLE` 文案与 traceId 面板。

## 数据库

零 DDL。图片附件沿用 `attachment.extracted_text`，与文档附件共用同一列；不需要新增
引擎或置信度列，避免过度设计。

## 错误与降级策略

- OCR 微服务不可用：上传成功（图片可预览），纯文本模型对话引用该图时明确报错。
- 识别结果为空（空白图/纯装饰图）：`text=""`，对话时按无 OCR 文本处理并提示。
- 超时/429/503：全部按不可用处理，不重试、不缓存失败结果。
- 多模态模型路径永远不受 OCR 可用性影响。

## 验证计划

1. 单元测试（Mock `OcrService`）：
   - 纯文本模型：图片 OCR 文本注入 prompt，不调用 `media()`；
   - 多模态模型：走 `media()`，不注入 OCR 文本；
   - OCR 不可用 + 纯文本模型：返回 `OCR_CAPABILITY_UNAVAILABLE`；
   - 超时/5xx：按不可用降级，上传仍成功。
2. 集成测试：本地启动 PaddleOCR 微服务，上传中文截图 → 对话验证识别内容进入回答。
3. 回归：PDF/Word 附件、知识库上传（仍拒绝图片）、多模态路径（如配置视觉模型）
   不受影响。
4. 验证命令：`cd backend && mvn test`；两个前端 `npm run lint && npm run build`。

## 实施清单

### 后端

- `config/OcrProperties.java`（新增）
- `service/OcrService.java`（新增）
- `service/impl/HttpOcrClient.java`（新增）
- `service/impl/TikaFileParserServiceImpl.java`（修改：图片分支调 OCR）
- `service/model/AiModelCatalog.java`（修改：新增 `isMultimodal`）
- `service/impl/ChatServiceImpl.java`（修改：模型路由分流）
- `common/ErrorCode.java`（修改：新增 `OCR_CAPABILITY_UNAVAILABLE`）
- `application*.yml`、`.env.example`、`.env.docker.example`（修改：`app.ocr.*`）

### Python 微服务（`deploy/ocr-service/`）

- `app.py`：FastAPI + PaddleOCR（PP-OCRv4），懒加载模型，`/ocr/recognize`、`/healthz`
- `requirements.txt`、`Dockerfile`
- `docker-compose.yml`：新增 `ocr-service` 服务（内网暴露，不映射公网）

### 前端（`fonted-oa`）

- `src/config/aiModels.ts`（修改：多模态标记）
- `src/components/ai-chat/AttachmentPreview.tsx`（可选：OCR 标签）
