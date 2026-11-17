import '@testing-library/jest-dom/vitest';
import { configure } from '@testing-library/react';
import i18n from '@/i18n';

// 初始化 react-i18next 实例并强制中文，保证测试用例按 zh-CN 文案断言
await i18n.changeLanguage('zh-CN');

// AntD 弹层（Modal/Drawer）带进场动画，慢速 CI 机器（2 核）上 1s 默认异步预算不足以完成渲染，
// 会让 findBy*/waitFor 出现与被测行为无关的偶发失败。这里适度放宽异步查询默认预算。
configure({ asyncUtilTimeout: 2_000 });

class ResizeObserverMock implements ResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}

Object.defineProperty(window, 'ResizeObserver', {
  writable: true,
  value: ResizeObserverMock,
});
Object.defineProperty(globalThis, 'ResizeObserver', {
  writable: true,
  value: ResizeObserverMock,
});

const getComputedStyle = window.getComputedStyle.bind(window);
Object.defineProperty(window, 'getComputedStyle', {
  writable: true,
  value: (element: Element) => getComputedStyle(element),
});

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => undefined,
    removeListener: () => undefined,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
    dispatchEvent: () => false,
  }),
});
