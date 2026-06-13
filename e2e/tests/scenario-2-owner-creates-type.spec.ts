import { expect, test } from '../src/fixtures.js'
import { findFirstFreeSlot } from '../src/api.js'
import { readEventTypes } from '../src/db.js'

/**
 * Сценарий 2 — Владелец создаёт тип события, гость его видит.
 * Тип создаётся через UI намеренно (это и есть проверяемый путь владельца).
 */
test('владелец создаёт тип события, и гость его видит', async ({ visitor, admin, ownerToken }) => {
  await admin.goto()
  await admin.login(ownerToken)
  await expect(admin.dashboardHeading()).toBeVisible()

  await admin.createEventType({
    name: 'Demo звонок',
    description: 'Демонстрация продукта',
    durationMinutes: 45,
  })

  // (UI) Тост и появление типа в списке владельца.
  await expect(admin.toast('Тип события создан')).toBeVisible()
  await expect(admin.eventTypeItem('Demo звонок')).toBeVisible()

  // (БД) В таблице типов — одна запись с заданными полями.
  const eventTypes = await readEventTypes()
  expect(eventTypes).toHaveLength(1)
  expect(eventTypes[0]).toMatchObject({
    name: 'Demo звонок',
    description: 'Демонстрация продукта',
    durationMinutes: 45,
  })

  // (UI) Гость видит тип в публичном списке и может открыть его доступность.
  await visitor.goto()
  await expect(visitor.page.getByText('Demo звонок')).toBeVisible()

  // Доступность считается под длительность типа: есть хотя бы один свободный слот.
  const slot = await findFirstFreeSlot(eventTypes[0].id)
  await visitor.selectEventType('Demo звонок')
  await visitor.selectDay(slot.date)
  await expect(visitor.slotButton(slot.startAt)).toBeVisible()
})
