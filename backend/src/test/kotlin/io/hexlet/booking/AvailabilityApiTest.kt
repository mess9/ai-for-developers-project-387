package io.hexlet.booking

import io.hexlet.booking.db.Tables.BOOKINGS
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.*

class AvailabilityApiTest : AbstractIntegrationTest() {

    private fun insertBooking(eventTypeId: UUID, start: OffsetDateTime, durationMinutes: Long) {
        dsl.insertInto(BOOKINGS)
            .set(BOOKINGS.ID, UUID.randomUUID())
            .set(BOOKINGS.EVENT_TYPE_ID, eventTypeId)
            .set(BOOKINGS.START_AT, start)
            .set(BOOKINGS.END_AT, start.plusMinutes(durationMinutes))
            .set(BOOKINGS.NAME, "Гость")
            .set(BOOKINGS.MEETING_LINK, "https://meet.example.com/x")
            .set(BOOKINGS.CREATED_AT, OffsetDateTime.now(clock))
            .execute()
    }

    @Test
    fun `GET availability 404 for unknown event type`() {
        val response = get("/event-types/${UUID.randomUUID()}/availability")

        assertThat(response.status).isEqualTo(404)
        assertThat(response.extractPath("errorCode")).isEqualTo("EVENT_TYPE_NOT_FOUND")
    }

    @Test
    fun `GET availability 400 for malformed uuid`() {
        val response = get("/event-types/not-a-uuid/availability")

        assertThat(response.status).isEqualTo(400)
        assertThat(response.extractPath("errorCode")).isEqualTo("MALFORMED_REQUEST")
    }

    @Test
    fun `GET availability returns all days of window`() {
        val id = createEventType(durationMinutes = 30)

        val response = get("/event-types/$id/availability")

        assertThat(response.status).isEqualTo(200)
        assertThat(response.extractPath("eventTypeId")).isEqualTo(id.toString())
        assertThat(response.extractPath("durationMinutes")?.toInt()).isEqualTo(30)
        // окно — horizonDays + 1 календарных дней (включая сегодня)
        assertThat(response.extractList("days").size).isEqualTo(props.horizonDays + 1)
    }

    @Test
    fun `GET availability has free slots tomorrow`() {
        val id = createEventType(durationMinutes = 30)

        val response = get("/event-types/$id/availability")

        val days = response.extractList("days")
        // завтра гарантированно полностью в будущем → есть свободные слоты
        val tomorrow = days.find {
            it.get("date").asText() == java.time.LocalDate.now(clock.withZone(props.zone)).plusDays(1).toString()
        }
        assertThat(tomorrow).isNotNull
        assertThat(tomorrow!!.get("hasFreeSlots").asBoolean()).isTrue()
        assertThat(tomorrow.get("slots").size()).isGreaterThan(0)
    }

    @Test
    fun `GET availability excludes slots overlapping a booking of another type`() {
        val typeA = createEventType(durationMinutes = 30, name = "A")
        val typeB = createEventType(durationMinutes = 60, name = "B")
        val start = slotStart(daysAhead = 1, slotIndex = 0)
        // бронь типа B блокирует время для слота типа A
        insertBooking(typeB, start, 60)

        val response = get("/event-types/$typeA/availability")

        val days = response.extractList("days")
        val tomorrow = days.find {
            it.get("date").asText() == java.time.LocalDate.now(clock.withZone(props.zone)).plusDays(1).toString()
        }!!
        val starts = tomorrow.get("slots").map { it.get("startAt").asText() }
        assertThat(starts).noneMatch { OffsetDateTime.parse(it).isEqual(start) }
    }

    @Test
    fun `GET availability excludes slots beyond working hours`() {
        // длительность 120 мин: последний влезающий старт в 09..18 — 16:00
        val id = createEventType(durationMinutes = 120)

        val response = get("/event-types/$id/availability")

        val days = response.extractList("days")
        val tomorrow = days.find {
            it.get("date").asText() == java.time.LocalDate.now(clock.withZone(props.zone)).plusDays(1).toString()
        }!!
        // ни один слот не выходит за рабочие часы
        assertThat(tomorrow.get("slots").all {
            OffsetDateTime.parse(it.get("endAt").asText())
                .atZoneSameInstant(props.zone).toLocalTime() <= props.workEnd
        }).isTrue()
    }
}
