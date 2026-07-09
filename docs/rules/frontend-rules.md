# Frontend Rules

## 技术栈

- Next.js 14 App Router。
- React 18 函数组件。
- TypeScript 严格建模。
- Tailwind CSS 用于现有营销页与基础样式。
- Zustand 用于轻量状态管理。
- Ant Design 用于 OA 工作台基础业务组件。
- ECharts 用于 OA 图表。
- lucide-react 可继续用于营销页和聊天页图标；OA 菜单与业务按钮优先使用 `@ant-design/icons`。

## Skill 触发

- 修改营销官网、聊天 UI、普通 React 组件或页面设计时，读取 `docs/skills/frontend-design-skill.md`。
- 修改动效、滚动进入、hover、过渡、reduced-motion 时，读取 `docs/skills/motion-skill.md`。
- 修改 `/oa`、`frontend/src/components/oa`、`frontend/src/mock/oa*`、`frontend/src/lib/oaApi.ts`、`frontend/src/types/oa.ts` 时，必须读取 `docs/skills/oa-workbench-skill.md`。

## 组件规范

- 页面组件负责路由入口和布局编排。
- 业务组件负责交互和状态组合。
- 基础组件负责纯 UI。
- 组件 props 必须声明 interface/type，避免隐式 `any`。
- 客户端组件仅在需要 state、effect、浏览器 API 或事件时使用 `'use client'`。
- 复杂组件超过 250 行、状态超过 3 类、渲染分支超过 4 个时，优先拆分。
- 图标按钮必须有 `aria-label` 或 `title`。

## OA 工作台强约束

OA 工作台业务 UI 必须使用真实 Ant Design 组件：

- 业务按钮使用 `Button`。
- 业务表格使用 `Table` 或后续 ProTable。
- 抽屉使用 `Drawer`。
- 弹窗使用 `Modal`。
- 标签使用 `Tag`。
- 表单使用 `Form`。
- 输入使用 `Input` 或 `Input.TextArea`。
- 选择器使用 `Select`。
- 开关使用 `Switch`。
- 上传使用 `Upload`。
- 滑块使用 `Slider`。
- AI 入口使用 `FloatButton`。
- 步骤和执行计划使用 `Steps`。
- 审计记录使用 `Timeline`。
- 当前上下文使用 `Descriptions`。

禁止用原生 HTML 或自定义 `div` 模拟 Ant Design 的按钮、表格、抽屉、弹窗、选择器。

## OA 布局规则

- `/oa` 入口只负责渲染 `AdminLayout`。
- `AdminLayout` 负责 OA 总布局和全局状态。
- 左侧 Sider 必须固定在视口左侧。
- 页面滚动时左侧 Sider 不得跟随移动。
- 左侧 Sider 默认隐藏滚动条，鼠标移入后显示。
- 右侧主布局必须根据 Sider 展开/收起状态保留左边距。
- 顶部 Header 可 sticky，但不能遮挡内容。
- 移动端必须避免 Sider 压住主内容。

## 状态与请求

- API 请求集中在 `frontend/src/lib/api.ts` 或按领域拆分到 `frontend/src/lib/<domain>.ts`。
- OA API 请求集中在 `frontend/src/lib/oaApi.ts`。
- `localStorage` 访问必须在浏览器环境中执行。
- 请求失败不能导致页面白屏；应提供 message 提示或 fallback mock。
- SSE 必须处理连接失败、流中断、空响应、重复发送和加载态恢复。

## UI 设计

- 企业工作台优先：信息清晰、密度适中、操作稳定。
- OA 页面避免营销式 hero、过度装饰、无意义大面积渐变背景。
- 聊天界面关注阅读体验：行高、代码块、复制、流式光标、错误态、空态。
- 移动端必须保证侧边栏、输入区、长代码块不挤压主内容。
- 动效遵循 `prefers-reduced-motion`。

## 可访问性

- 表单控件必须有 label；placeholder 不能替代 label。
- 可点击元素使用 `button` 或 `a`；OA 业务按钮使用 Ant Design `Button`。
- 键盘可以完成主要流程。
- 焦点态可见。
- 文本对比度满足 WCAG AA。
- 流式区域变化必要时使用 `aria-live="polite"`。

## 性能

- 避免在渲染过程中构造大对象或执行复杂计算。
- ECharts 必须处理 resize 和 dispose。
- 长消息列表后续应考虑虚拟滚动。
- 图片使用 Next.js Image 或显式尺寸，避免布局跳动。

## 交付前检查

- `npm run lint` 通过或说明已有历史警告。
- `npm run build` 通过。
- `/oa` 能打开，不白屏。
- 角色切换、菜单过滤、AI Drawer、ECharts 渲染可用。
- 文案无新增乱码。
- 组件没有引入不必要的全局副作用。

