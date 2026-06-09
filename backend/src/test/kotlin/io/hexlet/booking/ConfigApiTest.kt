package io.hexlet.booking

import io.hexlet.booking.config.BookingProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class ConfigApiTest : AbstractIntegrationTest() {

    @Autowired lateinit var props: BookingProperties

    @Test
    fun `GET config returns 200 with correct fields`() {
        val response = get("/config")

        assertThat(response.status).isEqualTo(200)
        val body = response.jsonBodyString()

        assertThat(body).contains("\"ownerTimeZone\"")
        assertThat(body).contains("\"workingHours\"")
        assertThat(body).contains("\"slotMinutes\"")
        assertThat(body).contains("\"horizonDays\"")
    }

    @Test
    fun `GET config ownerTimeZone matches configuration`() {
        val response = get("/config")

        val tz = response.extractPath("ownerTimeZone")

        assertThat(tz).isEqualTo(props.ownerTz)
    }

    @Test
    fun `GET config slotMinutes matches configuration`() {
        val response = get("/config")

        val slotMinutes = response.extractPath("slotMinutes")?.toInt()

        assertThat(slotMinutes).isEqualTo(props.slotMinutes)
    }

    @Test
    fun `GET config workingHours start and end match configuration`() {
        val response = get("/config")

        val whStart = response.extractPath("workingHours.start")
        val whEnd = response.extractPath("workingHours.end")

        assertThat(whStart).isEqualTo(props.workStart.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))
        assertThat(whEnd).isEqualTo(props.workEnd.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))
    }

    @Test
    fun `GET config horizonDays matches configuration`() {
        val response = get("/config")

        val horizonDays = response.extractPath("horizonDays")?.toInt()

        assertThat(horizonDays).isEqualTo(props.horizonDays)
    }
}
