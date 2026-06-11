import {eachDayOfInterval, eachMonthOfInterval, endOfMonth, format, getDay, isSameMonth, startOfMonth,} from 'date-fns'
import {ru} from 'date-fns/locale'
import type {components} from '../api/types'
import {parsePlainDate} from '../lib/time'

type AvailabilityDay = components['schemas']['AvailabilityDay']

interface AvailabilityCalendarProps {
  days: AvailabilityDay[]
  selectedDate: string | null
  onSelectDate: (date: string) => void
}

const WEEKDAYS = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс']

/**
 * Статичная подача фиксированного окна доступности (14 дней) на месячных
 * сетках. Если окно пересекает границу месяцев — рисуем две сетки. Навигации
 * по месяцам нет. Дни вне окна и без свободных слотов — погашены и неактивны.
 */
export function AvailabilityCalendar({
  days,
  selectedDate,
  onSelectDate,
}: AvailabilityCalendarProps) {
  if (days.length === 0) {
    return <p className="text-gray-500">Нет доступных дней.</p>
  }

  const daysByDate = new Map(days.map((d) => [d.date, d]))
  const windowStart = parsePlainDate(days[0].date)
  const windowEnd = parsePlainDate(days[days.length - 1].date)

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
          daysByDate={daysByDate}
          selectedDate={selectedDate}
          onSelectDate={onSelectDate}
        />
      ))}
    </div>
  )
}

interface MonthGridProps {
  monthDate: Date
  daysByDate: Map<string, AvailabilityDay>
  selectedDate: string | null
  onSelectDate: (date: string) => void
}

function MonthGrid({ monthDate, daysByDate, selectedDate, onSelectDate }: MonthGridProps) {
  const monthStart = startOfMonth(monthDate)
  const monthDays = eachDayOfInterval({ start: monthStart, end: endOfMonth(monthDate) })
  const startOffset = (getDay(monthStart) + 6) % 7 // Пн=0 … Вс=6

  return (
    <div className="flex-1">
      <h3 className="text-lg font-semibold mb-3 text-center capitalize">
        {format(monthDate, 'LLLL yyyy', { locale: ru })}
      </h3>
      <div className="grid grid-cols-7 gap-1">
        {WEEKDAYS.map((wd) => (
          <div key={wd} className="text-center text-xs font-medium text-gray-500 py-1">
            {wd}
          </div>
        ))}

        {Array.from({ length: startOffset }).map((_, i) => (
          <div key={`offset-${i}`} />
        ))}

        {monthDays.map((date) => {
          const dateStr = format(date, 'yyyy-MM-dd')
          const dayData = daysByDate.get(dateStr)
          const inWindow = isSameMonth(date, monthDate) && !!dayData
          const clickable = inWindow && dayData!.hasFreeSlots
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
                    ? 'border-green-300 bg-green-50 hover:bg-green-100 cursor-pointer'
                    : 'border-gray-100 text-gray-300 cursor-not-allowed',
                inWindow && !clickable ? 'bg-gray-50' : '',
              ].join(' ')}
            >
              {format(date, 'd')}
              {clickable && !isSelected && (
                <span className="absolute bottom-1 left-1/2 -translate-x-1/2 w-1.5 h-1.5 bg-green-500 rounded-full" />
              )}
            </button>
          )
        })}
      </div>
    </div>
  )
}
