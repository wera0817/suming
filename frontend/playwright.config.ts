import { defineConfig } from '@playwright/test'
export default defineConfig({
  testDir: './tests', fullyParallel: false, workers: 1, timeout: 45000,
  use: { baseURL: 'http://127.0.0.1:5174', headless: true, screenshot: 'only-on-failure', trace: 'off' },
  reporter: [['list']], outputDir: '../test-results/browser',
  webServer: [
    { command: 'node tests/mock-provider.mjs', url: 'http://127.0.0.1:19091/health', reuseExistingServer: false },
    { command: 'npm run dev -- --port 5174', url: 'http://127.0.0.1:5174', env: { BACKEND_URL: 'http://127.0.0.1:18180' }, reuseExistingServer: false },
  ],
})
