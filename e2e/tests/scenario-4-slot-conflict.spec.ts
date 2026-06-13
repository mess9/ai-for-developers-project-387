import { expect, test } from '../src/fixtures.js'
import { findFirstFreeSlot } from '../src/api.js'
import { readBookings, seedBooking, seedEventType } from '../src/db.js'

/**
 * Сценарий 4 — Занятое время недоступно (в т.ч. для другого типа).
 * Правило занятости: на одно время не больше одной брони, даже разных типов.
 *
 * Все слоты берём на «стабильном» дне (не сегодня): такой день всегда полностью
 * свободен в рабочие часы, поэтому у короткого и длинного типов он одинаково
 * навигабелен и наполнен слотами вне зависимости от времени суток и скорости
 * раннера. Это убирает флак, когда у длинного типа сегодняшний день к вечеру
 * оказывался без слотов и кнопка дня была неактивна.
 */
test('занятое время скрыто у другого типа и не бронируется при гонке', async ({ visitor }) => {
  const short = await seedEventType({ name: 'Короткая', durationMinutes: 30 })
  const long = await seedEventType({ name: 'Длинная', durationMinutes: 60 })

  // Бронируем слот короткого типа напрямую в БД.
  const bookedSlot = await findFirstFreeSlot(short.id)
  await seedBooking({
    eventTypeId: short.id,
    startAt: bookedSlot.startAt,
    endAt: bookedSlot.endAt,
    name: 'Занятое время',
    meetingLink: 'https://meet.example.com/busy',
  })

  // (UI) У другого типа пересекающийся старт не показывается, но день остаётся
  // навигабельным — на нём ещё много свободных слотов длинного типа.
  await visitor.goto()
  await visitor.selectEventType('Длинная')
  await visitor.selectDay(bookedSlot.date)
  await expect(visitor.slotButton(bookedSlot.startAt)).toHaveCount(0)

  // (Гонка) Гость открывает свободный слот длинного типа на том же дне; в этот
  // момент слот занимают (посев конкурирующей брони), затем гость шлёт форму.
  const raceSlot = await findFirstFreeSlot(long.id)
  await visitor.openSlot(raceSlot.startAt)
  await visitor.fillGuestDetails({
    name: 'Опоздавший гость',
    meetingLink: 'https://meet.example.com/late',
  })

  await seedBooking({
    eventTypeId: long.id,
    startAt: raceSlot.startAt,
    endAt: raceSlot.endAt,
    name: 'Перехватчик',
    meetingLink: 'https://meet.example.com/winner',
  })

  await visitor.submitBooking()

  // (UI) Сообщение о конфликте.
  await expect(visitor.toast('Это время только что заняли')).toBeVisible()

  // (БД) Конкурирующая бронь есть, а брони опоздавшего гостя — нет.
  const bookings = await readBookings()
  const names = bookings.map((b) => b.name)
  expect(names).toContain('Перехватчик')
  expect(names).not.toContain('Опоздавший гость')
})
