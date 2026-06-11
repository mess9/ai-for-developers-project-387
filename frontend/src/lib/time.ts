import {formatInTimeZone} from 'date-fns-tz'
import {parseISO} from 'date-fns'

/** Часовой пояс браузера (IANA-имя). */
export const browserTimeZone = Intl.DateTimeFormat().resolvedOptions().timeZone

/** Время `HH:mm` указанного момента в заданном часовом поясе. */
export function formatTime(iso: string, timeZone: string): string {
  return formatInTimeZone(new Date(iso), timeZone, 'HH:mm')
}

/** Дата+время одного момента в заданном поясе (для подтверждений/списков). */
export function formatDateTime(iso: string, timeZone: string): string {
  return formatInTimeZone(new Date(iso), timeZone, 'dd.MM.yyyy HH:mm')
}

/** Локальная дата (`plainDate`, без зоны) в объект Date по местному календарю. */
export function parsePlainDate(date: string): Date {
  return parseISO(`${date}T00:00:00`)
}
