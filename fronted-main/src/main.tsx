import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import HomePage from '@/components/home/HomePage';
import I18nProvider from '@/i18n/I18nProvider';
// 初始化 i18next（必须在渲染前执行副作用）
import '@/i18n';

// 字体：用 @fontsource 本地打包，替代原 next/font/google
// Inter: 300-900，对应原 --font-geist-sans
import '@fontsource/inter/300.css';
import '@fontsource/inter/400.css';
import '@fontsource/inter/500.css';
import '@fontsource/inter/600.css';
import '@fontsource/inter/700.css';
import '@fontsource/inter/800.css';
import '@fontsource/inter/900.css';
// JetBrains Mono: 400-700，对应原 --font-geist-mono
import '@fontsource/jetbrains-mono/400.css';
import '@fontsource/jetbrains-mono/500.css';
import '@fontsource/jetbrains-mono/600.css';
import '@fontsource/jetbrains-mono/700.css';

// antd 样式重置 + 全局样式
import 'antd/dist/reset.css';
import '@/styles/globals.css';

const container = document.getElementById('root');
if (!container) throw new Error('Root container #root not found');

createRoot(container).render(
  <StrictMode>
    <I18nProvider>
      <HomePage />
    </I18nProvider>
  </StrictMode>,
);
