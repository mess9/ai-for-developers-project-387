/** Время `HH:mm` указанного момента в заданном часовом поясе (как на экране). */
export function hhmmInTz(iso: string, timeZone: string): string {
  return new Intl.DateTimeFormat('ru-RU', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    timeZone,
  }).format(new Date(iso))
}

/** Номер дня месяца (без ведущего нуля) для даты `yyyy-MM-dd` — как подпись в сетке. */
export function dayOfMonth(isoDate: string): string {
  return String(Number(isoDate.slice(8, 10)))
}
