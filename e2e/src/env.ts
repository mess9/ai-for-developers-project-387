/**
 * Параметры окружения для e2e: адрес приложения и доступ к БД.
 * Значения по умолчанию указывают на изолированный e2e-стек (`compose.e2e.yaml`:
 * порты 8082/5433), а не на дев-стек (8081/5432). Так `npm test` не заденет
 * данные, в которых вы тыкаете руками.
 */
export const BASE_URL = process.env.E2E_BASE_URL ?? 'http://localhost:8082'

export const API_BASE = `${BASE_URL}/api/v1`

export const DB_CONFIG = {
  host: process.env.PGHOST ?? 'localhost',
  port: Number(process.env.PGPORT ?? 5433),
  database: process.env.PGDATABASE ?? 'booking',
  user: process.env.PGUSER ?? 'booking',
  password: process.env.PGPASSWORD ?? 'booking',
}

/**
 * Часовой пояс владельца. Браузер в тестах принудительно ставится в этот же
 * пояс (см. `playwright.config.ts`), чтобы:
 *  - тесты не зависели от пояса CI-раннера;
 *  - время слота на экране совпадало с поясом владельца без второй подписи
 *    «у вас это …», что делает текстовые селекторы по времени однозначными.
 */
export const OWNER_TZ = 'Asia/Yerevan'
