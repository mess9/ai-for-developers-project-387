package io.hexlet.booking.service

import io.hexlet.booking.config.BookingProperties
import io.hexlet.booking.db.Tables.BOOKINGS
import io.hexlet.booking.db.Tables.EVENT_TYPES
import io.hexlet.booking.exception.*
import io.hexlet.booking.model.Booking
import io.hexlet.booking.model.BookingConfirmation
import io.hexlet.booking.model.BookingRequest
import org.jooq.DSLContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.util.*

@Service
class BookingService(
    private val dsl: DSLContext,
    private val eventTypeService: EventTypeService,
    private val props: BookingProperties,
    private val clock: Clock,
) {
    /**
     * Лестница проверок со стопом на первом провале:
     * meetingLink → тип существует → старт на сетке и в рабочих часах →
     * не прошлое → в горизонте → нет пересечения с бронями → INSERT.
     */
    @Transactional
    fun createBooking(request: BookingRequest): BookingConfirmation {
        // 1. meetingLink: format:uri теряется кодгеном, проверяем вручную.
        if (!isValidUri(request.meetingLink)) {
            throw FieldValidationException("meetingLink", "must be a valid URI")
        }

        // 2. Тип существует? Длительность берём из типа.
        val eventType = eventTypeService.require(request.eventTypeId)
        val duration = eventType.durationMinutes.toLong()

        val start = request.startAt
        val end = start.plusMinutes(duration)
        val zone = props.zone

        // 3. Старт на 15-мин границе И интервал [start, start+D] в рабочих часах.
        val localStart = start.atZoneSameInstant(zone)
        val dayStart = ZonedDateTime.of(localStart.toLocalDate(), props.workStart, zone)
        val dayEnd = ZonedDateTime.of(localStart.toLocalDate(), props.workEnd, zone)
        val onGrid = localStart.minute % props.gridMinutes == 0 &&
            localStart.second == 0 && localStart.nano == 0
        val fitsHours = !localStart.isBefore(dayStart) &&
            !localStart.plusMinutes(duration).isAfter(dayEnd)
        if (!onGrid || !fitsHours) {
            throw SlotNotOnGridException()
        }

        // 4. Не прошлое.
        if (start.isBefore(OffsetDateTime.now(clock))) {
            throw SlotInPastException()
        }

        // 5. В горизонте: дата старта в OWNER_TZ ≤ сегодня + horizonDays.
        val today = LocalDate.now(clock.withZone(zone))
        if (localStart.toLocalDate().isAfter(today.plusDays(props.horizonDays.toLong()))) {
            throw SlotOutOfHorizonException()
        }

        // 6a. Идемпотентность: повтор идентичной брони возвращает существующую.
        val existing = dsl.selectFrom(BOOKINGS)
            .where(BOOKINGS.EVENT_TYPE_ID.eq(eventType.id))
            .and(BOOKINGS.START_AT.eq(start))
            .and(BOOKINGS.NAME.eq(request.name))
            .and(BOOKINGS.MEETING_LINK.eq(request.meetingLink))
            .fetchOne()
        if (existing != null) {
            return BookingConfirmation(
                eventTypeName = eventType.name,
                startAt = existing.startAt,
                endAt = existing.endAt,
                name = existing.name,
                meetingLink = existing.meetingLink,
                createdAt = existing.createdAt,
                description = existing.description,
            )
        }

        // 6b. Pre-check пересечения — чистый 409 без отлова исключения.
        val overlaps = dsl.fetchExists(
            dsl.selectFrom(BOOKINGS)
                .where(BOOKINGS.START_AT.lt(end)).and(BOOKINGS.END_AT.gt(start))
        )
        if (overlaps) {
            throw SlotAlreadyBookedException()
        }

        // 7. INSERT. Параллельная вставка ловится EXCLUDE-констрейнтом (23P01) → 409.
        val saved = dsl.insertInto(BOOKINGS)
            .set(BOOKINGS.ID, UUID.randomUUID())
            .set(BOOKINGS.EVENT_TYPE_ID, eventType.id)
            .set(BOOKINGS.START_AT, start)
            .set(BOOKINGS.END_AT, end)
            .set(BOOKINGS.NAME, request.name)
            .set(BOOKINGS.MEETING_LINK, request.meetingLink)
            .set(BOOKINGS.DESCRIPTION, request.description)
            .returning()
            .fetchOne()!!

        return BookingConfirmation(
            eventTypeName = eventType.name,
            startAt = saved.startAt,
            endAt = saved.endAt,
            name = saved.name,
            meetingLink = saved.meetingLink,
            createdAt = saved.createdAt,
            description = saved.description,
        )
    }

    /** Плоский список будущих/текущих встреч всех типов, сортировка по началу. */
    fun listUpcoming(): List<Booking> =
        dsl.select(
            BOOKINGS.ID, BOOKINGS.EVENT_TYPE_ID, EVENT_TYPES.NAME,
            BOOKINGS.START_AT, BOOKINGS.END_AT, BOOKINGS.NAME, BOOKINGS.MEETING_LINK,
            BOOKINGS.DESCRIPTION, BOOKINGS.CREATED_AT,
        )
            .from(BOOKINGS)
            .join(EVENT_TYPES).on(EVENT_TYPES.ID.eq(BOOKINGS.EVENT_TYPE_ID))
            .where(BOOKINGS.END_AT.gt(OffsetDateTime.now(clock)))
            .orderBy(BOOKINGS.START_AT)
            .fetch { r ->
                Booking(
                    id = r.get(BOOKINGS.ID),
                    eventTypeId = r.get(BOOKINGS.EVENT_TYPE_ID),
                    eventTypeName = r.get(EVENT_TYPES.NAME),
                    startAt = r.get(BOOKINGS.START_AT),
                    endAt = r.get(BOOKINGS.END_AT),
                    name = r.get(BOOKINGS.NAME),
                    meetingLink = r.get(BOOKINGS.MEETING_LINK),
                    createdAt = r.get(BOOKINGS.CREATED_AT),
                    description = r.get(BOOKINGS.DESCRIPTION),
                )
            }

    @Transactional
    fun cancelBooking(id: UUID) {
        val deleted = dsl.deleteFrom(BOOKINGS).where(BOOKINGS.ID.eq(id)).execute()
        if (deleted == 0) throw BookingNotFoundException()
    }

    /**
     * Абсолютный URI с хостом. parseServerAuthority() в одиночку пропускает мусор без схемы,
     * поэтому добавляем isAbsolute + host. Схема любая — допускаем deeplink'и.
     */
    private fun isValidUri(link: String): Boolean =
        try {
            val uri = URI(link).parseServerAuthority()
            uri.isAbsolute && uri.host != null
        } catch (_: Exception) {
            false
        }
}
