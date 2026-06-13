import {useState} from 'react'
import type {components} from '../api/types'
import {browserTimeZone, formatDateTime, formatTime} from '../lib/time'
import {BookingError} from '../lib/errors'

type EventType = components['schemas']['EventType']
type AvailableSlot = components['schemas']['AvailableSlot']

export interface BookingFormData {
  name: string
  meetingLink: string
  description?: string
}

interface BookingFormProps {
  eventType: EventType
  slot: AvailableSlot
  ownerTimeZone: string
  onSubmit: (data: BookingFormData) => Promise<void>
  onClose: () => void
}

function isValidUrl(value: string): boolean {
  try {
    new URL(value)
    return true
  } catch {
    return false
  }
}

export function BookingForm({ eventType, slot, ownerTimeZone, onSubmit, onClose }: BookingFormProps) {
  const [name, setName] = useState('')
  const [meetingLink, setMeetingLink] = useState('')
  const [description, setDescription] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const validate = (): Record<string, string> => {
    const errs: Record<string, string> = {}
    if (!name.trim()) errs.name = 'Укажите имя'
    if (!meetingLink.trim()) errs.meetingLink = 'Укажите ссылку на встречу'
    else if (!isValidUrl(meetingLink.trim())) errs.meetingLink = 'Введите корректную ссылку (URL)'
    return errs
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    const errs = validate()
    setFieldErrors(errs)
    setError(null)
    if (Object.keys(errs).length > 0) return

    setSubmitting(true)
    try {
      await onSubmit({
        name: name.trim(),
        meetingLink: meetingLink.trim(),
        description: description.trim() || undefined,
      })
    } catch (err) {
      if (err instanceof BookingError) {
        setError(err.message)
        if (Object.keys(err.fields).length > 0) setFieldErrors(err.fields)
      } else {
        setError(err instanceof Error ? err.message : 'Не удалось забронировать слот')
      }
      setSubmitting(false)
    }
  }

  const showLocal = browserTimeZone !== ownerTimeZone

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-40">
      <div className="bg-white rounded-lg p-6 max-w-md w-full">
        <h2 className="text-xl font-bold mb-1">Запись: {eventType.name}</h2>
        <p className="mb-1 text-gray-700">
          {formatDateTime(slot.startAt, ownerTimeZone)} ({eventType.durationMinutes} мин)
        </p>
        {showLocal && (
          <p className="mb-4 text-sm text-gray-500">
            у вас это {formatTime(slot.startAt, browserTimeZone)}
          </p>
        )}

        <form onSubmit={handleSubmit} noValidate>
          <div className="mb-3">
            <label htmlFor="booking-name" className="block text-sm font-medium mb-1">
              Имя <span className="text-red-500">*</span>
            </label>
            <input
              id="booking-name"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            {fieldErrors.name && <p className="mt-1 text-sm text-red-600">{fieldErrors.name}</p>}
          </div>

          <div className="mb-3">
            <label htmlFor="booking-link" className="block text-sm font-medium mb-1">
              Ссылка на встречу <span className="text-red-500">*</span>
            </label>
            <input
              id="booking-link"
              type="url"
              value={meetingLink}
              onChange={(e) => setMeetingLink(e.target.value)}
              className="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            {fieldErrors.meetingLink && (
              <p className="mt-1 text-sm text-red-600">{fieldErrors.meetingLink}</p>
            )}
          </div>

          <div className="mb-3">
            <label htmlFor="booking-description" className="block text-sm font-medium mb-1">
              Описание
            </label>
            <textarea
              id="booking-description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={3}
              className="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            {fieldErrors.description && (
              <p className="mt-1 text-sm text-red-600">{fieldErrors.description}</p>
            )}
          </div>

          {error && (
            <div className="mb-3 p-3 bg-red-100 border border-red-300 text-red-700 rounded">
              {error}
            </div>
          )}

          <div className="flex gap-2 justify-end">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 border border-gray-300 rounded hover:bg-gray-100"
            >
              Отмена
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:bg-gray-400"
            >
              {submitting ? 'Бронирование…' : 'Забронировать'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
