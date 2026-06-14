package io.hexlet.booking

import io.hexlet.booking.db.Tables.BOOKINGS
import io.hexlet.booking.service.TokenService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.OffsetDateTime
import java.util.*

class AdminApiTest : AbstractIntegrationTest() {

    @Autowired lateinit var tokenService: TokenService

    private fun token() = tokenService.currentToken()

    private fun insertBooking(
        eventTypeId: UUID,
        start: OffsetDateTime,
        durationMinutes: Long = 30,
        name: String = "Тест Тестов",
    ): UUID {
        val id = UUID.randomUUID()
        dsl.insertInto(BOOKINGS)
            .set(BOOKINGS.ID, id)
            .set(BOOKINGS.EVENT_TYPE_ID, eventTypeId)
            .set(BOOKINGS.START_AT, start)
            .set(BOOKINGS.END_AT, start.plusMinutes(durationMinutes))
            .set(BOOKINGS.NAME, name)
            .set(BOOKINGS.MEETING_LINK, "https://meet.example.com/test")
            .set(BOOKINGS.DESCRIPTION, "Тестовое описание")
            .set(BOOKINGS.CREATED_AT, OffsetDateTime.now(clock))
            .execute()
        return id
    }

    // ──── cancel ───────────────────────────────────────────────────────────────

    @Test
    fun `DELETE admin booking 401 without token`() {
        val response = deleteAdmin("", "/admin/bookings/${UUID.randomUUID()}")

        assertThat(response.status).isEqualTo(401)
        assertThat(response.extractPath("errorCode")).isEqualTo("UNAUTHORIZED")
    }

    @Test
    fun `DELETE admin booking 401 with wrong token`() {
        val response = deleteAdmin("bad-token", "/admin/bookings/${UUID.randomUUID()}")

        assertThat(response.status).isEqualTo(401)
        assertThat(response.extractPath("errorCode")).isEqualTo("UNAUTHORIZED")
    }

    @Test
    fun `DELETE admin booking 204 cancels existing booking`() {
        val eventTypeId = createEventType()
        val bookingId = insertBooking(eventTypeId, slotStart())

        val response = deleteAdmin(token(), "/admin/bookings/$bookingId")

        assertThat(response.status).isEqualTo(204)
        assertThat(dsl.fetchCount(BOOKINGS, BOOKINGS.ID.eq(bookingId))).isEqualTo(0)
    }

    @Test
    fun `DELETE admin booking 404 for unknown id`() {
        val response = deleteAdmin(token(), "/admin/bookings/${UUID.randomUUID()}")

        assertThat(response.status).isEqualTo(404)
        assertThat(response.extractPath("errorCode")).isEqualTo("BOOKING_NOT_FOUND")
    }

    @Test
    fun `DELETE admin booking 400 for invalid UUID format`() {
        val response = deleteAdmin(token(), "/admin/bookings/not-a-uuid")

        assertThat(response.status).isEqualTo(400)
        assertThat(response.extractPath("errorCode")).isEqualTo("MALFORMED_REQUEST")
    }

    @Test
    fun `DELETE admin booking 404 for already cancelled booking`() {
        val eventTypeId = createEventType()
        val bookingId = insertBooking(eventTypeId, slotStart())

        assertThat(deleteAdmin(token(), "/admin/bookings/$bookingId").status).isEqualTo(204)
        val second = deleteAdmin(token(), "/admin/bookings/$bookingId")

        assertThat(second.status).isEqualTo(404)
        assertThat(second.extractPath("errorCode")).isEqualTo("BOOKING_NOT_FOUND")
    }

    @Test
    fun `cancelling a booking frees the slot in availability`() {
        val eventTypeId = createEventType(durationMinutes = 30)
        val start = slotStart()
        val bookingId = insertBooking(eventTypeId, start, 30)

        val before = get("/event-types/$eventTypeId/availability")
        val tomorrowBefore = before.extractList("days").first {
            it.get("date").asText() == java.time.LocalDate.now(clock.withZone(props.zone)).plusDays(1).toString()
        }
        assertThat(tomorrowBefore.get("slots").map { it.get("startAt").asText() })
            .noneMatch { OffsetDateTime.parse(it).isEqual(start) }

        deleteAdmin(token(), "/admin/bookings/$bookingId")

        val after = get("/event-types/$eventTypeId/availability")
        val tomorrowAfter = after.extractList("days").first {
            it.get("date").asText() == java.time.LocalDate.now(clock.withZone(props.zone)).plusDays(1).toString()
        }
        assertThat(tomorrowAfter.get("slots").map { it.get("startAt").asText() })
            .anyMatch { OffsetDateTime.parse(it).isEqual(start) }
    }

    // ──── list ───────────────────────────────────────────────────────────────

    @Test
    fun `GET admin bookings 401 without token`() {
        val response = get("/admin/bookings")

        assertThat(response.status).isEqualTo(401)
        assertThat(response.extractPath("errorCode")).isEqualTo("UNAUTHORIZED")
    }

    @Test
    fun `GET admin bookings returns flat list with eventTypeName`() {
        val typeA = createEventType(durationMinutes = 30, name = "Intro call")
        insertBooking(typeA, slotStart(daysAhead = 1, slotIndex = 0), 30)

        val response = getAdmin(token(), "/admin/bookings")

        assertThat(response.status).isEqualTo(200)
        val list = response.jsonBody()
        assertThat(list.size()).isEqualTo(1)
        val first = list.first()
        assertThat(first.get("eventTypeName").asText()).isEqualTo("Intro call")
        assertThat(first.get("eventTypeId").asText()).isEqualTo(typeA.toString())
        assertThat(first.get("name").asText()).isEqualTo("Тест Тестов")
        assertThat(first.has("id")).isTrue()
    }

    @Test
    fun `GET admin bookings sorted by start and excludes past`() {
        val type = createEventType(durationMinutes = 30, name = "T")
        // прошлая встреча — не должна попасть в список
        insertBooking(type, slotStart(daysAhead = -1), 30, name = "Прошлый")
        // две будущие в разном порядке вставки
        insertBooking(type, slotStart(daysAhead = 2, slotIndex = 0), 30, name = "Позже")
        insertBooking(type, slotStart(daysAhead = 1, slotIndex = 0), 30, name = "Раньше")

        val response = getAdmin(token(), "/admin/bookings")

        assertThat(response.status).isEqualTo(200)
        val names = response.jsonBody().map { it.get("name").asText() }
        assertThat(names).containsExactly("Раньше", "Позже")
    }
}
