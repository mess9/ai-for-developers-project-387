import {useCallback, useEffect, useState} from 'react'
import {formatInTimeZone} from 'date-fns-tz'
import {useConfig} from '../hooks/useConfig'
import {adminClient} from '../api/client'
import type {components} from '../api/types'
import {AdminBookingCalendar} from '../components/AdminBookingCalendar'
import {EventTypeForm} from '../components/EventTypeForm'
import {BookingsList} from '../components/BookingsList'
import {Toast} from '../components/Toast'
import {BookingError, fieldErrors, problemMessage} from '../lib/errors'

type EventType = components['schemas']['EventType']
type Booking = components['schemas']['Booking']
type EventTypeRequest = components['schemas']['EventTypeRequest']

export function AdminPage() {
  const { config } = useConfig()
  const [token, setToken] = useState('')
  const [isAuthenticated, setIsAuthenticated] = useState(!!localStorage.getItem('adminToken'))
  const [eventTypes, setEventTypes] = useState<EventType[]>([])
  const [bookings, setBookings] = useState<Booking[]>([])
  const [selectedDate, setSelectedDate] = useState<string | null>(null)
  const [toast, setToast] = useState<string | null>(null)

  const logout = useCallback((message?: string) => {
    localStorage.removeItem('adminToken')
    setIsAuthenticated(false)
    setToken('')
    setEventTypes([])
    setBookings([])
    if (message) setToast(message)
  }, [])

  const loadData = useCallback(async () => {
    const [types, books] = await Promise.all([
      adminClient.GET('/admin/event-types'),
      adminClient.GET('/admin/bookings'),
    ])
    if (types.response.status === 401 || books.response.status === 401) {
      logout('Сессия истекла, войдите снова')
      return
    }
    setEventTypes(types.data ?? [])
    setBookings(books.data ?? [])
  }, [logout])

  useEffect(() => {
    // loadData асинхронна: setState вызывается только после await, не в теле эффекта.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    if (isAuthenticated) loadData()
  }, [isAuthenticated, loadData])

  const handleLogin = (e: React.FormEvent) => {
    e.preventDefault()
    if (token.trim()) {
      localStorage.setItem('adminToken', token.trim())
      setIsAuthenticated(true)
    }
  }

  const handleCreate = async (data: EventTypeRequest) => {
    const { error, response } = await adminClient.POST('/admin/event-types', { body: data })
    if (error) {
      if (response.status === 401) {
        logout('Сессия истекла, войдите снова')
        return
      }
      if (error.errorCode === 'VALIDATION_FAILED') {
        throw new BookingError(problemMessage(error), fieldErrors(error))
      }
      throw new BookingError(problemMessage(error))
    }
    setToast('Тип события создан')
    await loadData()
  }

  const handleCancel = async (id: string) => {
    if (!confirm('Отменить эту бронь?')) return
    const { error, response } = await adminClient.DELETE('/admin/bookings/{id}', {
      params: { path: { id } },
    })
    if (error) {
      if (response.status === 401) {
        logout('Сессия истекла, войдите снова')
        return
      }
      setToast(problemMessage(error))
      return
    }
    setToast('Бронь отменена')
    await loadData()
  }

  const copyPublicLink = async () => {
    await navigator.clipboard.writeText(window.location.origin)
    setToast('Публичная ссылка скопирована')
  }

  if (!config) {
    return <div className="p-4">Загрузка…</div>
  }

  if (!isAuthenticated) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
        <div className="bg-white rounded-lg shadow p-6 max-w-md w-full">
          <h1 className="text-2xl font-bold mb-4">Вход для владельца</h1>
          <form onSubmit={handleLogin}>
            <label htmlFor="admin-token" className="block text-sm font-medium mb-1">
              Токен доступа
            </label>
            <input
              id="admin-token"
              type="password"
              value={token}
              onChange={(e) => setToken(e.target.value)}
              className="w-full border border-gray-300 rounded px-3 py-2 mb-4 focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="Введите ваш токен"
            />
            <button
              type="submit"
              className="w-full px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
            >
              Войти
            </button>
          </form>
        </div>
        {toast && <Toast message={toast} onDismiss={() => setToast(null)} />}
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-4xl mx-auto p-4">
        <header className="mb-6 flex justify-between items-start gap-4">
          <div>
            <h1 className="text-2xl font-bold mb-1">Панель владельца</h1>
            <p className="text-sm text-gray-600">Часовой пояс: {config.ownerTimeZone}</p>
          </div>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={copyPublicLink}
              className="px-3 py-2 bg-gray-200 rounded hover:bg-gray-300"
            >
              Копировать ссылку
            </button>
            <button
              type="button"
              onClick={() => logout()}
              className="px-3 py-2 border border-gray-300 rounded hover:bg-gray-100"
            >
              Выйти
            </button>
          </div>
        </header>

        <section className="mb-8">
          <h2 className="text-xl font-bold mb-3">Типы событий</h2>
          <EventTypeForm onCreate={handleCreate} />
          {eventTypes.length === 0 ? (
            <p className="text-gray-500">Типов событий пока нет.</p>
          ) : (
            <ul className="space-y-2">
              {eventTypes.map((et) => (
                <li
                  key={et.id}
                  className="bg-white rounded border border-gray-200 p-3 flex justify-between"
                >
                  <div>
                    <span className="font-medium">{et.name}</span>
                    {et.description && (
                      <span className="text-sm text-gray-600"> — {et.description}</span>
                    )}
                  </div>
                  <span className="text-sm text-gray-500 whitespace-nowrap">
                    {et.durationMinutes} мин
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="mb-8">
          <h2 className="text-xl font-bold mb-3">Календарь встреч</h2>
          <AdminBookingCalendar
            bookings={bookings}
            selectedDate={selectedDate}
            onSelectDate={(date) => setSelectedDate((prev) => (prev === date ? null : date))}
            ownerTimeZone={config.ownerTimeZone}
            horizonDays={config.horizonDays}
          />
        </section>

        <section>
          <div className="flex items-baseline gap-3 mb-3">
            <h2 className="text-xl font-bold">
              {selectedDate ? `Встречи за ${selectedDate}` : 'Предстоящие встречи'}
            </h2>
            {selectedDate && (
              <button
                type="button"
                onClick={() => setSelectedDate(null)}
                className="text-sm text-blue-600 hover:underline"
              >
                Показать все
              </button>
            )}
          </div>
          {selectedDate ? (
            <BookingsList
              bookings={bookings.filter(
                (b) =>
                  formatInTimeZone(new Date(b.startAt), config.ownerTimeZone, 'yyyy-MM-dd') ===
                  selectedDate,
              )}
              ownerTimeZone={config.ownerTimeZone}
              onCancel={handleCancel}
            />
          ) : (
            <BookingsList
              bookings={bookings}
              ownerTimeZone={config.ownerTimeZone}
              onCancel={handleCancel}
            />
          )}
        </section>
      </div>

      {toast && <Toast message={toast} onDismiss={() => setToast(null)} />}
    </div>
  )
}
