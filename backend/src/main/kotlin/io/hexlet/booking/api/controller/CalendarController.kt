package io.hexlet.booking.api.controller

import io.hexlet.booking.api.CalendarApi
import io.hexlet.booking.config.BookingProperties
import io.hexlet.booking.model.Calendar
import io.hexlet.booking.model.Day
import io.hexlet.booking.model.Slot
import io.hexlet.booking.model.SlotStatus
import io.hexlet.booking.service.SlotService
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

@Validated
@RestController
@RequestMapping("/api/v1")
class CalendarController(
    private val slotService: SlotService,
    private val props: BookingProperties,
) : CalendarApi {

    override fun getCalendar(month: String): ResponseEntity<Calendar> {
        val now = OffsetDateTime.now()
        val days = slotService.getSlotsForMonth(month).map { (date, pairs) ->
            val slots = pairs.map { (slot, booking) ->
                Slot(
                    startAt = slot.startAt,
                    endAt   = slot.endAt,
                    status  = slotService.computeStatus(slot, booking, now),
                )
            }
            Day(
                date         = date,
                hasFreeSlots = slots.any { it.status == SlotStatus.FREE },
                slots        = slots,
            )
        }
        return ResponseEntity.ok(
            Calendar(
                month         = month,
                ownerTimeZone = props.ownerTz,
                days          = days,
            )
        )
    }
}
