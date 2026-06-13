import {useState} from 'react'
import type {components} from '../api/types'
import {BookingError} from '../lib/errors'

type EventTypeRequest = components['schemas']['EventTypeRequest']

interface EventTypeFormProps {
  onCreate: (data: EventTypeRequest) => Promise<void>
}

/** Длительности кратны 15 (от 15 до 120) — невалидная кратность невозможна. */
const DURATIONS = [15, 30, 45, 60, 75, 90, 105, 120]

export function EventTypeForm({ onCreate }: EventTypeFormProps) {
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [durationMinutes, setDurationMinutes] = useState(30)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setFieldErrors({})
    if (!name.trim()) {
      setFieldErrors({ name: 'Укажите название' })
      return
    }

    setSubmitting(true)
    try {
      await onCreate({
        name: name.trim(),
        description: description.trim() || undefined,
        durationMinutes,
      })
      setName('')
      setDescription('')
      setDurationMinutes(30)
    } catch (err) {
      if (err instanceof BookingError) {
        setError(err.message)
        if (Object.keys(err.fields).length > 0) setFieldErrors(err.fields)
      } else {
        setError(err instanceof Error ? err.message : 'Не удалось создать тип события')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="bg-white rounded-lg shadow p-4 mb-4">
      <h3 className="font-semibold mb-3">Новый тип события</h3>

      <div className="mb-3">
        <label htmlFor="et-name" className="block text-sm font-medium mb-1">
          Название <span className="text-red-500">*</span>
        </label>
        <input
          id="et-name"
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          className="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
        {fieldErrors.name && <p className="mt-1 text-sm text-red-600">{fieldErrors.name}</p>}
      </div>

      <div className="mb-3">
        <label htmlFor="et-description" className="block text-sm font-medium mb-1">
          Описание
        </label>
        <textarea
          id="et-description"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={2}
          className="w-full border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
      </div>

      <div className="mb-3">
        <label htmlFor="et-duration" className="block text-sm font-medium mb-1">
          Длительность
        </label>
        <select
          id="et-duration"
          value={durationMinutes}
          onChange={(e) => setDurationMinutes(Number(e.target.value))}
          className="border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          {DURATIONS.map((d) => (
            <option key={d} value={d}>
              {d} мин
            </option>
          ))}
        </select>
        {fieldErrors.durationMinutes && (
          <p className="mt-1 text-sm text-red-600">{fieldErrors.durationMinutes}</p>
        )}
      </div>

      {error && (
        <div className="mb-3 p-3 bg-red-100 border border-red-300 text-red-700 rounded">
          {error}
        </div>
      )}

      <button
        type="submit"
        disabled={submitting}
        className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:bg-gray-400"
      >
        {submitting ? 'Создание…' : 'Создать'}
      </button>
    </form>
  )
}
