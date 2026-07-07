# Frontend Design Skill

## 触发场景

当任务涉及 Next.js/React 页面、聊天 UI、工作台、组件、样式优化、响应式体验、设计系统时使用本 skill。

## 当前项目约束

- 使用 Next.js 14 App Router。
- 使用 Tailwind CSS，不引入大型 UI 框架作为默认方案。
- 使用 lucide-react 作为图标优先选择。
- 现有产品是企业 AI 助手，不做营销站风格。
- 当前 UI 核心是登录页、侧边栏、聊天主界面、消息气泡、代码高亮。

## 开源前端参考

优先借鉴这些开源生态的思想，而不是直接复制代码：

- shadcn/ui：组件组合方式、可访问性、Tailwind token 化。
- Radix UI：无样式可访问组件模式。
- Tailwind UI：间距、密度、表单状态、企业后台布局。
- Vercel AI SDK 示例：流式聊天状态、消息列表和输入体验。
- Chatbot UI / Open WebUI：对话列表、模型选择、知识库入口的产品结构。

引入依赖前必须确认收益。默认先用现有栈实现。

## 设计原则

- 企业级：稳定、克制、可扫描、信息密度适中。
- AI 产品：突出流式反馈、上下文感、可追溯、错误可恢复。
- 工作台：避免大面积装饰，优先布局效率。
- 聊天：代码块、Markdown、复制、滚动、空态、加载态必须舒服。
- 品牌：AI WorkMate 应体现“可靠的工作伙伴”，避免廉价科技感。

## 组件标准

- 按钮：明确 primary、secondary、ghost、danger、icon-only 状态。
- 输入框：支持禁用、错误、加载、快捷键提示。
- 消息气泡：区分 user、assistant、system、error、streaming。
- 侧边栏：支持折叠、活动态、禁用态、后续 conversation 列表。
- 模态与抽屉：后续知识库、工具配置优先使用 drawer/panel。
- Toast：用于轻反馈，不替代错误详情。

## Tailwind 规范

- 可复用颜色写入 `tailwind.config.ts`。
- 高频 class 组合可以抽成小组件，不要过早抽象工具函数。
- 避免到处写任意色值，例如 `bg-[#123456]`。
- 布局尺寸使用清晰约束：`min-w-0`、`max-w-*`、`overflow-*`、`h-screen`。
- 深色模式必须和浅色模式一起设计。

## 可访问性清单

- icon-only 按钮必须有 `aria-label`。
- 表单有 label 和错误提示。
- 焦点态可见。
- 键盘可以完成主要流程。
- 文本对比度满足 WCAG AA。
- 流式区域变化必要时使用 `aria-live="polite"`。

## 交付前检查

- `npm run lint` 通过。
- 移动端、桌面端无明显挤压。
- 长消息、长代码、空消息、请求失败、流式中断都有状态。
- 文案无乱码。
- 组件没有引入不必要的全局副作用。
