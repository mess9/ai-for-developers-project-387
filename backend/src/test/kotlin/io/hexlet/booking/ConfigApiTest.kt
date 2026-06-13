package io.hexlet.booking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConfigApiTest : AbstractIntegrationTest() {

    @Test
    fun `GET config returns 200 with correct fields`() {
        val response = get("/config")

        assertThat(response.status).isEqualTo(200)
        val body = response.jsonBodyString()

        assertThat(body).contains("\"ownerTimeZone\"")
        assertThat(body).contains("\"workingHours\"")
        assertThat(body).contains("\"gridMinutes\"")
        assertThat(body).contains("\"horizonDays\"")
    }

    @Test
    fun `GET config ownerTimeZone matches configuration`() {
        val response = get("/config")

        val tz = response.extractPath("ownerTimeZone")

        assertThat(tz).isEqualTo(props.ownerTz)
    }

    @Test
    fun `GET config gridMinutes matches configuration`() {
        val response = get("/config")

        val gridMinutes = response.extractPath("gridMinutes")?.toInt()

        assertThat(gridMinutes).isEqualTo(props.gridMinutes)
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
