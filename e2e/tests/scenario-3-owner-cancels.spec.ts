import { expect, test } from '../src/fixtures.js'
import { findFirstFreeSlot } from '../src/api.js'
import { readBookings, seedBooking, seedEventType } from '../src/db.js'

/**
 * Сценарий 3 — Владелец видит бронь и отменяет её, слот снова свободен.
 * Предусловие: тип события и активная бронь на конкретный слот (посев в БД).
 */
test('владелец отменяет бронь, и слот снова доступен гостю', async ({
  visitor,
  admin,
  ownerToken,
}) => {
  const eventType = await seedEventType({ name: 'Sync 30', durationMinutes: 30 })
  const slot = await findFirstFreeSlot(eventType.id)
  await seedBooking({
    eventTypeId: eventType.id,
    startAt: slot.startAt,
    endAt: slot.endAt,
    name: 'Иван Гость',
    meetingLink: 'https://zoom.us/j/123',
  })

  await admin.goto()
  await admin.login(ownerToken)
  await expect(admin.dashboardHeading()).toBeVisible()

  // (UI) Бронь видна в списке предстоящих встреч.
  const booking = admin.bookingItem('Иван Гость')
  await expect(booking).toBeVisible()
  await expect(booking).toContainText('Sync 30')

  // (UI) Отмена.
  await admin.cancelBooking('Иван Гость')
  await expect(admin.toast('Бронь отменена')).toBeVisible()
  await expect(admin.noBookings()).toBeVisible()

  // (БД) Запись брони удалена.
  expect(await readBookings()).toHaveLength(0)

  // (UI) Освободившийся слот снова доступен гостю.
  await visitor.goto()
  await visitor.selectEventType('Sync 30')
  await visitor.selectDay(slot.date)
  await expect(visitor.slotButton(slot.startAt)).toBeVisible()
})
