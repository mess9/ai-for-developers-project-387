import {describe, expect, it, vi} from 'vitest'
import {render, screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {BookingForm} from '../components/BookingForm'

const eventType = {
  id: '11111111-1111-1111-1111-111111111111',
  name: 'Intro call',
  description: null,
  durationMinutes: 30,
}
const slot = { startAt: '2026-06-11T09:00:00+03:00', endAt: '2026-06-11T09:30:00+03:00' }

function setup(onSubmit = vi.fn().mockResolvedValue(undefined)) {
  render(
    <BookingForm
      eventType={eventType}
      slot={slot}
      ownerTimeZone="Europe/Moscow"
      onSubmit={onSubmit}
      onClose={() => {}}
    />,
  )
  return onSubmit
}

describe('BookingForm', () => {
  it('требует имя и ссылку', async () => {
    const onSubmit = setup()
    await userEvent.click(screen.getByRole('button', { name: 'Забронировать' }))
    expect(screen.getByText('Укажите имя')).toBeInTheDocument()
    expect(screen.getByText('Укажите ссылку на встречу')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('валидирует формат ссылки', async () => {
    const onSubmit = setup()
    await userEvent.type(screen.getByLabelText(/Имя/), 'Гость')
    await userEvent.type(screen.getByLabelText(/Ссылка/), 'not-a-url')
    await userEvent.click(screen.getByRole('button', { name: 'Забронировать' }))
    expect(screen.getByText(/корректную ссылку/i)).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('отправляет очищенные данные (eventTypeId/startAt добавляет страница)', async () => {
    const onSubmit = setup()
    await userEvent.type(screen.getByLabelText(/Имя/), '  Гость  ')
    await userEvent.type(screen.getByLabelText(/Ссылка/), 'https://meet.example.com/x')
    await userEvent.click(screen.getByRole('button', { name: 'Забронировать' }))
    expect(onSubmit).toHaveBeenCalledWith({
      name: 'Гость',
      meetingLink: 'https://meet.example.com/x',
      description: undefined,
    })
  })
})
