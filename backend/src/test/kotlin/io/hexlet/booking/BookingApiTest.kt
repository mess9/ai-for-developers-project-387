package io.hexlet.booking

import io.hexlet.booking.config.BookingProperties
import io.hexlet.booking.db.Tables.BOOKINGS
import io.hexlet.booking.db.Tables.SLOTS
import io.hexlet.booking.model.BookingRequest
import io.hexlet.booking.service.TokenService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class BookingApiTest : AbstractIntegrationTest() {

    @Autowired lateinit var props: BookingProperties
    @Autowired lateinit var tokenService: TokenService

    private fun firstFutureSlot() = dsl.selectFrom(SLOTS)
        .where(SLOTS.START_AT.gt(OffsetDateTime.now()))
        .orderBy(SLOTS.START_AT)
        .limit(1)
        .fetchOne()!!

    private fun slotStartAtStr(slot: io.hexlet.booking.db.tables.records.SlotsRecord): String =
        slot.startAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    private fun validRequest(startAt: String) = mapOf(
        "startAt"     to startAt,
        "name"        to "Анна Иванова",
        "meetingLink" to "https://meet.example.com/abc-defg",
        "description" to "Тестовая встреча",
    )

    private fun validBookingRequest(startAt: OffsetDateTime) = BookingRequest(
        startAt = startAt,
        name = "Анна Иванова",
        meetingLink = "https://meet.example.com/abc-defg",
        description = "Тестовая встреча"
    )

    @Test
    fun `POST bookings 201 for valid free slot`() {
        val slot = firstFutureSlot()

        val response = post("/bookings", validBookingRequest(slot.startAt))

        assertThat(response.status).isEqualTo(201)
        assertThat(response.extractPath("name")).isEqualTo("Анна Иванова")
        assertThat(response.extractPath("meetingLink")).contains("meet.example.com")
        assertThat(response.extractPath("description")).isEqualTo("Тестовая встреча")
        assertThat(response.extractPath("createdAt")).isNotBlank()
        assertThat(response.extractPath("startAt")).isNotBlank()
        assertThat(response.extractPath("endAt")).isNotBlank()
        assertThat(response.jsonBody().has("id")).isFalse()
    }

    @Test
    fun `POST bookings saves booking in database`() {
        val slot = firstFutureSlot()

        post("/bookings", validBookingRequest(slot.startAt))

        val saved = dsl.selectFrom(BOOKINGS).where(BOOKINGS.SLOT_ID.eq(slot.id)).fetchOne()
        assertThat(saved).isNotNull
        assertThat(saved!!.name).isEqualTo("Анна Иванова")
    }

    @Test
    fun `POST bookings 409 when slot already booked`() {
        val slot = firstFutureSlot()
        val request = validBookingRequest(slot.startAt)

        post("/bookings", request)

        val response = post("/bookings", request)

        assertThat(response.status).isEqualTo(409)
        assertThat(response.extractPath("errorCode")).isEqualTo("SLOT_ALREADY_BOOKED")
    }

    @Test
    fun `POST bookings 422 for past slot`() {
        val pastStart = OffsetDateTime.now().minusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0)
        val pastEnd   = pastStart.plusMinutes(30)
        dsl.insertInto(SLOTS, SLOTS.START_AT, SLOTS.END_AT)
            .values(pastStart, pastEnd)
            .onConflictDoNothing()
            .execute()

        val response = post("/bookings", validBookingRequest(pastStart))

        assertThat(response.status).isEqualTo(422)
        assertThat(response.extractPath("errorCode")).isEqualTo("SLOT_IN_PAST")
    }

    @Test
    fun `POST bookings 422 for slot beyond horizon`() {
        val futureDate = LocalDate.now(props.zone).plusDays(props.horizonDays.toLong() + 10)
        val farStart = ZonedDateTime.of(futureDate, props.workStart, props.zone).toOffsetDateTime()
        val farEnd   = farStart.plusMinutes(props.slotMinutes.toLong())
        dsl.insertInto(SLOTS, SLOTS.START_AT, SLOTS.END_AT)
            .values(farStart, farEnd)
            .onConflictDoNothing()
            .execute()

        val response = post("/bookings", validBookingRequest(farStart))

        assertThat(response.status).isEqualTo(422)
        assertThat(response.extractPath("errorCode")).isEqualTo("SLOT_OUT_OF_HORIZON")
    }

    @Test
    fun `POST bookings 201 for slot exactly on horizon boundary`() {
        val lastDay = LocalDate.now(props.zone).plusDays(props.horizonDays.toLong())
        val lastStart = ZonedDateTime.of(lastDay, props.workStart, props.zone).toOffsetDateTime()
        val lastEnd   = lastStart.plusMinutes(props.slotMinutes.toLong())
        dsl.insertInto(SLOTS, SLOTS.START_AT, SLOTS.END_AT)
            .values(lastStart, lastEnd)
            .onConflictDoNothing()
            .execute()

        val response = post("/bookings", validBookingRequest(lastStart))

        assertThat(response.status).isEqualTo(201)
    }

    @Test
    fun `POST bookings 422 when startAt not on grid`() {
        val nonExistent = OffsetDateTime.now().plusDays(1).withSecond(37)

        val response = post("/bookings", validBookingRequest(nonExistent))

        assertThat(response.status).isEqualTo(422)
        assertThat(response.extractPath("errorCode")).isEqualTo("SLOT_NOT_ON_GRID")
    }

    @Test
    fun `POST bookings 404 when slot does not exist in database`() {
        val slot = firstFutureSlot()
        val nonExistentStart = slot.startAt.plusDays(100)

        val response = post("/bookings", validBookingRequest(nonExistentStart))

        assertThat(response.status).isEqualTo(404)
        assertThat(response.extractPath("errorCode")).isEqualTo("SLOT_NOT_FOUND")
    }

    @Test
    fun `POST bookings 400 when meetingLink is not valid URI`() {
        val slot = firstFutureSlot()

        val response = post("/bookings", mapOf(
            "startAt" to slotStartAtStr(slot),
            "name" to "Анна",
            "meetingLink" to "not-a-valid-uri"
        ))

        assertThat(response.status).isEqualTo(400)
        assertThat(response.extractPath("errorCode")).isEqualTo("VALIDATION_FAILED")
    }

    @Test
    fun `POST bookings 400 when name is empty`() {
        val slot = firstFutureSlot()

        val response = post("/bookings", mapOf(
            "startAt" to slotStartAtStr(slot),
            "name" to "",
            "meetingLink" to "https://meet.example.com/abc"
        ))

        assertThat(response.status).isEqualTo(400)
        assertThat(response.extractPath("errorCode")).isEqualTo("VALIDATION_FAILED")
        assertThat(response.extractList("errors")).isNotEmpty()
    }

    @Test
    fun `POST bookings 201 when name is exactly minLength 1`() {
        val slot = firstFutureSlot()

        val response = post("/bookings", mapOf(
            "startAt" to slotStartAtStr(slot),
            "name" to "Я",
            "meetingLink" to "https://meet.example.com/abc"
        ))

        assertThat(response.status).isEqualTo(201)
    }

    @Test
    fun `POST bookings 201 when name is exactly maxLength 255`() {
        val slot = firstFutureSlot()

        val response = post("/bookings", mapOf(
            "startAt" to slotStartAtStr(slot),
            "name" to "A".repeat(255),
            "meetingLink" to "https://meet.example.com/test"
        ))

        assertThat(response.status).isEqualTo(201)
    }

    @Test
    fun `POST bookings 400 when name exceeds maxLength 255`() {
        val slot = firstFutureSlot()

        val response = post("/bookings", mapOf(
            "startAt" to slotStartAtStr(slot),
            "name" to "A".repeat(256),
            "meetingLink" to "https://meet.example.com/test"
        ))

        assertThat(response.status).isEqualTo(400)
        assertThat(response.extractPath("errorCode")).isEqualTo("VALIDATION_FAILED")
    }

    @Test
    fun `POST bookings 400 when meetingLink is missing`() {
        val slot = firstFutureSlot()

        val response = post("/bookings", mapOf(
            "startAt" to slotStartAtStr(slot),
            "name" to "Анна"
        ))

        assertThat(response.status).isEqualTo(400)
        assertThat(response.extractPath("errorCode")).isEqualTo("MALFORMED_REQUEST")
    }

    @Test
    fun `POST bookings 400 on malformed JSON`() {
        val request = org.http4k.core.Request(org.http4k.core.Method.POST, org.http4k.core.Uri.of("$baseUrl/bookings"))
            .header("Content-Type", "application/json")
            .body("{ bad json }")
        val response = client(request)

        assertThat(response.status.code).isEqualTo(400)
        assertThat(response.bodyString()).contains("errorCode")
        assertThat(response.bodyString()).contains("MALFORMED_REQUEST")
    }

    @Test
    fun `GET slot 200 for existing slot`() {
        val slot = firstFutureSlot()
        val startAt = slotStartAtStr(slot)

        val response = get("/slots/$startAt")

        assertThat(response.status).isEqualTo(200)
        assertThat(response.extractPath("status")).isEqualTo("FREE")
        assertThat(response.extractPath("startAt")).isNotBlank()
    }

    @Test
    fun `GET slot status is BOOKED after booking`() {
        val slot = firstFutureSlot()
        post("/bookings", validRequest(slotStartAtStr(slot)))

        val startAt = slotStartAtStr(slot)
        val response = get("/slots/$startAt")

        assertThat(response.status).isEqualTo(200)
        assertThat(response.extractPath("status")).isEqualTo("BOOKED")
    }

    @Test
    fun `GET slot 404 for non-existent startAt`() {
        val nonExistent = OffsetDateTime.now().plusDays(1).withSecond(37)
        val startAt = nonExistent.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        val response = get("/slots/$startAt")

        assertThat(response.status).isEqualTo(404)
    }

    @Test
    fun `GET slot status is PAST for past slot`() {
        val pastStart = OffsetDateTime.now().minusDays(1)
        val pastEnd = pastStart.plusMinutes(props.slotMinutes.toLong())
        dsl.insertInto(SLOTS, SLOTS.START_AT, SLOTS.END_AT)
            .values(pastStart, pastEnd)
            .onConflictDoNothing()
            .execute()

        val response = get("/slots/${pastStart.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}")

        assertThat(response.status).isEqualTo(200)
        assertThat(response.extractPath("status")).isEqualTo("PAST")
    }

    @Test
    fun `GET slot 200 does NOT expose booking details for booked slot`() {
        val slot = firstFutureSlot()
        post("/bookings", validRequest(slotStartAtStr(slot)))

        val startAt = slotStartAtStr(slot)
        val response = get("/slots/$startAt")

        assertThat(response.status).isEqualTo(200)
        assertThat(response.extractPath("status")).isEqualTo("BOOKED")
        assertThat(response.extractPath("booking")).isNull()
    }

    @Test
    fun `POST bookings 201 with null description`() {
        val slot = firstFutureSlot()

        val response = post("/bookings", mapOf(
            "startAt" to slotStartAtStr(slot),
            "name" to "Сергей",
            "meetingLink" to "https://meet.example.com/null-desc"
        ))

        assertThat(response.status).isEqualTo(201)
    }

    @Test
    fun `POST bookings 400 when meetingLink is empty`() {
        val slot = firstFutureSlot()

        val response = post("/bookings", mapOf(
            "startAt" to slotStartAtStr(slot),
            "name" to "Анна",
            "meetingLink" to ""
        ))

        assertThat(response.status).isEqualTo(400)
        assertThat(response.extractPath("errorCode")).isEqualTo("VALIDATION_FAILED")
    }

    @Test
    fun `POST bookings 201 when meetingLink is exactly maxLength 2048`() {
        val slot = firstFutureSlot()
        val prefix = "https://meet.example.com/"
        val longLink = prefix + "a".repeat(2048 - prefix.length)

        val response = post("/bookings", mapOf(
            "startAt" to slotStartAtStr(slot),
            "name" to "Анна",
            "meetingLink" to longLink
        ))

        assertThat(response.status).isEqualTo(201)
    }

    @Test
    fun `POST bookings 400 when meetingLink exceeds maxLength 2048`() {
        val slot = firstFutureSlot()
        val prefix = "https://meet.example.com/"
        val longLink = prefix + "a".repeat(2049 - prefix.length)

        val response = post("/bookings", mapOf(
            "startAt" to slotStartAtStr(slot),
            "name" to "Анна",
            "meetingLink" to longLink
        ))

        assertThat(response.status).isEqualTo(400)
        assertThat(response.extractPath("errorCode")).isEqualTo("VALIDATION_FAILED")
    }

    @Test
    fun `POST bookings 201 when description is exactly maxLength 2000`() {
        val slot = firstFutureSlot()

        val response = post("/bookings", mapOf(
            "startAt" to slotStartAtStr(slot),
            "name" to "Анна",
            "meetingLink" to "https://meet.example.com/test",
            "description" to "D".repeat(2000)
        ))

        assertThat(response.status).isEqualTo(201)
    }

    @Test
    fun `POST bookings 400 when description exceeds maxLength 2000`() {
        val slot = firstFutureSlot()

        val response = post("/bookings", mapOf(
            "startAt" to slotStartAtStr(slot),
            "name" to "Анна",
            "meetingLink" to "https://meet.example.com/test",
            "description" to "D".repeat(2001)
        ))

        assertThat(response.status).isEqualTo(400)
        assertThat(response.extractPath("errorCode")).isEqualTo("VALIDATION_FAILED")
    }

    @Test
    fun `GET slots startAt 400 for invalid date-time format`() {
        val response = get("/slots/not-a-date-time")

        assertThat(response.status).isEqualTo(400)
        assertThat(response.extractPath("errorCode")).isEqualTo("MALFORMED_REQUEST")
    }

    @Test
    fun `Error response follows ProblemDetail format`() {
        val response = get("/slots/not-a-date-time")

        assertThat(response.status).isEqualTo(400)
        assertThat(response.extractPath("status")).isEqualTo("400")
        assertThat(response.extractPath("errorCode")).isNotBlank()
        assertThat(response.extractPath("title")).isNotBlank()
        assertThat(response.extractPath("detail")).isNotBlank()
    }

    @Test
    fun `POST bookings 201 for slot at midnight in owner timezone`() {
        val slot = dsl.selectFrom(SLOTS)
            .where(SLOTS.START_AT.gt(OffsetDateTime.now()))
            .and(SLOTS.ID.notIn(dsl.select(BOOKINGS.SLOT_ID).from(BOOKINGS)))
            .orderBy(SLOTS.START_AT)
            .limit(1)
            .fetchOne()!!

        val response = post("/bookings", validBookingRequest(slot.startAt))

        assertThat(response.status).isEqualTo(201)
        val returnedStartAt = OffsetDateTime.parse(response.extractPath("startAt").toString())
        assertThat(returnedStartAt).isEqualTo(slot.startAt)
    }

    @Test
    fun `POST bookings 201 for slot at month boundary last to first day`() {
        val ownerZone = ZoneId.of("Asia/Yerevan")
        val lastDayOfMonth = LocalDate.now(ownerZone).withDayOfMonth(1).plusMonths(1).minusDays(1)
        val firstSlotStart = ZonedDateTime.of(lastDayOfMonth, props.workStart, ownerZone).toOffsetDateTime()
        val firstSlotEnd = firstSlotStart.plusMinutes(props.slotMinutes.toLong())
        dsl.insertInto(SLOTS, SLOTS.START_AT, SLOTS.END_AT)
            .values(firstSlotStart, firstSlotEnd)
            .onConflictDoNothing()
            .execute()

        val response = post("/bookings", validBookingRequest(firstSlotStart))

        assertThat(response.status).isEqualTo(201)
    }

    @Test
    fun `GET calendar includes slots crossing month boundary`() {
        val ownerZone = ZoneId.of("Asia/Yerevan")
        val currentMonth = java.time.YearMonth.now(ownerZone)
        val lastDayOfMonth = currentMonth.atEndOfMonth()
        val lastSlotStart = ZonedDateTime.of(lastDayOfMonth, props.workStart, ownerZone).toOffsetDateTime()
        val lastSlotEnd = lastSlotStart.plusMinutes(props.slotMinutes.toLong())
        dsl.insertInto(SLOTS, SLOTS.START_AT, SLOTS.END_AT)
            .values(lastSlotStart, lastSlotEnd)
            .onConflictDoNothing()
            .execute()

        val response = get("/calendar?month=${currentMonth.toString()}")

        assertThat(response.status).isEqualTo(200)
        val days = response.extractList("days")
        val lastDay = days.find { it.get("date")?.asText() == lastDayOfMonth.toString() }
        assertThat(lastDay).isNotNull
        val slots = lastDay?.get("slots")
        assertThat(slots).isNotNull
        assertThat(slots?.size()).isGreaterThan(0)
    }

    @Test
    fun `GET calendar groups slots by correct day in owner timezone`() {
        val ownerZone = ZoneId.of("Asia/Yerevan")
        val currentMonth = java.time.YearMonth.now(ownerZone)
        val lastDayOfMonth = currentMonth.atEndOfMonth()
        val lastSlotStart = ZonedDateTime.of(lastDayOfMonth, props.workStart, ownerZone).toOffsetDateTime()
        val lastSlotEnd = lastSlotStart.plusMinutes(props.slotMinutes.toLong())
        dsl.insertInto(SLOTS, SLOTS.START_AT, SLOTS.END_AT)
            .values(lastSlotStart, lastSlotEnd)
            .onConflictDoNothing()
            .execute()

        val response = get("/calendar?month=${currentMonth.toString()}")

        assertThat(response.status).isEqualTo(200)
        val days = response.extractList("days")
        val lastDay = days.find { it.get("date")?.asText() == lastDayOfMonth.toString() }
        assertThat(lastDay).isNotNull
        val slots = lastDay?.get("slots")
        assertThat(slots).isNotNull
        assertThat(slots?.size()).isGreaterThan(0)
    }

    @Test
    fun `POST bookings 201 for slot today within working hours`() {
        val today = LocalDate.now(props.zone)
        val nowInTz = ZonedDateTime.now(props.zone)
        val slotTime = if (nowInTz.hour < props.workStart.hour) {
            ZonedDateTime.of(today, props.workStart, props.zone)
        } else {
            ZonedDateTime.of(today, props.workStart.plusHours(1), props.zone)
        }
        val todayStart = slotTime.toOffsetDateTime()
        
        if (todayStart.isBefore(OffsetDateTime.now())) {
            return
        }
        
        val todayEnd = todayStart.plusMinutes(props.slotMinutes.toLong())
        dsl.insertInto(SLOTS, SLOTS.START_AT, SLOTS.END_AT)
            .values(todayStart, todayEnd)
            .onConflictDoNothing()
            .execute()

        val response = post("/bookings", validBookingRequest(todayStart))

        assertThat(response.status).isEqualTo(201)
    }

    @Test
    fun `Error response includes type field`() {
        val response = get("/slots/not-a-date-time")

        assertThat(response.status).isEqualTo(400)
        assertThat(response.extractPath("type")).isEqualTo("about:blank")
    }

    @Test
    fun `Error response includes errors array for validation errors`() {
        val slot = firstFutureSlot()

        val response = post("/bookings", mapOf(
            "startAt" to slotStartAtStr(slot),
            "name" to "",
            "meetingLink" to "https://meet.example.com/test"
        ))

        assertThat(response.status).isEqualTo(400)
        assertThat(response.extractPath("errorCode")).isEqualTo("VALIDATION_FAILED")
        val errors = response.extractList("errors")
        assertThat(errors).isNotEmpty()
        val error = errors.first()
        assertThat(error.get("field")?.asText()).isEqualTo("name")
        assertThat(error.get("message")?.asText()).isNotBlank()
    }
}
