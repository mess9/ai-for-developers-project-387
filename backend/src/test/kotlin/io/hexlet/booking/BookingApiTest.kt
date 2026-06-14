package io.hexlet.booking

import io.hexlet.booking.db.Tables.BOOKINGS
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class BookingApiTest : AbstractIntegrationTest() {

    private fun iso(dt: OffsetDateTime) = dt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    private fun request(
        eventTypeId: UUID,
        startAt: OffsetDateTime,
        name: String = "Анна Иванова",
        meetingLink: String = "https://meet.example.com/abc-defg",
        description: String? = "Тестовая встреча",
    ): Map<String, Any?> = buildMap {
        put("eventTypeId", eventTypeId.toString())
        put("startAt", iso(startAt))
        put("name", name)
        put("meetingLink", meetingLink)
        if (description != null) put("description", description)
    }

    @Test
    fun `POST bookings 201 for valid free slot`() {
        val id = createEventType(durationMinutes = 30, name = "Intro call")
        val start = slotStart()

        val response = post("/bookings", request(id, start))

        assertThat(response.status).isEqualTo(201)
        assertThat(response.extractPath("eventTypeName")).isEqualTo("Intro call")
        assertThat(response.extractPath("name")).isEqualTo("Анна Иванова")
        assertThat(response.extractPath("meetingLink")).contains("meet.example.com")
        assertThat(response.extractPath("startAt")).isNotBlank()
        assertThat(response.extractPath("endAt")).isNotBlank()
        assertThat(response.extractPath("createdAt")).isNotBlank()
        // id брони гостю не раскрывается
        assertThat(response.jsonBody().has("id")).isFalse()
    }

    @Test
    fun `POST bookings saves booking with computed endAt`() {
        val id = createEventType(durationMinutes = 30)
        val start = slotStart()

        post("/bookings", request(id, start))

        val saved = dsl.selectFrom(BOOKINGS).fetchOne()
        assertThat(saved).isNotNull
        assertThat(saved!!.eventTypeId).isEqualTo(id)
        assertThat(saved.endAt).isEqualTo(saved.startAt.plusMinutes(30))
    }

    @Test
    fun `POST bookings 201 idempotent same booking returns success`() {
        val id = createEventType()
        val req = request(id, slotStart())

        post("/bookings", req)
        val response = post("/bookings", req)

        assertThat(response.status).isEqualTo(201)
        assertThat(dsl.fetchCount(BOOKINGS)).isEqualTo(1)
    }

    @Test
    fun `POST bookings 409 when same slot with different name`() {
        val id = createEventType()
        val start = slotStart()

        post("/bookings", request(id, start, name = "Анна"))
        val response = post("/bookings", request(id, start, name = "Борис"))

        assertThat(response.status).isEqualTo(409)
    }

    @Test
    fun `POST bookings 409 when overlapping booking of another type`() {
        val typeA = createEventType(durationMinutes = 30, name = "A")
        val typeB = createEventType(durationMinutes = 60, name = "B")
        val start = slotStart()

        post("/bookings", request(typeA, start))
        // бронь типа B на то же время пересекается с бронью типа A → 409
        val response = post("/bookings", request(typeB, start))

        assertThat(response.status).isEqualTo(409)
        assertThat(response.extractPath("errorCode")).isEqualTo("SLOT_ALREADY_BOOKED")
    }

    @Test
    fun `POST bookings concurrent same slot yields exactly one success rest 409`() {
        val id = createEventType(durationMinutes = 30)
        val start = slotStart()
        val threads = 8

        // Все потоки стартуют одновременно (барьер) — настоящая гонка за один слот.
        // In-memory pre-check у всех видит пустоту, до БД доходят INSERT'ы, и
        // EXCLUDE-констрейнт пропускает ровно один (остальным 23P01 → 409).
        val barrier = java.util.concurrent.CyclicBarrier(threads)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(threads)
        try {
            val statuses = (1..threads)
                .map { i ->
                    pool.submit<Int> {
                        barrier.await()
                        post("/bookings", request(id, start, name = "Гость $i")).status
                    }
                }
                .map { it.get() }

            assertThat(statuses.count { it == 201 }).isEqualTo(1)
            assertThat(statuses.count { it == 409 }).isEqualTo(threads - 1)
            assertThat(dsl.fetchCount(BOOKINGS)).isEqualTo(1)
        } finally {
            pool.shutdown()
        }
    }

    @Test
    fun `POST bookings 404 when event type not found`() {
        val response = post("/bookings", request(UUID.randomUUID(), slotStart()))

        assertThat(response.status).isEqualTo(404)
        assertThat(response.extractPath("errorCode")).isEqualTo("EVENT_TYPE_NOT_FOUND")
    }

    @Test
    fun `POST bookings 422 for past slot`() {
        val id = createEventType()
        val response = post("/bookings", request(id, slotStart(daysAhead = -1)))

        assertThat(response.status).isEqualTo(422)
        assertThat(response.extractPath("errorCode")).isEqualTo("SLOT_IN_PAST")
    }

    @Test
    fun `POST bookings 422 for slot beyond horizon`() {
        val id = createEventType()
        val response = post("/bookings", request(id, slotStart(daysAhead = props.horizonDays + 5L)))

        assertThat(response.status).isEqualTo(422)
        assertThat(response.extractPath("errorCode")).isEqualTo("SLOT_OUT_OF_HORIZON")
    }

    @Test
    fun `POST bookings 201 for slot exactly on horizon boundary`() {
        val id = createEventType()
        val response = post("/bookings", request(id, slotStart(daysAhead = props.horizonDays.toLong())))

        assertThat(response.status).isEqualTo(201)
    }

    @Test
    fun `POST bookings 422 when startAt not on grid`() {
        val id = createEventType()
        // 09:07 — не на 15-мин границе
        val offGrid = slotStart().plusMinutes(7)

        val response = post("/bookings", request(id, offGrid))

        assertThat(response.status).isEqualTo(422)
        assertThat(response.extractPath("errorCode")).isEqualTo("SLOT_NOT_ON_GRID")
    }

    @Test
    fun `POST bookings 422 when start outside working hours`() {
        val id = createEventType()
        // на сетке, но до начала рабочего дня
        val beforeHours = slotStart().minusHours(2)

        val response = post("/bookings", request(id, beforeHours))

        assertThat(response.status).isEqualTo(422)
        assertThat(response.extractPath("errorCode")).isEqualTo("SLOT_NOT_ON_GRID")
    }

    @Test
    fun `POST bookings 422 when interval does not fit working hours`() {
        // 120-мин встреча со стартом за 1 час до конца рабочего дня не влезает
        val id = createEventType(durationMinutes = 120)
        val nearEnd = slotStart().with(props.workEnd.minusHours(1))

        val response = post("/bookings", request(id, nearEnd))

        assertThat(response.status).isEqualTo(422)
        assertThat(response.extractPath("errorCode")).isEqualTo("SLOT_NOT_ON_GRID")
    }

    @Test
    fun `POST bookings 400 when meetingLink is not valid URI`() {
        val id = createEventType()
        val response = post("/bookings", request(id, slotStart(), meetingLink = "приходите"))

        assertThat(response.status).isEqualTo(400)
        assertThat(response.extractPath("errorCode")).isEqualTo("VALIDATION_FAILED")
    }

    @Test
    fun `POST bookings 400 when name is empty`() {
        val id = createEventType()
        val response = post("/bookings", request(id, slotStart(), name = ""))

        assertThat(response.status).isEqualTo(400)
        assertThat(response.extractPath("errorCode")).isEqualTo("VALIDATION_FAILED")
        assertThat(response.extractList("errors")).isNotEmpty()
    }

    @Test
    fun `POST bookings 201 when name is exactly minLength 1`() {
        val id = createEventType()
        val response = post("/bookings", request(id, slotStart(), name = "Я"))

        assertThat(response.status).isEqualTo(201)
    }

    @Test
    fun `POST bookings 201 when name is exactly maxLength 255`() {
        val id = createEventType()
        val response = post("/bookings", request(id, slotStart(), name = "A".repeat(255)))

        assertThat(response.status).isEqualTo(201)
    }

    @Test
    fun `POST bookings 400 when name exceeds maxLength 255`() {
        val id = createEventType()
        val response = post("/bookings", request(id, slotStart(), name = "A".repeat(256)))

        assertThat(response.status).isEqualTo(400)
        assertThat(response.extractPath("errorCode")).isEqualTo("VALIDATION_FAILED")
    }

    @Test
    fun `POST bookings 400 when meetingLink is missing`() {
        val id = createEventType()
        val response = post("/bookings", mapOf(
            "eventTypeId" to id.toString(),
            "startAt" to iso(slotStart()),
            "name" to "Анна",
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
        assertThat(response.bodyString()).contains("MALFORMED_REQUEST")
    }

    @Test
    fun `POST bookings 201 with null description`() {
        val id = createEventType()
        val response = post("/bookings", request(id, slotStart(), description = null))

        assertThat(response.status).isEqualTo(201)
    }

    @Test
    fun `POST bookings 400 when meetingLink is empty`() {
        val id = createEventType()
        val response = post("/bookings", request(id, slotStart(), meetingLink = ""))

        assertThat(response.status).isEqualTo(400)
        assertThat(response.extractPath("errorCode")).isEqualTo("VALIDATION_FAILED")
    }

    @Test
    fun `POST bookings 201 when meetingLink is exactly maxLength 2048`() {
        val id = createEventType()
        val prefix = "https://meet.example.com/"
        val longLink = prefix + "a".repeat(2048 - prefix.length)

        val response = post("/bookings", request(id, slotStart(), meetingLink = longLink))

        assertThat(response.status).isEqualTo(201)
    }

    @Test
    fun `POST bookings 400 when meetingLink exceeds maxLength 2048`() {
        val id = createEventType()
        val prefix = "https://meet.example.com/"
        val longLink = prefix + "a".repeat(2049 - prefix.length)

        val response = post("/bookings", request(id, slotStart(), meetingLink = longLink))

        assertThat(response.status).isEqualTo(400)
        assertThat(response.extractPath("errorCode")).isEqualTo("VALIDATION_FAILED")
    }

    @Test
    fun `POST bookings 201 when description is exactly maxLength 2000`() {
        val id = createEventType()
        val response = post("/bookings", request(id, slotStart(), description = "D".repeat(2000)))

        assertThat(response.status).isEqualTo(201)
    }

    @Test
    fun `POST bookings 400 when description exceeds maxLength 2000`() {
        val id = createEventType()
        val response = post("/bookings", request(id, slotStart(), description = "D".repeat(2001)))

        assertThat(response.status).isEqualTo(400)
        assertThat(response.extractPath("errorCode")).isEqualTo("VALIDATION_FAILED")
    }

    @Test
    fun `Error response follows ProblemDetail format`() {
        val response = post("/bookings", request(UUID.randomUUID(), slotStart()))

        assertThat(response.status).isEqualTo(404)
        assertThat(response.extractPath("status")).isEqualTo("404")
        assertThat(response.extractPath("errorCode")).isNotBlank()
        assertThat(response.extractPath("title")).isNotBlank()
        assertThat(response.extractPath("detail")).isNotBlank()
        assertThat(response.extractPath("type")).isEqualTo("about:blank")
    }
}
