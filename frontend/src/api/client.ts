import createClient from 'openapi-fetch'
import type {paths} from './types'

// Абсолютный baseUrl: в браузере — тот же origin, что и страница; в тестовой
// (node) среде относительный путь не парсится в URL, поэтому берём origin явно.
const origin = typeof window !== 'undefined' ? window.location.origin : 'http://localhost'
const baseUrl = `${origin}/api/v1`

// Ленивая обёртка: всегда берём актуальный globalThis.fetch на момент вызова,
// а не на момент создания клиента (иначе в тестах MSW не успевает его пропатчить).
const lazyFetch: typeof fetch = (...args) => globalThis.fetch(...args)

export const apiClient = createClient<paths>({ baseUrl, fetch: lazyFetch })

export const adminClient = createClient<paths>({ baseUrl, fetch: lazyFetch })

adminClient.use({
  onRequest({ request }) {
    const token = localStorage.getItem('adminToken')
    if (token) {
      request.headers.set('Authorization', `Bearer ${token}`)
    }
    return request
  },
})
