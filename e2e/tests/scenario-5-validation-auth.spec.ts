import { expect, test } from '../src/fixtures.js'
import { findFirstFreeSlot } from '../src/api.js'
import { readBookings, seedEventType } from '../src/db.js'

/**
 * Сценарий 5 — Валидация формы брони и защита админки.
 */
test.describe('сценарий 5', () => {
  test('валидация формы брони у гостя', async ({ visitor }) => {
    const eventType = await seedEventType({ name: 'Валидация', durationMinutes: 30 })
    const slot = await findFirstFreeSlot(eventType.id)

    await visitor.goto()
    await visitor.selectEventType('Валидация')
    await visitor.selectDay(slot.date)
    await visitor.openSlot(slot.startAt)

    // Пустая отправка — ошибки обязательных полей, запрос не уходит.
    await visitor.submitBooking()
    await expect(visitor.fieldError('Укажите имя')).toBeVisible()
    await expect(visitor.fieldError('Укажите ссылку на встречу')).toBeVisible()

    // Невалидный URL — отдельное сообщение.
    await visitor.fillGuestDetails({ name: 'Гость', meetingLink: 'не-ссылка' })
    await visitor.submitBooking()
    await expect(visitor.fieldError('Введите корректную ссылку (URL)')).toBeVisible()

    // (БД) Ни одной брони не создано.
    expect(await readBookings()).toHaveLength(0)
  })

  test('защита админки токеном', async ({ admin, ownerToken }) => {
    // Без токена — форма входа, защищённые данные не подгружаются.
    await admin.goto()
    await expect(admin.loginHeading()).toBeVisible()

    // Неверный токен — 401, сброс к форме входа.
    await admin.login('invalid-token-0000')
    await expect(admin.toast('Сессия истекла, войдите снова')).toBeVisible()
    await expect(admin.loginHeading()).toBeVisible()

    // Валидный токен — панель владельца открывается.
    await admin.login(ownerToken)
    await expect(admin.dashboardHeading()).toBeVisible()
    await expect(admin.page.getByRole('heading', { name: 'Типы событий' })).toBeVisible()
    await expect(admin.page.getByRole('heading', { name: 'Предстоящие встречи' })).toBeVisible()
  })
})
