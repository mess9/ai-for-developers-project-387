import { API_BASE } from './env.js'

/** Свободный слот, как его отдаёт бэк (момент начала и конца). */
export interface Slot {
  startAt: string
  endAt: string
}

/** Свободный слот вместе с днём окна, в котором он лежит (`yyyy-MM-dd` в TZ владельца). */
export interface DatedSlot extends Slot {
  date: string
}

interface AvailabilityDay {
  date: string
  hasFreeSlots: boolean
  slots: Slot[]
}

interface Availability {
  eventTypeId: string
  durationMinutes: number
  ownerTimeZone: string
  days: AvailabilityDay[]
}

async function getJson<T>(path: string): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`)
  if (!res.ok) throw new Error(`GET ${path} → ${res.status}`)
  return res.json() as Promise<T>
}

export function getConfig(): Promise<{ ownerTimeZone: string }> {
  return getJson('/config')
}

export function getAvailability(eventTypeId: string): Promise<Availability> {
  return getJson(`/event-types/${eventTypeId}/availability`)
}

/**
 * Детерминированный выбор слота: первый свободный слот первого дня окна, в
 * котором есть свободные слоты. Так тест не зависит от текущей даты/времени.
 */
export async function findFirstFreeSlot(eventTypeId: string): Promise<DatedSlot> {
  const availability = await getAvailability(eventTypeId)
  const day = availability.days.find((d) => d.hasFreeSlots && d.slots.length > 0)
  if (!day) throw new Error(`Нет свободных слотов для типа ${eventTypeId}`)
  return { date: day.date, ...day.slots[0] }
}
