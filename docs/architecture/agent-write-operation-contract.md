# Agent 原子写操作契约

## 目标

Agent 写工具继续通过 `ToolGateway` 执行，Handler 调用类型化 Port，Adapter 再调用领域 Service。该契约只统一可跨进程传输的操作标识、业务收据和结果核验语义，不替代领域权限、状态机、事务、乐观锁或审计。

## 共享类型

- `ToolOperationKey`：由可信 taskId、stepId、toolCode 和版本生成，长度受限；模型与客户端不能传入。它只用于领域幂等及结果核验，不是授权或重试许可。
- `ToolWriteReceipt`：写结果的无字段标记。各领域仍返回自己的封闭 record，避免万能响应和字段泄露；标记本身不会改变工具 JSON 输出。
- `ToolWriteVerification<R>`：只读核验返回 `OBSERVED + receipt` 或 `UNOBSERVED`。两种状态互斥，禁止用空 Optional 模糊表示“可以重试”。

当前会议创建与取消已接入显式核验结果；底层查询继续重新解析实时用户、权限、租户和本人归属，并按稳定操作键匹配原命令。`UNOBSERVED` 只表示查询时没有观察到已提交结果，不证明在途写入失败，也不允许 Worker 自动重放。

## Spring Cloud 演进边界

未来拆分领域服务时，只替换 `agent.tool.adapter`：

1. 目标服务由服务端配置或服务发现固定选择，不允许模型提供 URL。
2. `ToolActorContext`、类型化命令和 `ToolOperationKey` 作为内部受信调用协议传输；远端仍需重新鉴权，不能信任转发的权限列表。
3. 写请求超时后使用同一操作键调用只读核验接口；`OBSERVED` 返回原领域收据，`UNOBSERVED` 进入人工核实或失败关闭，不自动重试。
4. 单体内继续使用本地领域事务。真正出现跨服务协作后再按业务设计 Outbox、事件去重和补偿，不引入共享数据库事务或通用分布式事务模板。

本契约不增加公共 Tool API、远程调用依赖、写工具开关或执行权限，也不改变冻结的输入输出 Schema 与版本 Hash。
