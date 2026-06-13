package io.hexlet.booking.api.controller

import io.hexlet.booking.api.ConfigApi
import io.hexlet.booking.config.BookingProperties
import io.hexlet.booking.model.Config
import io.hexlet.booking.model.WorkingHours
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.format.DateTimeFormatter

private val HH_MM = DateTimeFormatter.ofPattern("HH:mm")

@RestController
@RequestMapping("/api/v1")
class ConfigController(private val props: BookingProperties) : ConfigApi {

    override fun getConfig(): ResponseEntity<Config> =
        ResponseEntity.ok(
            Config(
                ownerTimeZone = props.ownerTz,
                workingHours = WorkingHours(
                    start = props.workStart.format(HH_MM),
                    end = props.workEnd.format(HH_MM),
                ),
                gridMinutes = props.gridMinutes,
                horizonDays = props.horizonDays,
            )
        )
}
