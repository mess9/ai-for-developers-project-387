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
 * Детерминированный выбор слота: первый свободный слот первого «стабильного» дня
 * окна — то есть дня строго после сегодняшнего, в котором есть свободные слоты.
 *
 * Сегодня намеренно пропускаем: текущий день частично «съеден» временем суток
 * (прошедшие слоты отфильтрованы), и к вечеру у него может не остаться слотов —
 * особенно у длинных типов. Из-за этого кнопка дня в календаре становится
 * неактивной, и навигация по нему виснет до таймаута теста. Любой будущий день
 * окна свободен на все рабочие часы, поэтому выбор не зависит ни от времени
 * суток, ни от скорости раннера: день всегда навигабелен и наполнен слотами.
 */
export async function findFirstFreeSlot(eventTypeId: string): Promise<DatedSlot> {
  const availability = await getAvailability(eventTypeId)
  const today = availability.days[0]?.date
  const day = availability.days.find(
    (d) => d.date !== today && d.hasFreeSlots && d.slots.length > 0,
  )
  if (!day) throw new Error(`Нет свободных слотов на будущих днях для типа ${eventTypeId}`)
  return { date: day.date, ...day.slots[0] }
}
