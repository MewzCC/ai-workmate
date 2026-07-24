# Git And Commit Rules

## 分支规范

推荐格式：

- `feat/<scope>-<short-name>`
- `fix/<scope>-<short-name>`
- `docs/<scope>-<short-name>`
- `refactor/<scope>-<short-name>`
- `chore/<scope>-<short-name>`

示例：

- `feat/chat-sse-history`
- `fix/auth-token-expiration`
- `docs/agent-rules`

## 提交日志

使用 Conventional Commits：

```text
<type>(<scope>): <subject>
```

常用类型：

- `feat`：新增用户可见能力。
- `fix`：修复缺陷。
- `docs`：文档、规范、注释。
- `style`：格式调整，不改变行为。
- `refactor`：重构，不改变外部行为。
- `perf`：性能优化。
- `test`：测试相关。
- `build`：构建、依赖、脚本。
- `ci`：CI/CD。
- `chore`：维护性任务。

示例：

```text
docs(rules): add enterprise agent and engineering rules
feat(chat): persist assistant response after stream completes
fix(auth): reject expired jwt before controller execution
```

## 提交正文

当改动涉及架构、数据结构、接口契约、迁移、风险时，提交正文必须包含：

- 背景：为什么改。
- 方案：怎么改。
- 验证：运行了什么命令。
- 风险：上线或回滚注意点。

## 禁止事项

- 禁止提交真实密钥、`.env`、数据库 dump、用户隐私数据。
- 禁止把格式化全仓代码和业务修改混在一个提交。
- 禁止在未说明的情况下改动无关模块。
- 禁止用“update”“fix bug”“wip”作为最终提交说明。
