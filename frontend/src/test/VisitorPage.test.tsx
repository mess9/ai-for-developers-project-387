import {describe, expect, it} from 'vitest'
import {render, screen, waitFor} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {http, HttpResponse} from 'msw'
import {VisitorPage} from '../pages/VisitorPage'
import {server} from './mocks/server'

async function selectTypeAndDay() {
  const user = userEvent.setup()
  render(<VisitorPage />)

  // 1. список типов
  await screen.findByText('Intro call')
  await user.click(screen.getByText('Intro call'))

  // 2-3. доступность: ждём сетку и кликаем первый свободный день
  await waitFor(() => expect(screen.getAllByRole('heading', { level: 3 }).length).toBeGreaterThan(0))
  const freeDay = screen
    .getAllByRole('button')
    .find((b) => /^\d+$/.test(b.textContent?.trim() ?? '') && !(b as HTMLButtonElement).disabled)
  await user.click(freeDay!)

  // 4. слоты
  await screen.findByText('09:00')
  return user
}

describe('VisitorPage', () => {
  it('проходит путь тип → день → слот → бронь и показывает успех', async () => {
    const user = await selectTypeAndDay()

    await user.click(screen.getByText('09:00'))
    await screen.findByRole('button', { name: 'Забронировать' })

    await user.type(screen.getByLabelText(/Имя/), 'Гость')
    await user.type(screen.getByLabelText(/Ссылка/), 'https://meet.example.com/x')
    await user.click(screen.getByRole('button', { name: 'Забронировать' }))

    expect(await screen.findByText('Вы записаны')).toBeInTheDocument()
  })

  it('на 409 показывает, что время заняли, и перезапрашивает доступность', async () => {
    const user = await selectTypeAndDay()

    server.use(
      http.post('/api/v1/bookings', () =>
        HttpResponse.json(
          { status: 409, errorCode: 'SLOT_ALREADY_BOOKED' },
          { status: 409, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )

    await user.click(screen.getByText('09:00'))
    await user.type(screen.getByLabelText(/Имя/), 'Гость')
    await user.type(screen.getByLabelText(/Ссылка/), 'https://meet.example.com/x')
    await user.click(screen.getByRole('button', { name: 'Забронировать' }))

    expect(await screen.findByText('Это время только что заняли')).toBeInTheDocument()
  })
})
