import type {components} from '../api/types'
import {browserTimeZone, formatTime} from '../lib/time'

type AvailableSlot = components['schemas']['AvailableSlot']

interface SlotListProps {
  slots: AvailableSlot[]
  ownerTimeZone: string
  onSelect: (slot: AvailableSlot) => void
}

export function SlotList({ slots, ownerTimeZone, onSelect }: SlotListProps) {
  if (slots.length === 0) {
    return <p className="text-gray-500">На этот день нет свободных слотов.</p>
  }

  const showLocal = browserTimeZone !== ownerTimeZone

  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
      {slots.map((slot) => (
        <button
          key={slot.startAt}
          type="button"
          onClick={() => onSelect(slot)}
          className="p-2 rounded border border-green-300 bg-green-50 hover:bg-green-100 text-center transition"
        >
          <div className="font-medium">{formatTime(slot.startAt, ownerTimeZone)}</div>
          {showLocal && (
            <div className="text-xs text-gray-500">
              у вас это {formatTime(slot.startAt, browserTimeZone)}
            </div>
          )}
        </button>
      ))}
    </div>
  )
}
