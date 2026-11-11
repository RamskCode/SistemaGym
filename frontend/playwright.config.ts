import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: 'list',
  timeout: 30_000,
  use: {
    browserName: 'chromium',
    headless: true,
    baseURL: process.env.SISTEMAGYM_E2E_FRONTEND,
    trace: 'off',
    screenshot: 'off',
    video: 'off',
  },
})
