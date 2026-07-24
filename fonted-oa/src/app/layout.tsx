import type { Metadata } from 'next';
import { Inter, JetBrains_Mono } from 'next/font/google';
import Script from 'next/script';
import 'antd/dist/reset.css';
import './globals.css';
import Providers from './providers';

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
  title: 'AI WorkMate OA - 企业驾驶舱',
  description: '企业级 OA 工作台、权限控制、ECharts 图表与 AI 任务入口',
};

export default function OaRootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN" className={`${geistSans.variable} ${geistMono.variable}`}>
      <body className="min-h-screen overflow-x-hidden antialiased">
        <Providers>{children}</Providers>
        <Script src="/iconfont/iconfont.js" strategy="beforeInteractive" />
      </body>
    </html>
  );
}
