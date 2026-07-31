# Embedding 与 pgvector 接入

## 目标

AI WorkMate 的知识库使用 PostgreSQL `pgvector` 保存向量。开发环境可调用本机
Qwen3 Embedding 服务，服务器环境可切换到 OpenAI 兼容的 Embeddings API。两种模式
共用知识入库、检索和聊天 RAG 链路，不存在本地 mock 或失败后伪造成功。

## Provider 切换

只允许通过服务端环境变量切换，API Key 不进入前端或数据库。

本地模式：

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

远程 API 模式：

```dotenv
EMBEDDING_ENABLED=true
EMBEDDING_PROVIDER=api
EMBEDDING_API_BASE_URL=https://api.openai.com/v1
EMBEDDING_API_KEY=replace-with-server-secret
EMBEDDING_API_MODEL=text-embedding-3-small
EMBEDDING_API_SEND_DIMENSIONS=true
EMBEDDING_DIMENSION=1024
```

远程服务需要兼容 `POST /embeddings`。如果供应商不接受 `dimensions` 参数，将
`EMBEDDING_API_SEND_DIMENSIONS` 设为 `false`，同时所选模型仍必须固定返回 1024 维。

## 向量空间隔离与切换

`knowledge_doc` 和 `knowledge_chunk` 都保存 `embedding_provider` 与
`embedding_model`。检索只查询当前 Provider 和模型生成的向量，避免不同模型的向量
直接比较。

切换 Provider 或模型后，旧文档不会被当前模型检索，需要逐个调用重建接口：

```http
POST /api/knowledge/documents/{documentId}/reindex
Authorization: Bearer <JWT>
```

重建在事务内更新该文档全部分块。任一分块失败时整次重建回滚。

## API

- `GET /api/knowledge/embedding-status`：查看当前 Provider、模型和维度。
- `POST /api/knowledge/documents`：提交纯文本知识文档并同步分块、向量化。
- `GET /api/knowledge/documents?page=1&size=20`：查询当前用户的文档。
- `DELETE /api/knowledge/documents/{id}`：删除本人文档及其分块。
- `POST /api/knowledge/documents/{id}/reindex`：使用当前 Provider 重建向量。
- `POST /api/knowledge/search`：执行当前租户、当前用户范围内的相似度检索。

所有接口均受 JWT 保护。服务端会再次从数据库解析有效用户与租户，查询同时带
`tenant_id` 和 `user_id`，不会信任客户端提交的租户或资源归属。

聊天服务会在生成回答前执行同范围检索，将命中的内容以 `[知识来源N]` 注入系统提示。
Embedding 服务不可用、响应数量不匹配、维度错误或包含非法数值时，接口明确返回稳定
错误，不会静默降级成虚假 RAG。

## 数据库

唯一升级入口仍为 `backend/src/main/resources/db/init.sql`。它会：

- 幂等启用 `vector` 扩展；
- 创建 `knowledge_chunk.embedding vector(1024)`；
- 创建余弦距离 HNSW 索引；
- 建立租户、用户、Provider、模型组合索引；
- 使用文档内容哈希和当前向量空间防止重复入库。

部署 PostgreSQL 时必须使用包含 pgvector 的镜像，例如项目
`docker-compose.yml` 中的 `pgvector/pgvector:pg16`。
