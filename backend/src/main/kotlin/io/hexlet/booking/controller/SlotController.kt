package io.hexlet.booking.controller

import io.hexlet.booking.api.SlotsApi
import io.hexlet.booking.exception.SlotNotFoundException
import io.hexlet.booking.model.Slot
import io.hexlet.booking.service.SlotService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

@RestController
class SlotController(private val slotService: SlotService) : SlotsApi {

    override fun getSlot(startAt: OffsetDateTime): ResponseEntity<Slot> {
        val slot    = slotService.findByStartAt(startAt) ?: throw SlotNotFoundException()
        val booking = slotService.bookingForSlot(slot.id)
        return ResponseEntity.ok(
            Slot(startAt = slot.startAt, endAt = slot.endAt,
                 status  = slotService.computeStatus(slot, booking, OffsetDateTime.now()))
        )
    }
}
