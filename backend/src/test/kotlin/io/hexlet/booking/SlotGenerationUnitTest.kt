package io.hexlet.booking

import io.hexlet.booking.config.BookingProperties
import io.hexlet.booking.db.Tables.SLOTS
import io.hexlet.booking.service.SlotService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Проверяет алгоритм генерации слотов: количество, границы рабочего дня,
 * корректность для часового пояса владельца.
 */
class SlotGenerationUnitTest : AbstractIntegrationTest() {

    @Autowired lateinit var props: BookingProperties
    @Autowired lateinit var slotService: SlotService

    @BeforeEach
    fun resetSlots() {
        dsl.deleteFrom(SLOTS).execute()
        slotService.generateSlots()
    }

    @Test
    fun `slots per day match working hours and slot duration`() {
        val minutesInWorkday = (props.workEnd.toSecondOfDay() - props.workStart.toSecondOfDay()) / 60
        val slotsPerDay = minutesInWorkday / props.slotMinutes  // 9*60/30 = 18 при 09-18

        // Берём конкретный рабочий день из горизонта (завтра гарантированно в горизонте)
        val tomorrow = LocalDate.now(props.zone).plusDays(1)
        val dayStart = ZonedDateTime.of(tomorrow, props.workStart, props.zone).toOffsetDateTime()
        val dayEnd   = ZonedDateTime.of(tomorrow, props.workEnd,   props.zone).toOffsetDateTime()

        val slots = dsl.selectFrom(SLOTS)
            .where(SLOTS.START_AT.ge(dayStart).and(SLOTS.END_AT.le(dayEnd)))
            .fetch()

        assertThat(slots).hasSize(slotsPerDay)
    }

    @Test
    fun `first slot of day starts exactly at working hours start`() {
        val tomorrow = LocalDate.now(props.zone).plusDays(1)
        val dayStart = ZonedDateTime.of(tomorrow, props.workStart, props.zone).toOffsetDateTime()

        val firstSlot = dsl.selectFrom(SLOTS)
            .where(SLOTS.START_AT.eq(dayStart))
            .fetchOne()

        assertThat(firstSlot).isNotNull
        assertThat(firstSlot!!.endAt).isEqualTo(dayStart.plusMinutes(props.slotMinutes.toLong()))
    }

    @Test
    fun `last slot of day ends at working hours end`() {
        val tomorrow = LocalDate.now(props.zone).plusDays(1)
        val dayEnd   = ZonedDateTime.of(tomorrow, props.workEnd, props.zone).toOffsetDateTime()

        val lastSlot = dsl.selectFrom(SLOTS)
            .where(SLOTS.END_AT.eq(dayEnd))
            .fetchOne()

        assertThat(lastSlot).isNotNull
    }

    @Test
    fun `no slots generated beyond horizon`() {
        val maxSlot = dsl.selectFrom(SLOTS)
            .orderBy(SLOTS.START_AT.desc())
            .limit(1)
            .fetchOne()

        assertThat(maxSlot).isNotNull()

        val lastDay = LocalDate.now(props.zone).plusDays(props.horizonDays.toLong())
        val lastDayEnd = ZonedDateTime.of(lastDay, props.workEnd, props.zone).toOffsetDateTime()

        assertThat(maxSlot!!.startAt).isBeforeOrEqualTo(lastDayEnd)
    }

    @Test
    fun `generateSlots is idempotent`() {
        val before = dsl.fetchCount(SLOTS)
        slotService.generateSlots()
        val after = dsl.fetchCount(SLOTS)
        assertThat(after).isEqualTo(before)
    }
}
