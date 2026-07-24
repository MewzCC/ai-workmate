# Motion Skill

## 触发场景

当前端任务涉及页面过渡、滚动进入、聊天流式反馈、按钮交互、列表进入、骨架屏、微交互或动效质量优化时使用本 skill。

如果动效发生在 `/oa` 工作台内，请同时读取 `docs/skills/oa-workbench-skill.md`，确保不破坏 Ant Design 组件、固定 Sider 和企业后台信息密度。

## 动效定位

AI WorkMate 的动效服务于“可靠、响应快、可理解”：

- 强调状态变化，而不是装饰。
- 优先微动效，避免干扰阅读。
- 企业工作台动效应短、轻、可预测。
- 营销官网可以更有表现力，但要遵守 reduced-motion。

## 技术选择

默认优先级：

1. CSS transition。
2. CSS keyframes。
3. IntersectionObserver + CSS class。
4. Web Animations API。
5. Framer Motion，仅在复杂进出场、拖拽、布局动画确实需要时引入。

不默认引入 GSAP、Anime.js 等重型动效库。

## 时间规范

- Hover/focus：120ms 到 180ms。
- Drawer/Modal 进入：180ms 到 260ms。
- 页面局部切换：200ms 到 320ms。
- Toast：160ms 到 220ms。
- Skeleton/shimmer：1000ms 到 1600ms 循环。
- 聊天打字点：1200ms 到 1600ms 循环。

## Reduced Motion

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

## OA 动效补充

- 左侧 Sider 固定，不参与页面滚动动画。
- 左侧 Sider 滚动条默认隐藏，hover 显示，不能造成布局抖动。
- OA 卡片 hover 只做轻微位移和阴影。
- Drawer、Modal、Table、Select 保持 Ant Design 默认动效为主。
- ECharts 更新不做大幅闪烁。

## 禁止事项

- 禁止让整页持续运动。
- 禁止大面积炫光、漂浮装饰干扰工作台内容。
- 禁止动效导致布局跳动。
- 禁止 loading 比真实请求更慢。
- 禁止忽略移动端低性能设备。

