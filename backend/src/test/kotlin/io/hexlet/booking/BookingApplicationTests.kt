package io.hexlet.booking

import io.hexlet.booking.db.Tables.APP_SETTINGS
import io.hexlet.booking.service.TokenService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class BookingApplicationTests : AbstractIntegrationTest() {

    @Autowired lateinit var tokenService: TokenService

    @Test
    fun `owner token is created on startup`() {
        val row = dsl.selectFrom(APP_SETTINGS).fetchOne()
        assertThat(row).isNotNull
        assertThat(row!!.token).isNotBlank()
        assertThat(row.token).isEqualTo(tokenService.currentToken())
    }

    @Test
    fun `actuator health returns UP`() {
        val response = TestResponse(
            client(org.http4k.core.Request(org.http4k.core.Method.GET, org.http4k.core.Uri.of("http://localhost:$port/actuator/health"))),
            mapper,
        )
        assertThat(response.status).isEqualTo(200)
    }

    @Test
    fun `owner token persists across restarts`() {
        val tokenBefore = tokenService.currentToken()

        val row = dsl.selectFrom(APP_SETTINGS).fetchOne()
        assertThat(row).isNotNull
        assertThat(row!!.token).isEqualTo(tokenBefore)
    }
}
