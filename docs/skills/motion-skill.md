# Motion Skill

## 触发场景

当前端任务涉及页面过渡、聊天流式反馈、按钮交互、列表进入、骨架屏、微交互或动效质量优化时使用本 skill。

## 动效定位

AI WorkMate 的动效应该服务于“可靠、响应快、可理解”：

- 强调状态变化，而不是装饰。
- 优先微动效，避免干扰阅读。
- 聊天流式输出要让用户感到系统正在工作。
- 企业工作台动效应短、轻、可预测。

## 技术选择

默认优先级：

1. Tailwind animation/keyframes。
2. CSS transition。
3. Web Animations API。
4. Framer Motion，仅在复杂进出场、拖拽、布局动画确实需要时引入。

不默认引入 GSAP、Anime.js 等重型动效库。

## 时间规范

- Hover/focus：120ms 到 180ms。
- 弹层进入：180ms 到 240ms。
- 页面局部切换：200ms 到 320ms。
- Toast：160ms 到 220ms。
- Skeleton/shimmer：1000ms 到 1600ms 循环。
- 聊天打字点：1200ms 到 1600ms 循环。

## Easing 规范

- 默认进入：`cubic-bezier(0.16, 1, 0.3, 1)`。
- 默认退出：`cubic-bezier(0.7, 0, 0.84, 0)`。
- 普通 hover：`ease-out`。
- 避免过弹的 spring，除非是轻量愉悦反馈。

## 必须支持 Reduced Motion

所有非必要动效必须支持：

```css
@media (prefers-reduced-motion: reduce) {
  * {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
    scroll-behavior: auto !important;
  }
}
```

## 推荐动效场景

- 消息进入：轻微 fade + translateY。
- AI 流式中：闪烁光标或三点 loading。
- 发送按钮：disabled、loading、hover 状态清晰。
- 侧边栏菜单：hover 背景和 active 指示。
- 错误提示：轻量进入，不抖动整块布局。
- 知识库上传：进度条、解析状态、失败重试。

## 禁止事项

- 禁止让整页持续动。
- 禁止使用大面积炫光、背景漂浮装饰干扰内容。
- 禁止动画导致布局跳动。
- 禁止把 loading 做得比真实请求更慢。
- 禁止忽略移动端低性能设备。
