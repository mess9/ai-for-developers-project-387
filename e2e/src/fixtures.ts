import { test as base } from '@playwright/test'
import { closePool, getOwnerToken, truncateData } from './db.js'
import { AdminPage } from './pages/admin.page.js'
import { VisitorPage } from './pages/visitor.page.js'

interface TestFixtures {
  visitor: VisitorPage
  admin: AdminPage
  /** Авто-фикстура сброса БД перед каждым тестом (не используется напрямую). */
  forEachTest: void
}

interface WorkerFixtures {
  /** Токен владельца, прочитанный из БД один раз на воркер. */
  ownerToken: string
}

export const test = base.extend<TestFixtures, WorkerFixtures>({
  ownerToken: [
    async ({}, use) => {
      const token = await getOwnerToken()
      await use(token)
      // Пул живёт весь воркер; закрываем после всех тестов воркера.
      await closePool()
    },
    { scope: 'worker' },
  ],

  // Автофикстура: чистый старт каждого сценария — изоляция тестов.
  // eslint-disable-next-line no-empty-pattern
  forEachTest: [
    async ({}, use) => {
      await truncateData()
      await use()
    },
    { auto: true },
  ],

  visitor: async ({ page }, use) => {
    await use(new VisitorPage(page))
  },

  admin: async ({ page }, use) => {
    await use(new AdminPage(page))
  },
})

export { expect } from '@playwright/test'
