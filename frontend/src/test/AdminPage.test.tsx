import {beforeEach, describe, expect, it, vi} from 'vitest'
import {render, screen, waitFor, within} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {http, HttpResponse} from 'msw'
import {addDays, format} from 'date-fns'
import {AdminPage} from '../pages/AdminPage'
import {sampleBooking} from './mocks/handlers'
import {server} from './mocks/server'

beforeEach(() => {
  localStorage.clear()
  vi.restoreAllMocks()
})

async function login() {
  const user = userEvent.setup()
  render(<AdminPage />)
  await user.type(await screen.findByLabelText(/Токен/), 'test-token')
  await user.click(screen.getByRole('button', { name: 'Войти' }))
  return user
}

function getCalendarRegion() {
  return screen.getByRole('region', { name: 'Календарь встреч' })
}

describe('AdminPage', () => {
  it('вход по токену, показ типов и встреч', async () => {
    await login()
    expect(await screen.findAllByText('Deep dive')).toHaveLength(2)
    expect(await screen.findByText('Иван Гость')).toBeInTheDocument()
  })

  it('создаёт тип события', async () => {
    const user = await login()
    await screen.findByText('Иван Гость')

    await user.type(screen.getByLabelText(/Название/), 'Консультация')
    await user.click(screen.getByRole('button', { name: 'Создать' }))

    expect(await screen.findByText('Тип события создан')).toBeInTheDocument()
  })

  it('отменяет бронь', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const user = await login()
    await screen.findByText('Иван Гость')

    await user.click(screen.getAllByRole('button', { name: 'Отменить' })[0])
    expect(await screen.findByText('Бронь отменена')).toBeInTheDocument()
  })

  it('сбрасывает сессию на 401 и возвращает к форме входа', async () => {
    localStorage.setItem('adminToken', 'wrong-token')
    render(<AdminPage />)
    await waitFor(() =>
      expect(screen.getByRole('heading', { name: 'Вход для владельца' })).toBeInTheDocument(),
    )
  })

  it('календарь отображает занятые даты и фильтрует встречи по клику', async () => {
    const user = await login()
    await screen.findByText('Иван Гость')

    expect(screen.getByText('Календарь встреч')).toBeInTheDocument()
    expect(screen.getByText('Предстоящие встречи')).toBeInTheDocument()

    const calendar = getCalendarRegion()
    const calendarScope = within(calendar)
    const bookedDays = calendarScope.getAllByRole('button', { name: /встреч: 1/ })
    expect(bookedDays.length).toBeGreaterThan(0)
    expect(within(bookedDays[0]).getByTestId('booking-count-badge')).toBeInTheDocument()

    const emptyDate = format(addDays(new Date(), 1), 'yyyy-MM-dd')
    const emptyDay = calendarScope.getByRole('button', { name: emptyDate })
    expect(within(emptyDay).queryByTestId('booking-count-badge')).not.toBeInTheDocument()

    await user.click(bookedDays[0])
    expect(screen.queryByText('Предстоящие встречи')).not.toBeInTheDocument()
    expect(screen.getByText('Показать все')).toBeInTheDocument()
    const selectedBookedDay = within(getCalendarRegion()).getAllByRole('button', {
      name: /встреч: 1/,
      pressed: true,
    })[0]
    expect(within(selectedBookedDay).getByTestId('booking-count-badge')).toBeInTheDocument()

    await user.click(screen.getByText('Показать все'))
    expect(screen.getByText('Предстоящие встречи')).toBeInTheDocument()
  })

  it('фильтрует встречи, когда в календаре один занятый день', async () => {
    server.use(
      http.get('/api/v1/admin/bookings', ({ request }) => {
        if (request.headers.get('Authorization') !== 'Bearer test-token') {
          return HttpResponse.json({ status: 401, errorCode: 'UNAUTHORIZED' }, { status: 401 })
        }
        return HttpResponse.json([sampleBooking])
      }),
    )
    const user = await login()
    await screen.findByText('Иван Гость')

    const calendar = getCalendarRegion()
    const bookedDays = within(calendar).getAllByRole('button', { name: /встреч: 1/ })
    expect(bookedDays).toHaveLength(1)

    await user.click(bookedDays[0])
    expect(screen.getByRole('heading', { name: /Встречи за/ })).toBeInTheDocument()
    expect(screen.getByText('Иван Гость')).toBeInTheDocument()
    expect(screen.getByText('Показать все')).toBeInTheDocument()

    await user.click(screen.getByText('Показать все'))
    expect(screen.getByRole('heading', { name: 'Предстоящие встречи' })).toBeInTheDocument()
  })
})
