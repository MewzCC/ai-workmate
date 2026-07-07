# Frontend Rules

## 技术栈

- Next.js 14 App Router。
- React 18 函数组件。
- TypeScript 严格建模。
- Tailwind CSS 作为主样式系统。
- Zustand 作为轻量状态管理。
- lucide-react 作为优先图标库。

## 组件规范

- 页面组件负责布局编排，业务组件负责交互，基础组件负责纯 UI。
- 组件 props 必须声明 interface/type，避免隐式 `any`。
- 客户端组件仅在需要 state、effect、浏览器 API 或事件时使用 `'use client'`。
- 复杂组件拆分标准：单文件超过 250 行、状态超过 3 类、渲染分支超过 4 个时考虑拆分。
- 图标按钮必须有 `aria-label` 或 `title`。

## 状态与请求

- API 请求集中在 `frontend/src/lib/api.ts` 或按领域拆分到 `frontend/src/lib/<domain>.ts`。
- Zustand action 负责组合 UI 状态和 API 调用，但不要隐藏复杂后端业务规则。
- SSE 必须处理连接失败、流中断、空响应、重复发送和加载态恢复。
- `localStorage` 访问必须在浏览器环境中执行。

## UI 设计

- 企业级工作台优先：信息清晰、密度适中、操作稳定。
- 避免营销式 hero、过度装饰、无意义渐变背景。
- 聊天界面必须关注阅读体验：行高、代码块、复制、流式光标、错误态、空态。
- 移动端必须保证侧边栏、输入区、长代码块不会挤压主内容。
- 色彩使用 Tailwind theme token；新增品牌色先更新 `tailwind.config.ts`。

## 可访问性

- 表单控件必须有 label、placeholder 不能替代 label。
- 可点击元素使用 `button` 或 `a`，不要用 `div` 模拟按钮。
- 键盘可操作：发送、复制、菜单、退出都要能聚焦。
- 动效遵循 `prefers-reduced-motion`。

## 性能

- 避免在渲染过程中构造大对象或执行复杂计算。
- Markdown 和代码高亮要控制重渲染范围。
- 长消息列表后续应考虑虚拟滚动。
- 图片使用 Next.js Image 或显式尺寸，避免布局跳动。
