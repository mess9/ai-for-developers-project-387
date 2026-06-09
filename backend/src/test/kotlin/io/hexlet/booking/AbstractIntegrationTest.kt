package io.hexlet.booking

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.http4k.client.ApacheClient
import org.http4k.core.*
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer

private val postgres: PostgreSQLContainer<*> =
    PostgreSQLContainer<Nothing>("postgres:17-alpine").apply {
        start()
    }

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractIntegrationTest {

    @LocalServerPort
    protected var port: Int = 0

    @Autowired
    protected lateinit var dsl: DSLContext

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
