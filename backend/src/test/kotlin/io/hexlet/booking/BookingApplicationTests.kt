package io.hexlet.booking

import io.hexlet.booking.db.Tables.APP_SETTINGS
import io.hexlet.booking.db.Tables.SLOTS
import io.hexlet.booking.service.TokenService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class BookingApplicationTests : AbstractIntegrationTest() {

    @Autowired lateinit var tokenService: TokenService

    @Test
    fun `context loads and Flyway migrates schema`() {
        val slotCount = dsl.fetchCount(SLOTS)
        assertThat(slotCount).isGreaterThan(0)
    }

    @Test
    fun `owner token is created on startup`() {
        val row = dsl.selectFrom(APP_SETTINGS).fetchOne()
        assertThat(row).isNotNull
        assertThat(row!!.token).isNotBlank()
        assertThat(row.token).isEqualTo(tokenService.currentToken())
    }

    @Test
    fun `slots generated for horizon days`() {
        val count = dsl.fetchCount(SLOTS)
        assertThat(count).isGreaterThan(0)
    }

    @Test
    fun `actuator health returns UP`() {
        val response = get("/actuator/health")

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
