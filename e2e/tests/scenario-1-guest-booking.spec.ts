import { expect, test } from '../src/fixtures.js'
import { findFirstFreeSlot } from '../src/api.js'
import { readBookings, seedEventType } from '../src/db.js'

/**
 * Сценарий 1 — Гость бронирует слот (happy path).
 * Предусловие: один тип события (посев в БД). Действия — через UI.
 * Проверки: тост, исчезновение слота из выдачи, запись в БД.
 */
test('гость бронирует свободный слот', async ({ visitor }) => {
  const eventType = await seedEventType({ name: 'Intro call', durationMinutes: 30 })
  const slot = await findFirstFreeSlot(eventType.id)

  await visitor.goto()
  await expect(visitor.ownerBanner()).toBeVisible()

  await visitor.book(eventType.name, slot, {
    name: 'Гость Тестовый',
    meetingLink: 'https://meet.example.com/intro',
    description: 'Обсудим интеграцию',
  })

  // (UI) Подтверждение и исчезновение слота после рефетча.
  await expect(visitor.toast('Вы записаны')).toBeVisible()
  await visitor.selectDay(slot.date)
  await expect(visitor.slotButton(slot.startAt)).toHaveCount(0)

  // (БД) Появилась ровно одна бронь с введёнными данными на выбранный слот.
  const bookings = await readBookings()
  expect(bookings).toHaveLength(1)
  expect(bookings[0]).toMatchObject({
    eventTypeId: eventType.id,
    name: 'Гость Тестовый',
    meetingLink: 'https://meet.example.com/intro',
    description: 'Обсудим интеграцию',
  })
  expect(bookings[0].startAt.getTime()).toBe(new Date(slot.startAt).getTime())
})
