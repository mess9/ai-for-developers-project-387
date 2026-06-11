import type {components} from '../api/types'

export type ProblemDetail = components['schemas']['ProblemDetail']
type ErrorCode = components['schemas']['ErrorCode']

/** Человекочитаемые сообщения по машинному коду ошибки. */
const MESSAGES: Record<ErrorCode, string> = {
  VALIDATION_FAILED: 'Проверьте правильность заполнения полей.',
  MALFORMED_REQUEST: 'Некорректный запрос.',
  EVENT_TYPE_NOT_FOUND: 'Тип события не найден.',
  BOOKING_NOT_FOUND: 'Бронь не найдена.',
  SLOT_ALREADY_BOOKED: 'Это время только что заняли.',
  SLOT_IN_PAST: 'Время уже в прошлом — доступность обновлена.',
  SLOT_OUT_OF_HORIZON: 'Время за пределами окна записи — доступность обновлена.',
  SLOT_NOT_ON_GRID: 'Это время недоступно — доступность обновлена.',
  UNAUTHORIZED: 'Требуется авторизация владельца.',
}

/** Сообщение для пользователя по ProblemDetail (или общий текст). */
export function problemMessage(problem: ProblemDetail | undefined): string {
  if (!problem) return 'Произошла ошибка. Попробуйте ещё раз.'
  return MESSAGES[problem.errorCode] ?? problem.detail ?? 'Произошла ошибка.'
}

/** Разложить `errors[]` валидации в карту «поле → сообщение». */
export function fieldErrors(problem: ProblemDetail | undefined): Record<string, string> {
  const result: Record<string, string> = {}
  for (const e of problem?.errors ?? []) {
    if (!result[e.field]) result[e.field] = e.message
  }
  return result
}

/**
 * Ошибка бронирования, прокидываемая из страницы в форму: несёт общий текст
 * и (для VALIDATION_FAILED) ошибки по полям.
 */
export class BookingError extends Error {
  fields: Record<string, string>
  constructor(message: string, fields: Record<string, string> = {}) {
    super(message)
    this.name = 'BookingError'
    this.fields = fields
  }
}
