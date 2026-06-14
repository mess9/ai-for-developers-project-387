import {addDays, eachDayOfInterval, eachMonthOfInterval, endOfMonth, format, getDay, startOfMonth} from 'date-fns'
import {ru} from 'date-fns/locale'
import {formatInTimeZone} from 'date-fns-tz'
import type {components} from '../api/types'
import {parsePlainDate} from '../lib/time'

type Booking = components['schemas']['Booking']

interface AdminBookingCalendarProps {
  bookings: Booking[]
  selectedDate: string | null
  onSelectDate: (date: string) => void
  ownerTimeZone: string
  horizonDays: number
}

const WEEKDAYS = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс']

export function AdminBookingCalendar({
  bookings,
  selectedDate,
  onSelectDate,
  ownerTimeZone,
  horizonDays,
}: AdminBookingCalendarProps) {
  const bookingsByDate = new Map<string, Booking[]>()
  for (const b of bookings) {
    const date = formatInTimeZone(new Date(b.startAt), ownerTimeZone, 'yyyy-MM-dd')
    const arr = bookingsByDate.get(date) || []
    arr.push(b)
    bookingsByDate.set(date, arr)
  }

  const todayStr = formatInTimeZone(new Date(), ownerTimeZone, 'yyyy-MM-dd')
  const windowStart = parsePlainDate(todayStr)
  const windowEnd = addDays(windowStart, horizonDays - 1)
  const windowDates = new Set(
    Array.from({length: horizonDays}, (_, i) => format(addDays(windowStart, i), 'yyyy-MM-dd')),
  )

  const months = eachMonthOfInterval({
    start: startOfMonth(windowStart),
    end: startOfMonth(windowEnd),
  })

  return (
    <div className="flex flex-col md:flex-row gap-6 mb-6">
      {months.map((monthDate) => (
        <MonthGrid
          key={format(monthDate, 'yyyy-MM')}
          monthDate={monthDate}
          bookingsByDate={bookingsByDate}
          windowDates={windowDates}
          selectedDate={selectedDate}
          onSelectDate={onSelectDate}
        />
      ))}
    </div>
  )
}

interface MonthGridProps {
  monthDate: Date
  bookingsByDate: Map<string, Booking[]>
  windowDates: Set<string>
  selectedDate: string | null
  onSelectDate: (date: string) => void
}

function MonthGrid({monthDate, bookingsByDate, windowDates, selectedDate, onSelectDate}: MonthGridProps) {
  const monthStart = startOfMonth(monthDate)
  const monthDays = eachDayOfInterval({start: monthStart, end: endOfMonth(monthDate)})
  const startOffset = (getDay(monthStart) + 6) % 7

  return (
    <div className="flex-1">
      <h3 className="text-lg font-semibold mb-3 text-center capitalize">
        {format(monthDate, 'LLLL yyyy', {locale: ru})}
      </h3>
      <div className="grid grid-cols-7 gap-1">
        {WEEKDAYS.map((wd) => (
          <div key={wd} className="text-center text-xs font-medium text-gray-500 py-1">
            {wd}
          </div>
        ))}

        {Array.from({length: startOffset}).map((_, i) => (
          <div key={`offset-${i}`} />
        ))}

        {monthDays.map((date) => {
          const dateStr = format(date, 'yyyy-MM-dd')
          const inWindow = windowDates.has(dateStr)
          const dayBookings = bookingsByDate.get(dateStr)
          const count = dayBookings?.length ?? 0
          const clickable = inWindow && count > 0
          const isSelected = selectedDate === dateStr

          return (
            <button
              key={dateStr}
              type="button"
              onClick={() => clickable && onSelectDate(dateStr)}
              disabled={!clickable}
              aria-pressed={isSelected}
              className={[
                'h-12 rounded border text-sm transition relative',
                isSelected
                  ? 'border-blue-600 bg-blue-100 font-semibold'
                  : clickable
                    ? 'border-amber-300 bg-amber-50 hover:bg-amber-100 cursor-pointer'
                    : inWindow
                      ? 'border-gray-200 bg-gray-50 text-gray-400 cursor-default'
                      : 'border-gray-100 text-gray-300 cursor-not-allowed',
              ].join(' ')}
            >
              {format(date, 'd')}
              {clickable && !isSelected && (
                <span className="absolute bottom-1 left-1/2 -translate-x-1/2 min-w-[1.25rem] h-5 bg-amber-500 rounded-full text-white text-[10px] leading-5 text-center px-1">
                  {count}
                </span>
              )}
            </button>
          )
        })}
      </div>
    </div>
  )
}
