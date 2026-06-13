import { randomUUID } from 'node:crypto'
import pg from 'pg'
import { DB_CONFIG } from './env.js'

const { Pool } = pg

let pool: pg.Pool | null = null

function getPool(): pg.Pool {
  if (!pool) pool = new Pool(DB_CONFIG)
  return pool
}

export async function closePool(): Promise<void> {
  if (pool) {
    await pool.end()
    pool = null
  }
}

// ── Сброс ────────────────────────────────────────────────────────────────

/**
 * Чистит данные перед сценарием. Токен владельца (`app_settings`) намеренно
 * не трогаем — он генерируется один раз на старте приложения.
 */
export async function truncateData(): Promise<void> {
  await getPool().query('TRUNCATE bookings, event_types RESTART IDENTITY CASCADE')
}

// ── Посев (preconditions) ──────────────────────────────────────────────────

export interface SeededEventType {
  id: string
  name: string
  description: string | null
  durationMinutes: number
}

export async function seedEventType(input: {
  name: string
  description?: string | null
  durationMinutes: number
}): Promise<SeededEventType> {
  const id = randomUUID()
  const description = input.description ?? null
  await getPool().query(
    `INSERT INTO event_types (id, name, description, duration_minutes)
     VALUES ($1, $2, $3, $4)`,
    [id, input.name, description, input.durationMinutes],
  )
  return { id, name: input.name, description, durationMinutes: input.durationMinutes }
}

export async function seedBooking(input: {
  eventTypeId: string
  startAt: string
  endAt: string
  name: string
  meetingLink: string
  description?: string | null
}): Promise<string> {
  const id = randomUUID()
  await getPool().query(
    `INSERT INTO bookings (id, event_type_id, start_at, end_at, name, meeting_link, description)
     VALUES ($1, $2, $3, $4, $5, $6, $7)`,
    [
      id,
      input.eventTypeId,
      input.startAt,
      input.endAt,
      input.name,
      input.meetingLink,
      input.description ?? null,
    ],
  )
  return id
}

// ── Чтение (проверки) ───────────────────────────────────────────────────────

export interface BookingRow {
  id: string
  eventTypeId: string
  startAt: Date
  endAt: Date
  name: string
  meetingLink: string
  description: string | null
}

export async function readBookings(): Promise<BookingRow[]> {
  const { rows } = await getPool().query(
    `SELECT id, event_type_id, start_at, end_at, name, meeting_link, description
     FROM bookings ORDER BY start_at`,
  )
  return rows.map((r) => ({
    id: r.id,
    eventTypeId: r.event_type_id,
    startAt: r.start_at,
    endAt: r.end_at,
    name: r.name,
    meetingLink: r.meeting_link,
    description: r.description,
  }))
}

export async function readEventTypes(): Promise<SeededEventType[]> {
  const { rows } = await getPool().query(
    `SELECT id, name, description, duration_minutes FROM event_types ORDER BY created_at`,
  )
  return rows.map((r) => ({
    id: r.id,
    name: r.name,
    description: r.description,
    durationMinutes: r.duration_minutes,
  }))
}

export async function getOwnerToken(): Promise<string> {
  const { rows } = await getPool().query('SELECT token FROM app_settings WHERE id = 1')
  if (rows.length === 0) throw new Error('Токен владельца не найден в app_settings')
  return rows[0].token
}
