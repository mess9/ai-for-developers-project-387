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
    expect(await screen.findByText('Deep dive')).toBeInTheDocument()
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

    await user.click(screen.getByRole('button', { name: 'Отменить' }))
    expect(await screen.findByText('Бронь отменена')).toBeInTheDocument()
  })

  it('сбрасывает сессию на 401 и возвращает к форме входа', async () => {
    localStorage.setItem('adminToken', 'wrong-token')
    render(<AdminPage />)
    await waitFor(() =>
      expect(screen.getByRole('heading', { name: 'Вход для владельца' })).toBeInTheDocument(),
    )
  })
})
