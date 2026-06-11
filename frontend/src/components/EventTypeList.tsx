import type {components} from '../api/types'

type EventType = components['schemas']['EventType']

interface EventTypeListProps {
  eventTypes: EventType[]
  onSelect: (eventType: EventType) => void
}

export function EventTypeList({ eventTypes, onSelect }: EventTypeListProps) {
  if (eventTypes.length === 0) {
    return <p className="text-gray-500">Пока нет доступных типов встреч.</p>
  }

  return (
    <ul className="grid gap-3 sm:grid-cols-2">
      {eventTypes.map((et) => (
        <li key={et.id}>
          <button
            type="button"
            onClick={() => onSelect(et)}
            className="w-full text-left bg-white rounded-lg shadow border border-gray-200 p-4 hover:border-blue-400 hover:shadow-md transition"
          >
            <div className="flex items-center justify-between gap-2">
              <span className="font-semibold">{et.name}</span>
              <span className="text-sm text-gray-500 whitespace-nowrap">
                {et.durationMinutes} мин
              </span>
            </div>
            {et.description && (
              <p className="mt-1 text-sm text-gray-600">{et.description}</p>
            )}
          </button>
        </li>
      ))}
    </ul>
  )
}
