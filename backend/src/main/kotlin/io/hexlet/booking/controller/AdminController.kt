package io.hexlet.booking.controller

import io.hexlet.booking.api.AdminApi
import io.hexlet.booking.config.BookingProperties
import io.hexlet.booking.exception.SlotNotFoundException
import io.hexlet.booking.model.AdminCalendar
import io.hexlet.booking.model.AdminDay
import io.hexlet.booking.model.AdminSlot
import io.hexlet.booking.model.Booking
import io.hexlet.booking.service.BookingService
import io.hexlet.booking.service.SlotService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.util.*

@RestController
class AdminController(
    private val slotService: SlotService,
    private val bookingService: BookingService,
    private val props: BookingProperties,
) : AdminApi {

    override fun getAdminCalendar(month: String): ResponseEntity<AdminCalendar> {
        val now  = OffsetDateTime.now()
        val days = slotService.getSlotsForMonth(month).map { (date, pairs) ->
            val slots = pairs.map { (slot, booking) ->
                AdminSlot(
                    startAt = slot.startAt,
                    endAt   = slot.endAt,
                    status  = slotService.computeStatus(slot, booking, now),
                    booking = booking?.let {
                        Booking(id          = it.id,
                                startAt     = slot.startAt,
                                name        = it.name,
                                meetingLink = it.meetingLink,
                                description = it.description,
                                createdAt   = it.createdAt)
                    }
                )
            }
            AdminDay(date        = date,
                     hasFreeSlots = slots.any { it.status.value == "FREE" },
                     slots        = slots)
        }
        return ResponseEntity.ok(
            AdminCalendar(month = month, ownerTimeZone = props.ownerTz, days = days)
        )
    }

    override fun getAdminSlot(startAt: OffsetDateTime): ResponseEntity<AdminSlot> {
        val now     = OffsetDateTime.now()
        val slot    = slotService.findByStartAt(startAt) ?: throw SlotNotFoundException()
        val booking = slotService.bookingForSlot(slot.id)
        return ResponseEntity.ok(
            AdminSlot(
                startAt = slot.startAt,
                endAt   = slot.endAt,
                status  = slotService.computeStatus(slot, booking, now),
                booking = booking?.let {
                    Booking(id          = it.id,
                            startAt     = slot.startAt,
                            name        = it.name,
                            meetingLink = it.meetingLink,
                            description = it.description,
                            createdAt   = it.createdAt)
                }
            )
        )
    }

    override fun cancelBooking(id: UUID): ResponseEntity<Unit> {
        bookingService.cancelBooking(id)
        return ResponseEntity.noContent().build()
    }
}
