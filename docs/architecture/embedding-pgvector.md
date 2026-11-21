# Embedding 与 pgvector 接入

## 目标

AI WorkMate 的知识库使用 PostgreSQL `pgvector` 保存向量。新建知识库时可从 OA 的 AI 渠道选择已启用的 Embedding 和 Rerank 模型。选中的 Embedding 用于文档入库、索引重建和指定知识库的检索，选中的 Rerank 用于对召回的候选片段重新排序。

## 渠道模型和兼容默认值

渠道模型的地址和凭据由超级管理员在「系统设置 → AI 配置 → 模型与偏好 → AI 渠道」维护。知识库只保存渠道模型引用 `channelId::modelId`。Embedding 需要支持 1024 维输出，可在模型选项里勾选显式发送 `dimensions=1024`。Rerank 渠道需要支持兼容的 `POST /rerank` 接口。 [Embeddings API](https://developers.openai.com/api/reference/resources/embeddings/methods/create) · [Rerank API](https://docs.siliconflow.com/en/api-reference/rerank/create-rerank)

已有知识库选择服务端默认时，继续兼容现有本地或旧远程索引。默认本地模式依赖以下服务：

```dotenv
EMBEDDING_ENABLED=true
EMBEDDING_PROVIDER=local
EMBEDDING_LOCAL_BASE_URL=http://127.0.0.1:18080
EMBEDDING_LOCAL_MODEL=Qwen3-Embedding-0.6B
EMBEDDING_DIMENSION=1024
```

本地服务必须提供：

```http
POST /embed
Content-Type: application/json

{"texts":["第一段文本","第二段文本"]}
```

响应中的 `embeddings` 数量必须与请求一致，`dim` 必须为 `1024`。

## 向量空间隔离与切换

`knowledge_doc` 和 `knowledge_chunk` 都保存 `embedding_provider` 与
`embedding_model`。检索只查询当前 Provider 和模型生成的向量，避免不同模型的向量
直接比较。

知识库已有文档时锁定 Embedding 选择。需要换模型时，先新建知识库并选好新模型，再重新导入原文档。空知识库可以直接切换。相同渠道与模型 ID 应持续指向同一向量空间。

单文档重建使用所属知识库当前的模型：

```http
POST /api/knowledge/documents/{documentId}/reindex
Authorization: Bearer <JWT>
```

重建在事务内更新该文档全部分块。任一分块失败时整次重建回滚。

## API

知识库（集合）维度：

- `GET /api/knowledge/bases`：当前用户的知识库列表（含文档数、分块数）。
- `POST /api/knowledge/bases`：新建知识库，可选传入 `embeddingModelRef`、`rerankModelRef`。
- `GET /api/knowledge/bases/{kbId}`：知识库详情（基本信息 + 文档/分块统计 + 模型与检索参数）。
- `PUT /api/knowledge/bases/{kbId}`：更新知识库基本信息与检索/分块参数（分块重叠必须小于分块大小）。
- `DELETE /api/knowledge/bases/{kbId}`：删除知识库，级联删除其文档与分块。

文档维度（均在知识库内）：

- `GET /api/knowledge/embedding-status`：查看当前 Provider、模型和维度。
- `POST /api/knowledge/documents`：向知识库提交纯文本知识文档并同步分块、向量化（请求体含 `kbId`）。
- `POST /api/knowledge/documents/upload?kbId=1`：multipart 上传 TXT、PDF、Word（.doc/.docx）等文档，服务端用 Tika 解析文本后按该知识库分块参数同步向量化。
- `GET /api/knowledge/documents?kbId=1&page=1&size=20`：查询指定知识库的文档。
- `GET /api/knowledge/documents/{id}`：文档详情（基本信息 + 分块列表，含序号、内容、字符数、向量 ID）。
- `DELETE /api/knowledge/documents/{id}`：删除本人文档及其分块。
- `DELETE /api/knowledge/documents/{id}/chunks/{chunkId}`：删除单个分块，后续分块序号自动前移并同步文档分块数。
- `POST /api/knowledge/documents/batch-delete`：批量删除本人文档（请求体 `{"ids":[1,2,3]}`，最多 100 个）。
- `POST /api/knowledge/documents/{id}/reindex`：使用当前 Provider 重建向量。
- `POST /api/knowledge/documents/batch-reindex`：批量使用当前 Provider 重建向量（请求体同 batch-delete，事务内全部成功或全部回滚）。

检索维度：

- `POST /api/knowledge/search`：全库稠密检索（聊天 RAG 使用）。
- `POST /api/knowledge/bases/{kbId}/search`：知识库内混合检索，按知识库设置的稠密/稀疏数量分别召回（向量语义 + PG 全文 `ts_rank`），融合排序后返回；请求体 `{query, topK?, minScore?}`，`topK` 限制返回条数（默认稠密+稀疏之和，最多 20），`minScore` 为稠密召回最低相关度。命中项带 `matchType`（DENSE / SPARSE / HYBRID）。

所有接口均受 JWT 保护。服务端会再次从数据库解析有效用户与租户，查询同时带
`tenant_id` 和 `user_id`，不会信任客户端提交的租户或资源归属。

聊天服务会在生成回答前执行同范围检索，将命中的内容以 `[知识来源N]` 注入系统提示。
`POST /api/chat/stream` 与 `POST /api/chat` 请求体支持可选 `kbId`：指定后只在该知识库内混合检索，
缺省时检索当前用户全部知识库。知识库归属仍按 `tenant_id + user_id + kb_id` 由服务端校验。
Embedding 服务不可用、响应数量不匹配、维度错误或包含非法数值时，接口明确返回稳定
错误，不会静默降级成虚假 RAG。

## 数据库

唯一升级入口仍为 `backend/src/main/resources/db/init.sql`。它会：

- 幂等启用 `vector` 扩展；
- 创建 `knowledge_chunk.embedding vector(1024)`；
- 创建余弦距离 HNSW 索引；
- 建立租户、用户、Provider、模型组合索引；
- 使用文档内容哈希和当前向量空间防止重复入库（按知识库隔离：`knowledge_doc.kb_id`）；
- 创建 `knowledge_base` 知识库集合表，并自动为已有历史文档创建「默认知识库」回收归属；
- 为 `knowledge_chunk.content` 生成 `tsvector` 列并建 GIN 索引，支撑稀疏（关键词）召回。

部署 PostgreSQL 时必须使用包含 pgvector 的镜像，例如项目
`docker-compose.yml` 中的 `pgvector/pgvector:pg16`。
