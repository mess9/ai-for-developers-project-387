import { expect, type Locator, type Page } from '@playwright/test'

/** Параметры создаваемого типа события. */
export interface NewEventType {
  name: string
  description?: string
  durationMinutes: number
}

/**
 * Экран владельца (`/admin`): вход по токену, секции «Типы событий» и
 * «Предстоящие встречи».
 */
export class AdminPage {
  constructor(readonly page: Page) {}

  /**
   * Прямой переход на админку по секретной ссылке. Сервер отдаёт `index.html`
   * на клиентские маршруты SPA (SPA-fallback в бэкенде), поэтому deep-link и F5
   * на `/admin` работают.
   */
  async goto(): Promise<void> {
    await this.page.goto('/admin')
    await expect(this.page).toHaveURL(/\/admin$/)
  }

  loginHeading(): Locator {
    return this.page.getByRole('heading', { name: 'Вход для владельца' })
  }

  dashboardHeading(): Locator {
    return this.page.getByRole('heading', { name: 'Панель владельца' })
  }

  /** Вход через форму (интерфейсный путь): ввод токена и сабмит. */
  async login(token: string): Promise<void> {
    await this.page.getByLabel('Токен доступа').fill(token)
    await this.page.getByRole('button', { name: 'Войти' }).click()
  }

  async createEventType(input: NewEventType): Promise<void> {
    await this.page.getByLabel('Название').fill(input.name)
    if (input.description !== undefined) {
      await this.page.getByLabel('Описание').fill(input.description)
    }
    await this.page.getByLabel('Длительность').selectOption(String(input.durationMinutes))
    await this.page.getByRole('button', { name: 'Создать' }).click()
  }

  /** Элемент списка типов событий с указанным названием. */
  eventTypeItem(name: string): Locator {
    return this.page.getByRole('listitem').filter({ hasText: name })
  }

  /** Элемент списка предстоящих встреч по имени гостя. */
  bookingItem(guestName: string): Locator {
    return this.page.getByRole('listitem').filter({ hasText: guestName })
  }

  /** Отменяет бронь гостя, подтверждая `window.confirm`. */
  async cancelBooking(guestName: string): Promise<void> {
    this.page.once('dialog', (dialog) => dialog.accept())
    await this.bookingItem(guestName).getByRole('button', { name: 'Отменить' }).click()
  }

  noBookings(): Locator {
    return this.page.getByText('Предстоящих встреч нет.')
  }

  toast(message: string): Locator {
    return this.page.getByRole('status').filter({ hasText: message })
  }
}
