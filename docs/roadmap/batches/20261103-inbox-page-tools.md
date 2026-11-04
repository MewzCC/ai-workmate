# 待办与消息页面操作

批次 9B，依赖 `bdb31148` 的浏览器公共桥接。只收口待办与消息页、共享只读查询隔离和本人通知详情接口；申请页、其他页面和无关国际化改动继续分批。

## 页面与接口

| 页面 | 真实读取 | 界面指令与人工操作 |
| --- | --- | --- |
| `todo` | 既有 `GET /api/todos`、`GET /api/todos/{id}` | 状态、日期、数字分页筛选；刷新；先请求受保护详情，再进入既有审批详情路由。无直接审批回调 |
| `messages` | 既有通知列表/未读数；新增 `GET /api/notifications/{id}` | 数字分页、刷新及通知详情；详情不依赖当前分页，也不隐式标记已读。人工单条/全部已读继续使用原接口，增加同步锁和迟到回调保护 |

新增详情接口沿用 `Result<NotificationResponse>` 和 JWT Cookie。领域重新解析实时 `notification:read:self`，并在同一条条件查询中绑定认证租户、用户及通知编号。无权限返回 403，其他用户/租户或不存在资源返回 404，非法编号返回参数错误；查看不改变 read_flag，也不写业务变更审计。没有新增 Agent 工具、任意执行 HTTP 接口或放开写发布门。

待办使用服务端现有上限 50，消息使用现有上限 100；指令数字分页与控件状态一致，日期筛选同步到受控 Ant Design RangePicker。未知字段、非法日期、分页和状态在发请求或改变控件前拒绝。读取失败清除旧数据、展示错误和重试；UI 刷新失败返回拒绝，不能报告应用成功。

两页注册的都是本地界面/读取回调，不提供标记已读、批量操作或审批的 Agent UI 命令。人工已读操作仍经原业务 API 校验，按钮和同步锁只防重复操作，不能替代后端授权。

## 共享查询边界

`useScopedOverview` 将认证身份、权限版本、实际权限/角色/数据范围及查询函数变化绑定本地递增版本。页面或身份 A→B→A 不复活旧快照和旧回调；最新请求覆盖前次请求，旧分页结果、卸载后结果和旧错误提示不回写。当前数据只与当前查询绑定，不能把上页结果暂时展示为新页数据。

React 根级 StrictMode 的 effect 重放必须实际覆盖：初次只把 StrictMode 放在内部 wrapper 的测试不足以触发根级重放；启用 `reactStrictMode: true` 后复现加载守卫失效、数据持续为空。修复改为按调用检查所属作用域，异步请求另保留请求编号和认证数据范围校验，重放的第二次读取可正常完成，旧请求仍不能回写。

## 验证

独立暂存快照：`backend/target/chat-server-review-ojt1r2su/`。前端定向 15 项、后端定向 15 项均通过；后者包括 4 项真实 Cookie/PostgreSQL 新详情用例和原通知服务用例。前端覆盖筛选、详情预检、权限撤回、分页迟到结果、替换作用域、重试、重复已读点击、其他分页通知和 StrictMode。

首次前端定向只有重试按钮查询失败，原因是 Ant Design 自动在中文短按钮中加入空格；已使用可接受该呈现的按钮名称匹配。StrictMode 根级用例另先失败再修复通过，不将内部 wrapper 的最初通过误认为已覆盖根级生命周期。

最终完整执行 `npm run lint`、`npm run test`、`npm run build` 和 `mvn test`：前端 **694 项全部通过、零跳过**，lint 零错误、保留其他旧页面 3 项 Hook 警告，构建通过（22.06 秒，保留大分块提示）；后端 **1983 项，零失败/错误，2 跳过，BUILD SUCCESS**。两项旧环境跳过仍为缺 Docker 的初始化测试和缺专用 `OBSERVABILITY_TEST_DB_URL` 的运行日志测试，新增通知详情用例均实际执行。

类型检查亦通过。11 个暂存源码/测试文件与最终验证快照逐字节一致。最终日志为快照根目录的 `inbox-page-verified-tests.json`、`inbox-page-verified-lint.log`、`inbox-page-verified-build.log` 及 `backend/target/inbox-page-backend-full.log`；定向为 `inbox-page-verified-target.json` 和 `backend/target/inbox-page-backend-target.log`。根级 StrictMode 失败证据为 `inbox-page-target-final.json`，修复后的该用例在定向及最终全量均通过。整理 Hook 依赖以消除新增 lint 警告后，已再次运行最终前端完整检查，没有关闭 lint 规则。

没有数据库结构或迁移变化，测试框架只操作拥有的隔离 PostgreSQL 与随机 Redis。真实模型、浏览器及全部业务域的回归继续保留；现有待办 API 的业务范围沿用服务端实现，本批不承诺已统一其他审批业务的待办。

## 回滚

可按依赖回滚前端页面及新增详情 API，无结构回滚。保留后端既有鉴权、已读领域事务与 Tool Gateway；撤回界面或取消本地请求不会撤销已完成的人工已读操作。后续依赖共享 Hook 的页面需同步回滚或保留相同的身份及查询隔离保护。
