import type { Metadata } from 'next';
import { Inter, JetBrains_Mono } from 'next/font/google';
import Script from 'next/script';
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
  description: '面向企业团队的 AI Agent 官网与产品体验入口',
};

export default function HomeRootLayout({ children }: { children: React.ReactNode }) {
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
        <Script src="/iconfont/iconfont.js" strategy="beforeInteractive" />
      </body>
    </html>
  );
}
