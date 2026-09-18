# Agent 类型化写工具执行模板

## 目标

`TypedWriteToolHandler<C, R>` 统一固定单写工具的执行骨架：代码与版本标识、参数解析、调用、输出序列化和稳定操作键生成。`TypedVersionedWriteToolHandler<R>` 进一步复用“单资源 ID + 乐观锁版本”的常见命令形状。

模板不承载授权结论，也不把业务命令退化为通用参数字典。每个具体 Handler 仍把封闭 JSON 参数转换为类型化 Port 命令；领域 Service 仍须重新校验实时用户、租户、业务权限、资源归属、状态机和版本。

## 服务化边界

Handler 只依赖传输中立的业务 Port。当前 Port 由本地 Adapter 实现；未来拆分 Spring Cloud 时可以替换为固定目标的远程 Adapter，而不改变 Planner、ToolGateway、工具契约和具体 Handler。稳定操作键继续由服务端可信任务与步骤上下文生成，不允许模型或客户端提供。

## 门禁

- 具体 Handler 只能继承类型化读或写模板，不再直接实现 `ToolHandler`。
- 通用模板不依赖 Mapper、Controller DTO、领域实体、HTTP Client 或 Spring Cloud 类型。
- 写模板只收口机械执行顺序；ToolGateway 和领域层的双重鉴权不可下沉或省略。
- 写工具仍受单写步骤、确认、写开关、结果上限、审计和禁止自动重试策略约束。
