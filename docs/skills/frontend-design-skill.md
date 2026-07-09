# Frontend Design Skill

## 触发场景

当任务涉及 Next.js/React 页面、营销官网、聊天 UI、普通工作台组件、样式优化、响应式体验、设计系统时使用本 skill。

如果任务涉及 `/oa`、`frontend/src/components/oa`、OA 菜单权限、Ant Design 中后台组件或 ECharts，请同时读取 `docs/skills/oa-workbench-skill.md`，并以 OA skill 的组件约束为准。

## 当前项目约束

- 使用 Next.js 14 App Router。
- 使用 React 18 函数组件。
- 使用 TypeScript。
- 现有营销官网以自定义 CSS + Tailwind 基础能力为主。
- OA 工作台以 Ant Design 为基础业务 UI 标准。
- 聊天界面保留现有 `ChatInterface`，不要因 OA 改动删除。

## 设计原则

- 营销官网可以有品牌感、动效和转化路径，但不能破坏 `/oa` 入口。
- 企业工作台优先稳定、清晰、可扫描、信息密度适中。
- AI 产品要突出上下文、权限、可追踪、失败可恢复。
- 聊天界面关注阅读体验、流式反馈、代码块、复制、错误态和空态。
- 移动端必须可用，不得出现主要按钮或内容被遮挡。

## 组件标准

- 普通页面可以复用现有组件和 Tailwind/CSS。
- OA 页面业务控件必须使用 Ant Design，详见 `oa-workbench-skill.md`。
- 图标按钮必须提供 `aria-label` 或 `title`。
- 表单必须有 label 和错误反馈。
- Toast 只用于轻反馈，不替代错误详情。

## 交付前检查

- `npm run lint` 通过或说明历史警告。
- `npm run build` 通过。
- 页面桌面端和移动端无明显挤压。
- 文案无新增乱码。
- 无不必要的全局副作用。

