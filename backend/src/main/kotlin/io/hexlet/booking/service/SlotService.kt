package io.hexlet.booking.service

import io.hexlet.booking.config.BookingProperties
import io.hexlet.booking.db.Tables.BOOKINGS
import io.hexlet.booking.db.Tables.SLOTS
import io.hexlet.booking.db.tables.records.BookingsRecord
import io.hexlet.booking.db.tables.records.SlotsRecord
import io.hexlet.booking.model.SlotStatus
import jakarta.annotation.PostConstruct
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZonedDateTime

@Service
class SlotService(
    private val dsl: DSLContext,
    private val props: BookingProperties,
) {
    private val log = LoggerFactory.getLogger(SlotService::class.java)

    @PostConstruct
    fun initSlots() = generateSlots()

    @Scheduled(cron = "0 1 0 * * *")   // каждый день в 00:01 в локальном времени JVM
    fun scheduledGenerate() = generateSlots()

    /**
     * Идемпотентно создаёт слоты на горизонт [сегодня; сегодня + horizonDays].
     * ON CONFLICT DO NOTHING защищает от дублей.
     */
    fun generateSlots() {
        val today = LocalDate.now(props.zone)
        val duration = java.time.Duration.ofMinutes(props.slotMinutes.toLong())
        var created = 0

        for (offset in 0..props.horizonDays) {
            val date = today.plusDays(offset.toLong())
            val dayStart = ZonedDateTime.of(date, props.workStart, props.zone).toOffsetDateTime()
            val dayEnd   = ZonedDateTime.of(date, props.workEnd,   props.zone).toOffsetDateTime()

            var cur = dayStart
            while (!cur.plus(duration).isAfter(dayEnd)) {
                val end = cur.plus(duration)
                val n = dsl.insertInto(SLOTS, SLOTS.START_AT, SLOTS.END_AT)
                    .values(cur, end)
                    .onConflictDoNothing()
                    .execute()
                created += n
                cur = end
            }
        }
        log.info("Slot generation complete: {} new slots", created)
    }

    fun findByStartAt(startAt: OffsetDateTime): SlotsRecord? =
        dsl.selectFrom(SLOTS).where(SLOTS.START_AT.eq(startAt)).fetchOne()

    fun computeStatus(slot: SlotsRecord, booking: BookingsRecord?, now: OffsetDateTime): SlotStatus =
        when {
            slot.startAt.isBefore(now) -> SlotStatus.PAST
            booking != null            -> SlotStatus.BOOKED
            else                       -> SlotStatus.FREE
        }

    /**
     * Возвращает слоты запрошенного месяца, сгруппированные по дням в часовом поясе владельца.
     * Каждый элемент: (localDate, список пар (slot, booking?)).
     */
    fun getSlotsForMonth(month: String): Map<LocalDate, List<Pair<SlotsRecord, BookingsRecord?>>> {
        val ym = java.time.YearMonth.parse(month)
        val rangeStart = ym.atDay(1).atStartOfDay(props.zone).toOffsetDateTime()
        val rangeEnd   = ym.plusMonths(1).atDay(1).atStartOfDay(props.zone).toOffsetDateTime()

        return dsl
            .select(SLOTS.asterisk(), BOOKINGS.asterisk())
            .from(SLOTS)
            .leftJoin(BOOKINGS).on(BOOKINGS.SLOT_ID.eq(SLOTS.ID))
            .where(SLOTS.START_AT.ge(rangeStart).and(SLOTS.START_AT.lt(rangeEnd)))
            .orderBy(SLOTS.START_AT)
            .fetch { r ->
                val slot    = r.into(SLOTS)
                val booking = if (r.get(BOOKINGS.ID) != null) r.into(BOOKINGS) else null
                slot to booking
            }
            .groupBy { (slot, _) ->
                slot.startAt.atZoneSameInstant(props.zone).toLocalDate()
            }
            .toSortedMap()
    }

    /** Бронь для конкретного слота (или null). */
    fun bookingForSlot(slotId: Long): BookingsRecord? =
        dsl.selectFrom(BOOKINGS).where(BOOKINGS.SLOT_ID.eq(slotId)).fetchOne()
}
