package io.hexlet.booking

import io.hexlet.booking.config.BookingProperties
import io.hexlet.booking.db.Tables.BOOKINGS
import io.hexlet.booking.db.Tables.SLOTS
import io.hexlet.booking.model.BookingRequest
import io.hexlet.booking.service.TokenService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.*
import java.util.*

class CalendarApiTest : AbstractIntegrationTest() {

    @Autowired lateinit var props: BookingProperties
    @Autowired lateinit var tokenService: TokenService

    private val currentMonth: String get() = YearMonth.now(ZoneId.of("Asia/Yerevan")).toString()

    private fun insertBooking(): Pair<UUID, io.hexlet.booking.db.tables.records.SlotsRecord> {
        val slot = dsl.selectFrom(SLOTS)
            .where(SLOTS.START_AT.gt(OffsetDateTime.now(ZoneId.of("Asia/Yerevan"))))
            .orderBy(SLOTS.START_AT).limit(1).fetchOne()!!
        val bookingId = UUID.randomUUID()
        dsl.insertInto(BOOKINGS)
            .set(BOOKINGS.ID, bookingId)
            .set(BOOKINGS.SLOT_ID, slot.id)
            .set(BOOKINGS.NAME, "Тестовый Пользователь")
            .set(BOOKINGS.MEETING_LINK, "https://meet.example.com/test")
            .set(BOOKINGS.DESCRIPTION, "Тест")
            .set(BOOKINGS.CREATED_AT, OffsetDateTime.now())
            .execute()
        return bookingId to slot
    }

    private fun validBookingRequest(startAt: OffsetDateTime) = BookingRequest(
        startAt = startAt,
        name = "Тестовый Пользователь",
        meetingLink = "https://meet.example.com/test",
        description = "Тест"
    )

    @Test
    fun `GET calendar returns 200 for current month`() {
        val response = get("/calendar?month=$currentMonth")

        assertThat(response.status).isEqualTo(200)
    }

    @Test
    fun `GET calendar response has month and days fields`() {
        val response = get("/calendar?month=$currentMonth")

        assertThat(response.extractPath("month")).isEqualTo(currentMonth)
        assertThat(response.extractPath("ownerTimeZone")).isNotBlank()
        assertThat(response.extractList("days")).isNotEmpty()
    }

    @Test
    fun `GET calendar days contain slots`() {
        val response = get("/calendar?month=$currentMonth")

        assertThat(response.extractList("days")).isNotEmpty()
    }

    @Test
    fun `GET calendar days have hasFreeSlots matching slot statuses`() {
        val response = get("/calendar?month=$currentMonth")

        val days = response.extractList("days")
        assertThat(days).isNotEmpty

        for (day in days) {
            val hasFreeSlots = day.get("hasFreeSlots")?.asBoolean()
            val slots = day.get("slots") ?: continue
            val anyFree = slots.any { it.get("status")?.asText() == "FREE" }
            assertThat(hasFreeSlots).isEqualTo(anyFree)
        }
    }

    @Test
    fun `GET calendar 400 for invalid month format`() {
        val response = get("/calendar?month=2026-13")

        assertThat(response.status).isEqualTo(400)
    }

    @Test
    fun `GET calendar 400 when month param missing`() {
        val response = get("/calendar")

        assertThat(response.status).isEqualTo(400)
    }

    @Test
    fun `GET calendar returns empty days for month with no slots`() {
        val response = get("/calendar?month=2030-01")

        assertThat(response.status).isEqualTo(200)
        assertThat(response.extractPath("month")).isEqualTo("2030-01")
        assertThat(response.extractPath("ownerTimeZone")).isNotBlank()
        val days = response.extractList("days")
        assertThat(days).isEmpty()
    }

    @Test
    fun `GET admin calendar 401 without token`() {
        val response = get("/admin/calendar?month=$currentMonth")

        assertThat(response.status).isEqualTo(401)
    }

    @Test
    fun `GET admin calendar 401 with wrong token`() {
        val response = getAdmin("wrong-token", "/admin/calendar?month=$currentMonth")

        assertThat(response.status).isEqualTo(401)
    }

    @Test
    fun `GET admin calendar 200 with valid token`() {
        val response = getAdmin(tokenService.currentToken(), "/admin/calendar?month=$currentMonth")

        assertThat(response.status).isEqualTo(200)
    }

    @Test
    fun `GET admin calendar includes booking details`() {
        insertBooking()
        val response = getAdmin(tokenService.currentToken(), "/admin/calendar?month=$currentMonth")

        assertThat(response.status).isEqualTo(200)
        assertThat(response.extractPath("month")).isEqualTo(currentMonth)
        assertThat(response.extractList("days")).isNotEmpty()

        val days = response.extractList("days")
        val bookedSlots = days.flatMap { day ->
            day.get("slots")?.filter { it.get("status")?.asText() == "BOOKED" } ?: emptyList()
        }
        assertThat(bookedSlots).isNotEmpty
        val firstBooked = bookedSlots.first()
        assertThat(firstBooked.get("booking")?.get("name")?.asText()).isEqualTo("Тестовый Пользователь")
        assertThat(firstBooked.get("booking")?.get("meetingLink")?.asText()).isEqualTo("https://meet.example.com/test")
        assertThat(firstBooked.get("booking")?.get("description")?.asText()).isEqualTo("Тест")
    }

    @Test
    fun `GET public calendar does NOT expose booking details for booked slots`() {
        insertBooking()
        val response = get("/calendar?month=$currentMonth")

        assertThat(response.status).isEqualTo(200)

        val days = response.extractList("days")
        val bookedSlots = days.flatMap { day ->
            day.get("slots")?.filter { it.get("status")?.asText() == "BOOKED" } ?: emptyList()
        }
        assertThat(bookedSlots).isNotEmpty

        for (slot in bookedSlots) {
            assertThat(slot.get("booking")).isNull()
        }
    }

    @Test
    fun `GET admin calendar includes booking details created via API`() {
        val slot = dsl.selectFrom(SLOTS)
            .where(SLOTS.START_AT.gt(OffsetDateTime.now(ZoneId.of("Asia/Yerevan"))))
            .orderBy(SLOTS.START_AT).limit(1)
            .fetchOne()!!

        val bookingRequest = BookingRequest(
            startAt = slot.startAt,
            name = "API User",
            meetingLink = "https://meet.example.com/api-test",
            description = "Created via API"
        )
        post("/bookings", bookingRequest)

        val response = getAdmin(tokenService.currentToken(), "/admin/calendar?month=${currentMonth}")

        assertThat(response.status).isEqualTo(200)
        val days = response.extractList("days")
        val bookedSlots = days.flatMap { day ->
            day.get("slots")?.filter { it.get("status")?.asText() == "BOOKED" } ?: emptyList()
        }
        assertThat(bookedSlots).isNotEmpty
        val apiBookedSlot = bookedSlots.find { it.get("booking")?.get("name")?.asText() == "API User" }
        assertThat(apiBookedSlot).isNotNull
        assertThat(apiBookedSlot?.get("booking")?.get("meetingLink")?.asText()).isEqualTo("https://meet.example.com/api-test")
    }

    @Test
    fun `GET calendar hasFreeSlots is false when all slots in a day are booked`() {
        val tomorrow = LocalDate.now(props.zone).plusDays(1)
        val dayStart = ZonedDateTime.of(tomorrow, props.workStart, props.zone).toOffsetDateTime()
        val dayEnd = ZonedDateTime.of(tomorrow, props.workEnd, props.zone).toOffsetDateTime()

        val allDaySlots = dsl.selectFrom(SLOTS)
            .where(SLOTS.START_AT.ge(dayStart))
            .and(SLOTS.START_AT.lt(dayEnd))
            .orderBy(SLOTS.START_AT)
            .fetch()

        assertThat(allDaySlots).isNotEmpty

        allDaySlots.forEach { slot ->
            dsl.insertInto(BOOKINGS)
                .set(BOOKINGS.ID, UUID.randomUUID())
                .set(BOOKINGS.SLOT_ID, slot.id)
                .set(BOOKINGS.NAME, "Тест")
                .set(BOOKINGS.MEETING_LINK, "https://meet.example.com/test")
                .set(BOOKINGS.CREATED_AT, OffsetDateTime.now())
                .execute()
        }

        val month = YearMonth.now(props.zone).toString()
        val response = get("/calendar?month=$month")

        assertThat(response.status).isEqualTo(200)
        val days = response.extractList("days")
        val targetDay = days.find { it.get("date")?.asText() == tomorrow.toString() }
        assertThat(targetDay).isNotNull
        assertThat(targetDay?.get("hasFreeSlots")?.asBoolean()).isEqualTo(false)

        val slots = targetDay?.get("slots")
        assertThat(slots).isNotNull
        slots!!.forEach { slot ->
            assertThat(slot.get("status")?.asText()).isEqualTo("BOOKED")
        }
    }
}
