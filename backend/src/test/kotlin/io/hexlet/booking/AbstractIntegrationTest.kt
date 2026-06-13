package io.hexlet.booking

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.hexlet.booking.config.BookingProperties
import io.hexlet.booking.db.Tables.EVENT_TYPES
import org.http4k.client.ApacheClient
import org.http4k.core.*
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.util.*

private val postgres = PostgreSQLContainer("postgres:17-alpine").apply {
    start()
}

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractIntegrationTest {

    @LocalServerPort
    protected var port: Int = 0

    @Autowired
    protected lateinit var dsl: DSLContext

    @Autowired
    protected lateinit var props: BookingProperties

    protected lateinit var client: HttpHandler
    protected lateinit var baseUrl: String
    protected val mapper = ObjectMapper().registerModule(JavaTimeModule())

    @BeforeEach
    fun setupClient() {
        baseUrl = "http://localhost:$port/api/v1"
        client = ApacheClient()
    }

    @BeforeEach
    fun cleanDatabase() {
        dsl.deleteFrom(io.hexlet.booking.db.Tables.BOOKINGS).execute()
        dsl.deleteFrom(io.hexlet.booking.db.Tables.EVENT_TYPES).execute()
    }

    protected fun get(path: String): TestResponse {
        val request = Request(Method.GET, Uri.of("$baseUrl$path"))
        val response = client(request)
        return TestResponse(response, mapper)
    }

    protected fun getAdmin(token: String, path: String): TestResponse {
        val request = Request(Method.GET, Uri.of("$baseUrl$path"))
            .header("Authorization", "Bearer $token")
        val response = client(request)
        return TestResponse(response, mapper)
    }

    protected fun post(path: String, body: Any): TestResponse {
        val jsonBody = mapper.writeValueAsString(body)
        val request = Request(Method.POST, Uri.of("$baseUrl$path"))
            .header("Content-Type", "application/json")
            .body(jsonBody)
        val response = client(request)
        return TestResponse(response, mapper)
    }

    protected fun postAdmin(token: String, path: String, body: Any): TestResponse {
        val jsonBody = mapper.writeValueAsString(body)
        val request = Request(Method.POST, Uri.of("$baseUrl$path"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $token")
            .body(jsonBody)
        val response = client(request)
        return TestResponse(response, mapper)
    }

    protected fun deleteAdmin(token: String, path: String): TestResponse {
        val request = Request(Method.DELETE, Uri.of("$baseUrl$path"))
            .header("Authorization", "Bearer $token")
        val response = client(request)
        return TestResponse(response, mapper)
    }

    // ──── доменные хелперы ────────────────────────────────────────────────────

    /** Создаёт тип события напрямую в БД, возвращает его id. */
    protected fun createEventType(durationMinutes: Int = 30, name: String = "Intro call"): UUID {
        val id = UUID.randomUUID()
        dsl.insertInto(EVENT_TYPES)
            .set(EVENT_TYPES.ID, id)
            .set(EVENT_TYPES.NAME, name)
            .set(EVENT_TYPES.DESCRIPTION, "Описание")
            .set(EVENT_TYPES.DURATION_MINUTES, durationMinutes)
            .execute()
        return id
    }

    /**
     * Старт слота на сетке в рабочих часах: `daysAhead` дней вперёд от сегодня (в OWNER_TZ),
     * `slotIndex`-й шаг сетки от начала рабочего дня. По умолчанию — завтра в начале дня
     * (гарантированно будущее и в горизонте).
     */
    protected fun slotStart(daysAhead: Long = 1, slotIndex: Long = 0): OffsetDateTime =
        ZonedDateTime.of(
            LocalDate.now(props.zone).plusDays(daysAhead),
            props.workStart.plusMinutes(props.gridMinutes.toLong() * slotIndex),
            props.zone,
        ).toOffsetDateTime()

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }
}

data class TestResponse(
    private val response: Response,
    private val mapper: ObjectMapper
) {
    val status: Int get() = response.status.code

    fun jsonBodyString(): String = response.bodyString()

    fun jsonBody(): com.fasterxml.jackson.databind.JsonNode {
        return mapper.readTree(response.bodyString())
    }

    fun extractPath(path: String): String? {
        val json = jsonBody()
        val parts = path.trim('/').split(Regex("[./]"))
        var node: com.fasterxml.jackson.databind.JsonNode? = json
        for (part in parts) {
            if (part.isEmpty()) continue
            node = node?.get(part)
        }
        if (node == null || node.isNull) return null
        return node.asText()
    }

    fun extractList(path: String): List<com.fasterxml.jackson.databind.JsonNode> {
        val json = jsonBody()
        val parts = path.trim('/').split("/")
        var node: com.fasterxml.jackson.databind.JsonNode? = json
        for (part in parts) {
            node = node?.get(part)
        }
        return if (node != null && node.isArray) {
            node.toList()
        } else {
            emptyList()
        }
    }
}
