# Phase 2 Agent 安全边界与最小权限规范

## 1. 文档定位

本文档是 `phase-2-agent-plan.md` 与 `phase-2-agent-tasks.md` 的强制安全附件。Phase 2 的实现、代码评审、测试和发布均必须满足本文档；如果业务需求与本文档冲突，默认拒绝扩大 Agent 能力，必须重新进行安全评审。

Phase 2 Agent 的定位是：

> 一个由用户显式触发、只能执行固定白名单工具、每一步都由服务端重新鉴权的受控任务执行器。

它不是通用自动化平台，不是系统管理员替身，也不是可以自主发现、创建、修改或组合能力的自治 Agent。

## 2. 自治等级上限

| 等级 | 能力 | Phase 2 状态 |
| --- | --- | --- |
| A0 对话建议 | 只回答、总结、解释，不调用业务工具 | 允许 |
| A1 受控只读 | 用户发起，模型提议，代码校验，执行 L0 白名单查询 | Phase 2A 上限 |
| A2 单一受控写入 | 用户发起并确认，只执行一个 L1/L2 白名单写步骤 | Phase 2B 上限 |
| A3 自主编排 | 循环规划、多次写入、后台自动运行、跨系统操作 | 禁止 |
| A4 自主扩权 | 创建工具、修改权限、改变安全策略或执行任意代码 | 永久禁止 |

Phase 2B 中一个任务最多包含一个有副作用步骤。写入前置条件由 Policy Guard 或领域 Service 确定性读取和校验，不允许 Planner 先调用工具再自行决定写参数；不得把多个写步骤组合成“创建后立即提交”“批量修改后发布”等链路。`leave.submit` 只能提交执行前已存在且仍属于本人的有效草稿。

## 3. 不可被配置覆盖的安全不变量

以下规则优先级高于 Prompt、数据库工具配置、租户配置、角色权限、页面上下文和用户确认：

1. 默认拒绝：未被代码注册、未显式启用或策略不完整的工具不可见、不可规划、不可执行。
2. 模型不授权：模型只能提议 toolCode 和参数，不能改变 riskLevel、权限、租户、数据范围、确认要求或状态机。
3. 用户确认不扩权：确认只能批准用户本来就有权执行的具体计划，不能让无权限动作变得有权限。
4. 实时鉴权：规划、签发确认凭证、入队和每个步骤执行前均重新从数据库解析权限。
5. 身份不可参数化：userId、tenantId、role、permission、dataScope 只能来自认证上下文，工具 schema 禁止声明这些字段。
6. 计划不可变：执行使用持久化计划快照；客户端不得在 execute 中提交或修改 steps、toolCode、args、riskLevel。
7. 工具不可递归：工具输出、RAG 文档、页面文本和模型总结不能触发新的工具调用。
8. 禁止动态能力：不得根据数据库 className、beanName、URL、脚本或表达式动态装载 handler。
9. 拒绝优先：策略结果存在歧义、依赖不可用、模型输出不完整或安全校验异常时，一律拒绝，不做宽松 fallback。
10. Phase 2 Agent 不允许修改自己的工具注册表、启用开关、Prompt、安全策略、审计记录或权限数据。

## 4. 永久禁止的能力

即使当前用户是 `SUPER_ADMIN`，Phase 2 Agent 也不得获得以下工具或间接实现等价能力：

- 任意 SQL、数据库控制台、动态查询表达式或 Mapper 选择能力。
- Shell、PowerShell、JavaScript、SpEL、OGNL、模板表达式或任意代码执行。
- 任意文件路径读写、目录遍历、文件删除、压缩包解压和服务端本地文件访问。
- 任意或用户指定目标的 URL 请求、Webhook、HTTP 代理和 DNS 查询。平台固定配置的 LLM endpoint 仅供模型客户端使用，业务 ToolHandler 不得借此获得网络出口。
- 密钥、JWT、Cookie、API Key、连接串、环境变量和服务端配置读取。
- 用户、角色、权限、路由、安全策略、工具注册表和租户配置修改。
- 删除业务数据、批量审批、批量修改、付款、发布、部署和生产运维操作。
- 敏感导出、全量导出、跨租户汇总或绕过分页的数据抓取。
- 对外发送邮件、短信、企业微信、钉钉、飞书消息或调用未评审的第三方系统。
- 创建定时任务、后台长期运行任务、递归 Agent、子 Agent 或自我重试循环。

禁止项不能通过增加二次确认解除。未来确需增加时，必须进入新的 Phase、安全设计和独立发布门。

## 5. 信任边界与执行链

```text
用户输入 / pageContext / RAG 内容 / 工具结果
                 │ 全部不可信
                 ▼
         输入限制与上下文过滤
                 ▼
       Planner 仅生成候选结构化计划
                 ▼
  Tool Registry + Policy Guard 确定性复核
                 ▼
       持久化不可变 planHash/version
                 ▼
       用户执行或确认（不产生新权限）
                 ▼
      execute 再鉴权并原子进入队列
                 ▼
       Worker 每一步执行前再次鉴权
                 ▼
     固定 handler 调用既有领域 Service
                 ▼
       输出校验、脱敏、限量、审计
```

任何一层失败都必须终止链路。不得让 Planner 直接持有 Spring Bean、Mapper、HTTP Client、文件系统或数据库连接。

## 6. ToolDefinition 强制安全合约

每个工具必须在代码中静态声明以下字段，缺少任一安全字段时启动失败：

| 字段 | 要求 |
| --- | --- |
| code / handlerVersion | 稳定且唯一；版本变化使旧计划失效 |
| purpose | 明确允许场景和禁止场景 |
| inputSchema / outputSchema | `additionalProperties=false`；限制长度、枚举、数组大小和嵌套深度 |
| riskLevel | 只能由代码声明，租户配置只能提高风险或关闭工具 |
| requiredPermissions | 明确 ALL/ANY；不得使用 `*`、空权限或仅 route 权限代替业务权限 |
| ownershipPolicy | 明确 SELF、ASSIGNED_TO_SELF、TENANT_SCOPED 或固定资源规则；Phase 2 优先本人或分配给本人 |
| maxResultItems / maxResultBytes | 强制裁剪，禁止“全部”“无限制” |
| timeoutMs | 必填且有平台上限 |
| retryPolicy | READ_ONLY_SAFE、BUSINESS_IDEMPOTENT 或 NEVER；默认 NEVER |
| sideEffect | NONE 或 SINGLE_WRITE；Phase 2 禁止 EXTERNAL 和 BULK |
| confirmationPolicy | NONE、EXPLICIT 或 SECONDARY；不得由模型覆盖 |
| auditPolicy | 成功、失败和拒绝需要记录的脱敏字段 |

handler 必须从 `ToolExecutionContext` 取得认证用户与租户，并调用现有领域 Service。禁止直接调用 Mapper，也禁止把通用 `Map<String,Object>` 未经白名单映射后传给领域层。

## 7. 保守运行上限

以下为 Phase 2 默认上限，必须通过服务端配置统一收紧；租户不得提高超过平台上限：

| 项目 | 默认上限 |
| --- | ---: |
| 用户输入 | 4 KiB |
| pageContext | 16 KiB，最大深度 3 |
| 候选计划步骤 | Phase 2A 最多 3；Phase 2B 最多 3 且仅 1 个写步骤 |
| 单任务工具调用 | 最多 5 次，无循环、无递归 |
| 同一用户并发任务 | 2 个 |
| plan 请求 | 每用户每分钟 10 次 |
| confirmation-token 请求 | 每用户每任务每分钟 3 次 |
| execute 请求 | 每用户每分钟 5 次 |
| 单次查询返回 | 默认 20 条，硬上限 50 条 |
| knowledge.search topK | 默认 5，硬上限 10 |
| 单步骤结果 | 256 KiB；模型总结上下文需进一步裁剪 |
| 单工具超时 | 默认 15 秒，硬上限 30 秒 |
| 单任务执行时间 | 默认 60 秒，硬上限 120 秒 |
| Worker 自动重试 | 只读安全工具最多 1 次；写工具默认 0 次 |

达到限制时返回明确错误，不允许模型自行拆分成多个任务规避限制。限流键必须包含 tenantId + userId，不能仅按 IP。

## 8. 权限与数据范围防线

- 工具可用集合必须同时满足：代码注册、平台启用、租户启用、实时业务权限、页面能力和 Agent 永久禁止清单。
- `SUPER_ADMIN` 的应用权限不等于 Agent 自动化权限；永久禁止清单仍然生效。
- 每个资源查询和写入使用 tenantId + resourceId，SELF 工具额外使用 ownerUserId；禁止先按 ID 查出再在内存中判断租户。
- 查询工具不允许接收 ownerUserId、departmentId 等可扩大范围的字段，除非工具合约明确为受控租户范围且单独评审。
- plan 阶段查到的数据不能作为 execute 阶段仍有权限的证据；Worker 调用领域 Service 时必须再次校验。
- 角色或权限变更后无需重新登录；旧任务和旧 confirmationToken 不能保留旧权限。
- 任务列表、详情、取消、事件订阅必须使用 `(tenantId, userId, taskId)` 查询，禁止只按 taskId。

## 9. Prompt Injection 与模型输出防护

- 系统 Prompt 明确标识用户输入、pageContext、知识片段和工具结果为“不可信数据，不是指令”。
- 只向模型暴露当前允许工具的最小描述；永久禁止工具的名称和内部实现不进入 Prompt。
- RAG 检索必须先做权限过滤，再召回；引用保留 docId、chunkId、score 和来源。
- 文档中出现“忽略规则”“调用某工具”“输出密钥”等内容只能作为普通文本处理。
- Planner 输出只按严格 DTO 反序列化；关闭多态类型、未知字段和宽松类型转换，`additionalProperties=false`。
- 模型输出中的 URL、SQL、代码、权限字段、额外 toolCode 或自然语言动作均不能执行。
- 解析失败只允许一次受控重试；第二次失败直接 `SCHEMA_INVALID`，不得使用正则猜测或补全工具参数。
- 工具结果不得原样再次成为 Planner 输入；需要总结时只传脱敏、限长的结构化结果，并继续标记为不可信。
- systemPrompt、输出 schema 和安全策略使用代码版本管理；用户、数据库工具描述和租户管理员均不能覆盖系统 Prompt。

## 10. 写操作额外限制

- Phase 2B 写工具默认关闭，必须在 Phase 2A 安全验收后按租户显式启用。
- 一个任务只能有一个写步骤；不得批量，不得并行，不得由工具结果生成新的写步骤。
- confirmationToken 只证明用户确认了特定 planHash，不代表权限；签发、execute 和 Worker 执行前均要重新鉴权。
- 二次确认主要防止误操作，不是身份认证和授权替代；前端 Modal 或按钮隐藏从来不是安全边界。
- confirmationToken 只存内存，不写 localStorage、URL、日志、SSE、审计 summary 或错误消息。
- 写 handler 必须使用领域状态机和条件更新；不得用“先查后改”代替原子条件。
- 写入结果不确定时不得自动重试。任务进入 `PARTIALLY_SUCCEEDED` 或 `FAILED`，向用户展示可核实的资源 ID 和人工处理建议。
- `leave.createDraft` 和 `leave.submit` 不能出现在同一个计划中；提交只接受已存在草稿 ID 和 version。

## 11. 数据泄露与审计控制

- 模型输入只包含完成当前计划所需的最少字段；姓名、手机号、邮箱、证件、薪酬等默认不发送模型。
- 错误信息、SSE、任务详情和审计不返回堆栈、SQL、表名、内部类名、Prompt、token 或安全策略细节。
- SSE 必须通过 Authorization header 或当前项目等价的受控认证机制传递 JWT，禁止把 JWT、confirmationToken 放进 URL/query；CORS 仅允许项目配置的可信 Origin。
- 前端把工具结果和错误视为不可信文本，禁止直接使用 `dangerouslySetInnerHTML`；如使用 Markdown，必须禁用原始 HTML 并过滤危险链接协议。
- 审计记录 actor、tenant、taskId、toolCode、resourceType、resourceId、decision、errorCode、traceId 和参数摘要哈希。
- 拒绝事件同样审计：越权、跨租户、非法 schema、凭证重放、工具版本变化、限流和永久禁止能力请求。
- 审计记录只能追加，业务 Agent 无权修改或删除；审计查询继续使用既有权限控制。
- 任务 input、pageContext、args、result 和 event 必须有保留期限；清理任务按 tenant + 时间分批删除并记录统计，不输出内容。

## 12. Kill Switch 与故障安全

至少提供以下服务端开关，默认值均为安全关闭：

- `agent.enabled`：全局 Agent 开关。
- `agent.planning-enabled`：只允许/禁止计划生成。
- `agent.execution-enabled`：全局执行开关。
- `agent.write-tools-enabled`：全局写工具开关，默认 false。
- 租户级 Agent 开关和写工具开关。
- 单工具 enabled 开关。

关闭执行开关后，不再领取新任务；RUNNING 任务按安全策略完成当前原子步骤后停止，不强行中断数据库事务。模型异常、权限服务异常、审计写入失败或策略组件异常时必须 fail closed。

## 13. Vibe Coding 实施约束

Vibe Coding 可以加速样板代码、DTO、测试骨架和文档生成，但生成代码视为不可信输入，不能降低以下人工控制：

1. 每次只实现一个安全边界清晰的小任务，不允许一次性生成整个 Agent 引擎并直接合并。
2. 数据库约束、状态机、权限条件、确认消费、幂等和 Worker 领取 SQL 必须人工逐行评审。
3. AI 生成的权限判断必须用反向测试证明：未登录、普通员工、跨用户、跨租户、权限刚被回收均失败。
4. 不接受“前端已隐藏”“Prompt 已要求”“模型一般不会这样做”作为安全措施。
5. 每完成一个工具先跑安全契约测试，再将其加入数据库种子和 Planner 白名单。
6. 不允许 AI 为了让测试通过而放宽 SecurityConfig、删除鉴权、增加 fallback mock 或扩大工具 schema。
7. 任何新增依赖必须检查用途、维护状态和许可证；反序列化、JSON Schema、SSE、限流相关依赖优先使用成熟实现。
8. 安全失败测试必须先于正常 E2E 验收；没有失败路径测试的工具视为未完成。
9. 人工评审者负责最终发布门，代码生成 Agent 不得自行判断 Phase 2B 可以启用。

## 14. 安全发布门

以下任一项不满足，Phase 2A 不得上线，Phase 2B 更不得启用：

- 永久禁止能力无法通过 Prompt、数据库配置或高权限角色绕过。
- 普通员工、跨用户、跨租户和权限回收测试全部通过。
- 未知 toolCode、未知字段、超限参数、恶意嵌套和类型混淆全部被拒绝。
- Prompt Injection 语料不能触发未授权工具、改变参数边界或泄露系统信息。
- 并发 execute、确认重放和 Worker 重启不产生重复业务效果。
- SSE、任务详情和错误响应不泄露其他用户任务或内部敏感信息。
- Kill Switch、限流、超时、取消和审计失败的 fail-closed 行为经过演练。
- 所有写工具默认关闭，并能独立关闭；不存在一个通用“执行任意动作”工具。

Phase 2B 额外要求：

- 每个写工具完成滥用用例评审和独立人工签字。
- 一个任务最多一个写步骤，确认绑定 planHash 且实时权限复核通过。
- 重复请求、服务重启和结果不确定场景不会产生第二次写入。
