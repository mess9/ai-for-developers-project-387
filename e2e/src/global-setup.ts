import { API_BASE } from './env.js'
import { shouldManageStack, startStack } from './stack.js'

/**
 * Готовит стенд к прогону: при необходимости поднимает изолированный e2e-стек
 * (`compose.e2e.yaml`) и проверяет, что приложение отдаёт `/config`. Если стенд
 * не поднят и управление выключено — падаем с понятной подсказкой.
 */
async function globalSetup(): Promise<void> {
  if (shouldManageStack()) {
    startStack() // docker compose ... up --wait — вернётся, когда healthcheck'и зелёные
  }

  try {
    const res = await fetch(`${API_BASE}/config`)
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
  } catch (err) {
    throw new Error(
      `Приложение недоступно по ${API_BASE} (${(err as Error).message}).\n` +
        `Поднимите e2e-стек: docker compose -f compose.e2e.yaml up -d --build --wait\n` +
        `Либо запустите тесты с E2E_MANAGE_STACK=1 (стек поднимется и погасится сам).`,
    )
  }
}

export default globalSetup
