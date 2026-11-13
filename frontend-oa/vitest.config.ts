import { defineConfig } from 'vitest/config';
import path from 'node:path';

export default defineConfig({
  test: {
    environment: 'jsdom',
    setupFiles: ['./vitest.setup.ts'],
    // Multi-step AntD/jsdom interactions can exceed 5s on Windows even with one worker.
    // This is a test completion budget; assertion-level waits and business checks stay unchanged.
    testTimeout: 10_000,
    // Keep AntD/jsdom page setup from being starved by a machine-sized worker pool.
    maxWorkers: 2,
    minWorkers: 1,
    exclude: ['e2e/**', 'node_modules/**', 'dist/**'],
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
});
