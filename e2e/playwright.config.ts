import { defineConfig, devices } from '@playwright/test'
import { BASE_URL, OWNER_TZ } from './src/env.js'

export default defineConfig({
  testDir: './tests',
  globalSetup: './src/global-setup.ts',
  globalTeardown: './src/global-teardown.ts',

  // Тесты делят одну БД и сбрасывают её перед каждым сценарием, поэтому идут
  // строго последовательно — параллелизм привёл бы к гонке за данные.
  fullyParallel: false,
  workers: 1,

  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,

  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',

  use: {
    baseURL: BASE_URL,
    // Браузер в поясе владельца — независимость от пояса раннера и однозначные
    // селекторы по времени слотов (без второй подписи «у вас это …»).
    timezoneId: OWNER_TZ,
    locale: 'ru-RU',
    trace: 'on-first-retry',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
})
