import type { Metadata } from 'next';
import { Inter, JetBrains_Mono } from 'next/font/google';
import 'antd/dist/reset.css';
import './globals.css';

const geistSans = Inter({
  subsets: ['latin'],
  display: 'swap',
  variable: '--font-geist-sans',
  weight: ['300', '400', '500', '600', '700', '800', '900'],
});

const geistMono = JetBrains_Mono({
  subsets: ['latin'],
  display: 'swap',
  variable: '--font-geist-mono',
  weight: ['400', '500', '600', '700'],
});

export const metadata: Metadata = {
  title: 'AI WorkMate - 企业 AI 工作入口',
  description: '基于 Spring AI + Next.js 构建的企业级 AI Agent 平台',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  // 阻止浏览器自动恢复滚动位置，每次刷新都从顶部开始
  const scrollResetScript = `
    if (typeof window !== 'undefined' && 'scrollRestoration' in window.history) {
      window.history.scrollRestoration = 'manual';
    }
    window.scrollTo(0, 0);
  `;

  return (
    <html lang="zh-CN" className={`${geistSans.variable} ${geistMono.variable}`}>
      <head>
        <script dangerouslySetInnerHTML={{ __html: scrollResetScript }} />
      </head>
      <body className="min-h-screen overflow-x-hidden antialiased">
        {children}
      </body>
    </html>
  );
}
