package io.hexlet.booking.controller

import io.hexlet.booking.api.ConfigApi
import io.hexlet.booking.config.BookingProperties
import io.hexlet.booking.model.Config
import io.hexlet.booking.model.WorkingHours
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class ConfigController(private val props: BookingProperties) : ConfigApi {

    override fun getConfig(): ResponseEntity<Config> =
        ResponseEntity.ok(
            Config(
                ownerTimeZone = props.ownerTz,
                workingHours  = WorkingHours(
                    start = props.workStart.toString(),
                    end   = props.workEnd.toString(),
                ),
                slotMinutes  = props.slotMinutes,
                horizonDays  = props.horizonDays,
            )
        )
}
