package io.hexlet.booking

import io.hexlet.booking.config.BookingProperties
import io.hexlet.booking.db.Tables.BOOKINGS
import io.hexlet.booking.db.Tables.SLOTS
import io.hexlet.booking.model.BookingRequest
import io.hexlet.booking.service.TokenService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

class AdminApiTest : AbstractIntegrationTest() {

    @Autowired lateinit var props: BookingProperties
    @Autowired lateinit var tokenService: TokenService

    private val ownerZone = ZoneId.of("Asia/Yerevan")

    private fun insertBooking(): Pair<UUID, io.hexlet.booking.db.tables.records.SlotsRecord> {
        val slot = dsl.selectFrom(SLOTS)
            .where(SLOTS.START_AT.gt(OffsetDateTime.now(ownerZone)))
            .orderBy(SLOTS.START_AT)
            .limit(1)
            .fetchOne()!!
        val bookingId = UUID.randomUUID()
        dsl.insertInto(BOOKINGS)
            .set(BOOKINGS.ID,           bookingId)
            .set(BOOKINGS.SLOT_ID,      slot.id)
            .set(BOOKINGS.NAME,         "Тест Тестов")
            .set(BOOKINGS.MEETING_LINK, "https://meet.example.com/test")
            .set(BOOKINGS.DESCRIPTION,  "Тестовое описание")
            .set(BOOKINGS.CREATED_AT,   OffsetDateTime.now(ownerZone))
            .execute()
        return bookingId to slot
    }

    private fun encoded(dt: OffsetDateTime): String =
        dt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    private fun validBookingRequest(startAt: OffsetDateTime) = BookingRequest(
        startAt = startAt,
        name = "Тест Тестов",
        meetingLink = "https://meet.example.com/test",
        description = "Тестовое описание"
    )

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
        val (bookingId, _) = insertBooking()

        val response = deleteAdmin(tokenService.currentToken(), "/admin/bookings/$bookingId")

        assertThat(response.status).isEqualTo(204)

        val remaining = dsl.fetchCount(BOOKINGS, BOOKINGS.ID.eq(bookingId))
        assertThat(remaining).isEqualTo(0)
    }

    @Test
    fun `DELETE admin booking 404 for unknown id`() {
        val response = deleteAdmin(tokenService.currentToken(), "/admin/bookings/${UUID.randomUUID()}")

        assertThat(response.status).isEqualTo(404)
        assertThat(response.extractPath("errorCode")).isEqualTo("BOOKING_NOT_FOUND")
    }

    @Test
    fun `slot becomes FREE again after booking cancellation`() {
        val (bookingId, slot) = insertBooking()

        val beforeResponse = get("/slots/${encoded(slot.startAt)}")
        assertThat(beforeResponse.extractPath("status")).isEqualTo("BOOKED")

        deleteAdmin(tokenService.currentToken(), "/admin/bookings/$bookingId")

        val afterResponse = get("/slots/${encoded(slot.startAt)}")
        assertThat(afterResponse.extractPath("status")).isEqualTo("FREE")
    }

    @Test
    fun `GET admin slot 401 without token`() {
        val slot = dsl.selectFrom(SLOTS).orderBy(SLOTS.START_AT).limit(1).fetchOne()!!
        val response = get("/admin/slots/${encoded(slot.startAt)}")

        assertThat(response.status).isEqualTo(401)
        assertThat(response.extractPath("errorCode")).isEqualTo("UNAUTHORIZED")
    }

    @Test
    fun `GET admin slot 200 with valid token, booking details visible`() {
        val (_, slot) = insertBooking()

        val response = getAdmin(tokenService.currentToken(), "/admin/slots/${encoded(slot.startAt)}")

        assertThat(response.extractPath("status")).isEqualTo("BOOKED")
        assertThat(response.extractPath("booking.name")).isEqualTo("Тест Тестов")
        assertThat(response.extractPath("booking.meetingLink")).isEqualTo("https://meet.example.com/test")
        assertThat(response.extractPath("booking.id")).isNotBlank()
    }

    @Test
    fun `GET admin slot 404 for non-existent startAt`() {
        val nonExistent = OffsetDateTime.now(ownerZone).plusDays(1).withSecond(37)

        val response = getAdmin(tokenService.currentToken(), "/admin/slots/${encoded(nonExistent)}")

        assertThat(response.status).isEqualTo(404)
        assertThat(response.extractPath("errorCode")).isEqualTo("SLOT_NOT_FOUND")
    }

    @Test
    fun `GET admin slot 200 free slot has no booking field`() {
        val slot = dsl.selectFrom(SLOTS)
            .where(SLOTS.START_AT.gt(OffsetDateTime.now(ownerZone)))
            .orderBy(SLOTS.START_AT).limit(1).fetchOne()!!

        val response = getAdmin(tokenService.currentToken(), "/admin/slots/${encoded(slot.startAt)}")

        assertThat(response.extractPath("status")).isEqualTo("FREE")
        assertThat(response.extractPath("booking")).isNull()
    }

    @Test
    fun `GET admin calendar exposes booking details`() {
        insertBooking()
        val month = java.time.YearMonth.now(ZoneId.of("Asia/Yerevan")).toString()

        val response = getAdmin(tokenService.currentToken(), "/admin/calendar?month=$month")

        assertThat(response.status).isEqualTo(200)
        assertThat(response.extractPath("month")).isEqualTo(month)
        val json = response.jsonBody()
        val daysNode = json.get("days")
        val slots = mutableListOf<com.fasterxml.jackson.databind.JsonNode>()
        daysNode?.forEach { day ->
            day.get("slots")?.forEach { slot -> slots.add(slot) }
        }
        val bookedCount = slots.count { it.get("status")?.asText() == "BOOKED" }
        assertThat(bookedCount).isGreaterThan(0)

        val bookedSlot = slots.firstOrNull { it.get("status")?.asText() == "BOOKED" }
        assertThat(bookedSlot).isNotNull
        assertThat(bookedSlot?.get("booking")?.get("name")?.asText()).isEqualTo("Тест Тестов")
        assertThat(bookedSlot?.get("booking")?.get("meetingLink")?.asText()).isEqualTo("https://meet.example.com/test")
        assertThat(bookedSlot?.get("booking")?.get("description")?.asText()).isEqualTo("Тестовое описание")
    }

    @Test
    fun `GET admin calendar 400 for invalid month format`() {
        val response = getAdmin(tokenService.currentToken(), "/admin/calendar?month=invalid")

        assertThat(response.status).isEqualTo(400)
    }

    @Test
    fun `GET admin calendar 400 for invalid month value`() {
        val response = getAdmin(tokenService.currentToken(), "/admin/calendar?month=2026-13")

        assertThat(response.status).isEqualTo(400)
    }

    @Test
    fun `GET admin slot 400 for invalid startAt format`() {
        val response = getAdmin(tokenService.currentToken(), "/admin/slots/not-a-date-time")

        assertThat(response.status).isEqualTo(400)
        assertThat(response.extractPath("errorCode")).isEqualTo("MALFORMED_REQUEST")
    }

    @Test
    fun `DELETE admin booking 400 for invalid UUID format`() {
        val response = deleteAdmin(tokenService.currentToken(), "/admin/bookings/not-a-uuid")

        assertThat(response.status).isEqualTo(400)
        assertThat(response.extractPath("errorCode")).isEqualTo("MALFORMED_REQUEST")
    }

    @Test
    fun `DELETE admin booking 404 for already cancelled booking`() {
        val (bookingId, _) = insertBooking()

        val cancelResponse1 = deleteAdmin(tokenService.currentToken(), "/admin/bookings/$bookingId")
        assertThat(cancelResponse1.status).isEqualTo(204)

        val cancelResponse2 = deleteAdmin(tokenService.currentToken(), "/admin/bookings/$bookingId")

        assertThat(cancelResponse2.status).isEqualTo(404)
        assertThat(cancelResponse2.extractPath("errorCode")).isEqualTo("BOOKING_NOT_FOUND")
    }
}
