package io.hexlet.booking.api.controller

import io.hexlet.booking.api.SlotsApi
import io.hexlet.booking.exception.SlotNotFoundException
import io.hexlet.booking.model.Slot
import io.hexlet.booking.service.SlotService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

@RestController
@RequestMapping("/api/v1")
class SlotsController(private val slotService: SlotService) : SlotsApi {

    override fun getSlot(startAt: OffsetDateTime): ResponseEntity<Slot> {
        val now    = OffsetDateTime.now()
        val record = slotService.findByStartAt(startAt) ?: throw SlotNotFoundException()
        return ResponseEntity.ok(
            Slot(
                startAt = record.startAt,
                endAt   = record.endAt,
                status  = slotService.computeStatus(record, slotService.bookingForSlot(record.id), now),
            )
        )
    }
}
