package io.hexlet.booking.api.controller

import io.hexlet.booking.api.AdminApi
import io.hexlet.booking.config.BookingProperties
import io.hexlet.booking.exception.SlotNotFoundException
import io.hexlet.booking.model.*
import io.hexlet.booking.service.BookingService
import io.hexlet.booking.service.SlotService
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.util.*

@Validated
@RestController
@RequestMapping("/api/v1")
class AdminController(
    private val bookingService: BookingService,
    private val slotService: SlotService,
    private val props: BookingProperties,
) : AdminApi {

    override fun cancelBooking(id: UUID): ResponseEntity<Unit> {
        bookingService.cancelBooking(id)
        return ResponseEntity.noContent().build()
    }

    override fun getAdminCalendar(month: String): ResponseEntity<AdminCalendar> {
        val now  = OffsetDateTime.now()
        val days = slotService.getSlotsForMonth(month).map { (date, pairs) ->
            val adminSlots = pairs.map { (slot, booking) ->
                val status = slotService.computeStatus(slot, booking, now)
                AdminSlot(
                    startAt = slot.startAt,
                    endAt   = slot.endAt,
                    status  = status,
                    booking = booking?.let { b ->
                        Booking(
                            id          = b.id,
                            startAt     = slot.startAt,
                            name        = b.name,
                            meetingLink = b.meetingLink,
                            description = b.description,
                            createdAt   = b.createdAt,
                        )
                    },
                )
            }
            AdminDay(
                date         = date,
                hasFreeSlots = adminSlots.any { it.status == SlotStatus.FREE },
                slots        = adminSlots,
            )
        }
        return ResponseEntity.ok(
            AdminCalendar(
                month         = month,
                ownerTimeZone = props.ownerTz,
                days          = days,
            )
        )
    }

    override fun getAdminSlot(startAt: OffsetDateTime): ResponseEntity<AdminSlot> {
        val now    = OffsetDateTime.now()
        val record = slotService.findByStartAt(startAt) ?: throw SlotNotFoundException()
        val booking = slotService.bookingForSlot(record.id)
        val status  = slotService.computeStatus(record, booking, now)
        return ResponseEntity.ok(
            AdminSlot(
                startAt = record.startAt,
                endAt   = record.endAt,
                status  = status,
                booking = booking?.let { b ->
                    Booking(
                        id          = b.id,
                        startAt     = record.startAt,
                        name        = b.name,
                        meetingLink = b.meetingLink,
                        description = b.description,
                        createdAt   = b.createdAt,
                    )
                },
            )
        )
    }
}
