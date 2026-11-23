import { defineConfig } from '@playwright/test';
import base from './playwright.config';

export default defineConfig({
  ...base,
  grep: /接口失败时不白屏|独立入口在当前视口/,
  projects: [
    [320, 640], [390, 844], [720, 680], [721, 680], [1024, 600], [1920, 900],
  ].map(([width, height]) => ({
    name: `${width}x${height}`,
    use: { viewport: { width, height } },
  })),
});
