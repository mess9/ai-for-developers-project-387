import {http, HttpResponse} from 'msw'
import {addDays, format} from 'date-fns'

const OFFSET = '+03:00'
const dateStr = (i: number) => format(addDays(new Date(), i), 'yyyy-MM-dd')

export const baseConfig = {
  ownerTimeZone: 'Europe/Moscow',
  workingHours: { start: '09:00', end: '18:00' },
  gridMinutes: 15,
  horizonDays: 14,
}

export const introType = {
  id: '11111111-1111-1111-1111-111111111111',
  name: 'Intro call',
  description: 'Знакомство, 30 минут',
  durationMinutes: 30,
}

export const deepType = {
  id: '22222222-2222-2222-2222-222222222222',
  name: 'Deep dive',
  description: null,
  durationMinutes: 60,
}

/** Окно доступности 14 дней; свободные слоты — только в первые два дня. */
function buildAvailability(eventTypeId: string, durationMinutes: number) {
  const days = Array.from({ length: baseConfig.horizonDays }, (_, i) => {
    const date = dateStr(i)
    const hasFreeSlots = i < 2
    return {
      date,
      hasFreeSlots,
      slots: hasFreeSlots
        ? [
            { startAt: `${date}T09:00:00${OFFSET}`, endAt: `${date}T09:30:00${OFFSET}` },
            { startAt: `${date}T09:30:00${OFFSET}`, endAt: `${date}T10:00:00${OFFSET}` },
          ]
        : [],
    }
  })
  return { eventTypeId, durationMinutes, ownerTimeZone: baseConfig.ownerTimeZone, days }
}

const ADMIN = { Authorization: `Bearer test-token` }

export const sampleBookings = [
  {
    id: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    eventTypeId: introType.id,
    eventTypeName: introType.name,
    startAt: `${dateStr(0)}T09:00:00${OFFSET}`,
    endAt: `${dateStr(0)}T09:30:00${OFFSET}`,
    name: 'Иван Гость',
    meetingLink: 'https://meet.example.com/intro',
    description: 'Обсудить проект',
    createdAt: new Date().toISOString(),
  },
  {
    id: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    eventTypeId: deepType.id,
    eventTypeName: deepType.name,
    startAt: `${dateStr(2)}T10:00:00${OFFSET}`,
    endAt: `${dateStr(2)}T11:00:00${OFFSET}`,
    name: 'Пётр Клиент',
    meetingLink: 'https://meet.example.com/deep',
    description: null,
    createdAt: new Date().toISOString(),
  },
  {
    id: 'cccccccc-cccc-cccc-cccc-cccccccccccc',
    eventTypeId: introType.id,
    eventTypeName: introType.name,
    startAt: `${dateStr(4)}T14:00:00${OFFSET}`,
    endAt: `${dateStr(4)}T14:30:00${OFFSET}`,
    name: 'Мария Заказчик',
    meetingLink: 'https://meet.example.com/intro2',
    description: 'Пилотный проект',
    createdAt: new Date().toISOString(),
  },
]

export const sampleBooking = sampleBookings[0]

const bookingsMap = new Map<string, unknown>()

export const handlers = [
  http.get('/api/v1/config', () => HttpResponse.json(baseConfig)),

  http.get('/api/v1/event-types', () => HttpResponse.json([introType, deepType])),

  http.get('/api/v1/event-types/:id/availability', ({ params }) => {
    const id = params.id as string
    const type = [introType, deepType].find((t) => t.id === id)
    if (!type) {
      return HttpResponse.json(
        { status: 404, errorCode: 'EVENT_TYPE_NOT_FOUND' },
        { status: 404, headers: { 'Content-Type': 'application/problem+json' } },
      )
    }
    return HttpResponse.json(buildAvailability(type.id, type.durationMinutes))
  }),

  http.post('/api/v1/bookings', async ({ request }) => {
    const body = (await request.json()) as { eventTypeId: string; startAt: string; name: string; meetingLink: string; description?: string }
    const type = [introType, deepType].find((t) => t.id === body.eventTypeId)
    const bookingKey = `${body.eventTypeId}::${body.startAt}::${body.name}::${body.meetingLink}`
    const existing = bookingsMap.get(bookingKey)
    if (existing) {
      return HttpResponse.json(existing, { status: 201 })
    }
    const confirmation = {
      eventTypeName: type?.name ?? 'Intro call',
      startAt: body.startAt,
      endAt: body.startAt,
      name: body.name,
      meetingLink: body.meetingLink,
      description: body.description ?? null,
      createdAt: new Date().toISOString(),
    }
    bookingsMap.set(bookingKey, confirmation)
    return HttpResponse.json(confirmation, { status: 201 })
  }),

  // ── Admin ──
  http.get('/api/v1/admin/event-types', ({ request }) => {
    if (request.headers.get('Authorization') !== ADMIN.Authorization) {
      return HttpResponse.json({ status: 401, errorCode: 'UNAUTHORIZED' }, { status: 401 })
    }
    return HttpResponse.json([introType, deepType])
  }),

  http.post('/api/v1/admin/event-types', async ({ request }) => {
    if (request.headers.get('Authorization') !== ADMIN.Authorization) {
      return HttpResponse.json({ status: 401, errorCode: 'UNAUTHORIZED' }, { status: 401 })
    }
    const body = (await request.json()) as { name: string; description?: string; durationMinutes: number }
    return HttpResponse.json(
      { id: '33333333-3333-3333-3333-333333333333', ...body, description: body.description ?? null },
      { status: 201 },
    )
  }),

  http.get('/api/v1/admin/bookings', ({ request }) => {
    if (request.headers.get('Authorization') !== ADMIN.Authorization) {
      return HttpResponse.json({ status: 401, errorCode: 'UNAUTHORIZED' }, { status: 401 })
    }
    return HttpResponse.json(sampleBookings)
  }),

  http.delete('/api/v1/admin/bookings/:id', ({ request }) => {
    if (request.headers.get('Authorization') !== ADMIN.Authorization) {
      return HttpResponse.json({ status: 401, errorCode: 'UNAUTHORIZED' }, { status: 401 })
    }
    return new HttpResponse(null, { status: 204 })
  }),
]
