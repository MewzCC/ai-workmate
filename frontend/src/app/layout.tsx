import type { Metadata } from 'next';
import 'antd/dist/reset.css';
import './globals.css';

export const metadata: Metadata = {
  title: 'AI WorkMate - 企业 AI 工作入口',
  description: '基于 Spring AI + Next.js 构建的企业级 AI Agent 平台',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN">
      <body className="min-h-screen overflow-x-hidden">
        {children}
      </body>
    </html>
  );
}
