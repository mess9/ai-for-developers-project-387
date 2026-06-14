import {beforeEach, describe, expect, it, vi} from 'vitest'
import {render, screen, waitFor} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {AdminPage} from '../pages/AdminPage'

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

    const calendar = screen.getByText('Календарь встреч').closest('section')!
    const bookedDays = calendar.querySelectorAll('button.cursor-pointer')
    expect(bookedDays.length).toBeGreaterThan(0)

    await user.click(bookedDays[0] as HTMLElement)
    expect(screen.queryByText('Предстоящие встречи')).not.toBeInTheDocument()
    expect(screen.getByText('Показать все')).toBeInTheDocument()

    await user.click(screen.getByText('Показать все'))
    expect(screen.getByText('Предстоящие встречи')).toBeInTheDocument()
  })
})
