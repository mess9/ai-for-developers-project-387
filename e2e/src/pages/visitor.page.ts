import { expect, type Locator, type Page } from '@playwright/test'
import { OWNER_TZ } from '../env.js'
import { dayOfMonth, hhmmInTz } from '../time.js'

/** Данные гостя для формы брони. */
export interface GuestDetails {
  name?: string
  meetingLink?: string
  description?: string
}

/**
 * Экран посетителя (`/`): список типов → доступность (сетка дней) → слоты →
 * модальная форма брони. Селекторы — пользовательские (роли и видимый текст).
 */
export class VisitorPage {
  constructor(readonly page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto('/')
    await expect(this.page.getByRole('heading', { name: 'Запись на звонок' })).toBeVisible()
  }

  ownerBanner(): Locator {
    return this.page.getByText('Часовой пояс владельца:')
  }

  async selectEventType(name: string): Promise<void> {
    await this.page.getByRole('button', { name }).click()
    await expect(this.page.getByRole('button', { name: '← К типам встреч' })).toBeVisible()
  }

  /** Кликает день окна по дате `yyyy-MM-dd`: среди активных кнопок-дней ровно одна с этим номером. */
  async selectDay(isoDate: string): Promise<void> {
    const day = dayOfMonth(isoDate)
    await this.page
      .getByRole('button', { name: day, exact: true })
      .and(this.page.locator(':enabled'))
      .first()
      .click()
    await expect(this.page.getByRole('heading', { name: /^Слоты на/ })).toBeVisible()
  }

  /** Кнопка слота по моменту начала (подпись — время в поясе владельца). */
  slotButton(startAt: string): Locator {
    return this.page.getByRole('button', { name: hhmmInTz(startAt, OWNER_TZ), exact: true })
  }

  async openSlot(startAt: string): Promise<void> {
    await this.slotButton(startAt).click()
    await expect(this.page.getByRole('heading', { name: /^Запись:/ })).toBeVisible()
  }

  async fillGuestDetails(details: GuestDetails): Promise<void> {
    if (details.name !== undefined) await this.page.getByLabel('Имя').fill(details.name)
    if (details.meetingLink !== undefined) {
      await this.page.getByLabel('Ссылка на встречу').fill(details.meetingLink)
    }
    if (details.description !== undefined) {
      await this.page.getByLabel('Описание').fill(details.description)
    }
  }

  async submitBooking(): Promise<void> {
    await this.page.getByRole('button', { name: 'Забронировать' }).click()
  }

  /** Удобный составной шаг: тип → день → слот → форма → отправка. */
  async book(
    eventTypeName: string,
    slot: { date: string; startAt: string },
    details: GuestDetails,
  ): Promise<void> {
    await this.selectEventType(eventTypeName)
    await this.selectDay(slot.date)
    await this.openSlot(slot.startAt)
    await this.fillGuestDetails(details)
    await this.submitBooking()
  }

  fieldError(message: string): Locator {
    return this.page.getByText(message)
  }

  toast(message: string): Locator {
    return this.page.getByRole('status').filter({ hasText: message })
  }
}
