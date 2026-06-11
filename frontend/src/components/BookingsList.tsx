import type {components} from '../api/types'
import {browserTimeZone, formatDateTime, formatTime} from '../lib/time'

type Booking = components['schemas']['Booking']

interface BookingsListProps {
  bookings: Booking[]
  ownerTimeZone: string
  onCancel: (id: string) => void
}

export function BookingsList({ bookings, ownerTimeZone, onCancel }: BookingsListProps) {
  if (bookings.length === 0) {
    return <p className="text-gray-500">Предстоящих встреч нет.</p>
  }

  const showLocal = browserTimeZone !== ownerTimeZone

  return (
    <ul className="space-y-3">
      {bookings.map((b) => (
        <li
          key={b.id}
          className="bg-white rounded-lg shadow border border-gray-200 p-4 flex justify-between gap-4"
        >
          <div className="min-w-0">
            <div className="font-semibold">{b.name}</div>
            <div className="text-sm text-gray-600">{b.eventTypeName}</div>
            <div className="text-sm">
              {formatDateTime(b.startAt, ownerTimeZone)}
              {showLocal && (
                <span className="text-gray-500"> · у вас {formatTime(b.startAt, browserTimeZone)}</span>
              )}
            </div>
            <a
              href={b.meetingLink}
              target="_blank"
              rel="noreferrer"
              className="text-sm text-blue-600 hover:underline break-all"
            >
              {b.meetingLink}
            </a>
            {b.description && <p className="text-sm text-gray-600 mt-1">{b.description}</p>}
          </div>
          <button
            type="button"
            onClick={() => onCancel(b.id)}
            className="self-start px-3 py-1.5 border border-red-300 text-red-700 rounded hover:bg-red-50 whitespace-nowrap"
          >
            Отменить
          </button>
        </li>
      ))}
    </ul>
  )
}
