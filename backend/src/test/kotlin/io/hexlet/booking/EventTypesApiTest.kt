package io.hexlet.booking

import io.hexlet.booking.service.TokenService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class EventTypesApiTest : AbstractIntegrationTest() {

    @Autowired lateinit var tokenService: TokenService

    private fun token() = tokenService.currentToken()

    private fun validRequest(durationMinutes: Int = 30) = mapOf(
        "name" to "Intro call",
        "description" to "Знакомство",
        "durationMinutes" to durationMinutes,
    )

    @Test
    fun `POST admin event-types 201 creates event type`() {
        val response = postAdmin(token(), "/admin/event-types", validRequest())

        assertThat(response.status).isEqualTo(201)
        assertThat(response.extractPath("id")).isNotBlank()
        assertThat(response.extractPath("name")).isEqualTo("Intro call")
        assertThat(response.extractPath("durationMinutes")?.toInt()).isEqualTo(30)
    }

    @Test
    fun `POST admin event-types 401 without token`() {
        val response = post("/admin/event-types", validRequest())

        assertThat(response.status).isEqualTo(401)
        assertThat(response.extractPath("errorCode")).isEqualTo("UNAUTHORIZED")
    }

    @Test
    fun `POST admin event-types 400 when duration below 15`() {
        val response = postAdmin(token(), "/admin/event-types", validRequest(durationMinutes = 10))

        assertThat(response.status).isEqualTo(400)
        assertThat(response.extractPath("errorCode")).isEqualTo("VALIDATION_FAILED")
    }

    @Test
    fun `POST admin event-types 400 when duration above 120`() {
        val response = postAdmin(token(), "/admin/event-types", validRequest(durationMinutes = 150))

        assertThat(response.status).isEqualTo(400)
        assertThat(response.extractPath("errorCode")).isEqualTo("VALIDATION_FAILED")
    }

    @Test
    fun `POST admin event-types 400 when duration not multiple of 15`() {
        val response = postAdmin(token(), "/admin/event-types", validRequest(durationMinutes = 20))

        assertThat(response.status).isEqualTo(400)
        assertThat(response.extractPath("errorCode")).isEqualTo("VALIDATION_FAILED")
        val errors = response.extractList("errors")
        assertThat(errors.first().get("field")?.asText()).isEqualTo("durationMinutes")
    }

    @Test
    fun `POST admin event-types 400 when name is empty`() {
        val response = postAdmin(token(), "/admin/event-types", mapOf(
            "name" to "",
            "durationMinutes" to 30,
        ))

        assertThat(response.status).isEqualTo(400)
        assertThat(response.extractPath("errorCode")).isEqualTo("VALIDATION_FAILED")
    }

    @Test
    fun `GET event-types returns created types`() {
        createEventType(durationMinutes = 30, name = "Intro call")
        createEventType(durationMinutes = 60, name = "Demo")

        val response = get("/event-types")

        assertThat(response.status).isEqualTo(200)
        assertThat(response.jsonBody().size()).isEqualTo(2)
    }

    @Test
    fun `GET admin event-types 401 without token`() {
        val response = get("/admin/event-types")

        assertThat(response.status).isEqualTo(401)
    }

    @Test
    fun `GET admin event-types 200 lists types`() {
        createEventType()

        val response = getAdmin(token(), "/admin/event-types")

        assertThat(response.status).isEqualTo(200)
        assertThat(response.jsonBody().size()).isEqualTo(1)
    }
}
