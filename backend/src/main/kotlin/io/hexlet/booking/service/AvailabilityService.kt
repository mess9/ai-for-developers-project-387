package io.hexlet.booking.service

import io.hexlet.booking.config.BookingProperties
import io.hexlet.booking.db.Tables.BOOKINGS
import io.hexlet.booking.model.Availability
import io.hexlet.booking.model.AvailabilityDay
import io.hexlet.booking.model.AvailableSlot
import org.jooq.DSLContext
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.util.*

/** Простой полуинтервал [start, end) для проверки пересечений в памяти. */
private data class Interval(val start: OffsetDateTime, val end: OffsetDateTime)

@Service
class AvailabilityService(
    private val dsl: DSLContext,
    private val eventTypeService: EventTypeService,
    private val props: BookingProperties,
) {
    /**
     * Доступность типа события на окно «сегодня…сегодня+horizonDays» (по датам в OWNER_TZ).
     * Доступность не хранится — вычисляется на лету по event_types + bookings.
     */
    fun forEventType(eventTypeId: UUID): Availability {
        val eventType = eventTypeService.require(eventTypeId)
        val duration = eventType.durationMinutes.toLong()

        val zone = props.zone
        val today = OffsetDateTime.now(zone).toLocalDate()
        val now = OffsetDateTime.now()

        // Границы окна в UTC: от начала сегодняшнего дня до начала дня после последнего.
        val windowStart = today.atStartOfDay(zone).toOffsetDateTime()
        val windowEnd = today.plusDays(props.horizonDays + 1L).atStartOfDay(zone).toOffsetDateTime()

        // Одним запросом — брони, пересекающие окно. Никаких запросов на каждый слот.
        val bookings = dsl.select(BOOKINGS.START_AT, BOOKINGS.END_AT)
            .from(BOOKINGS)
            .where(BOOKINGS.START_AT.lt(windowEnd)).and(BOOKINGS.END_AT.gt(windowStart))
            .orderBy(BOOKINGS.START_AT)
            .fetch { Interval(it.value1(), it.value2()) }

        val days = (0..props.horizonDays).map { offset ->
            val date = today.plusDays(offset.toLong())
            val dayStart = ZonedDateTime.of(date, props.workStart, zone).toOffsetDateTime()
            val dayEnd = ZonedDateTime.of(date, props.workEnd, zone).toOffsetDateTime()

            val slots = mutableListOf<AvailableSlot>()
            var start = dayStart
            while (!start.plusMinutes(duration).isAfter(dayEnd)) {
                val end = start.plusMinutes(duration)
                val isPast = start.isBefore(now)
                val overlaps = bookings.any { it.start.isBefore(end) && it.end.isAfter(start) }
                if (!isPast && !overlaps) {
                    slots.add(AvailableSlot(startAt = start, endAt = end))
                }
                start = start.plusMinutes(props.gridMinutes.toLong())
            }
            AvailabilityDay(date = date, hasFreeSlots = slots.isNotEmpty(), slots = slots)
        }

        return Availability(
            eventTypeId = eventTypeId,
            durationMinutes = eventType.durationMinutes,
            ownerTimeZone = props.ownerTz,
            days = days,
        )
    }
}
