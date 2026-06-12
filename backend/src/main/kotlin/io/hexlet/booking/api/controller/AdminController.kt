package io.hexlet.booking.api.controller

import io.hexlet.booking.api.AdminApi
import io.hexlet.booking.model.Booking
import io.hexlet.booking.model.EventType
import io.hexlet.booking.model.EventTypeRequest
import io.hexlet.booking.service.BookingService
import io.hexlet.booking.service.EventTypeService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/api/v1")
class AdminController(
    private val eventTypeService: EventTypeService,
    private val bookingService: BookingService,
) : AdminApi {

    override fun createEventType(eventTypeRequest: EventTypeRequest): ResponseEntity<EventType> =
        ResponseEntity.status(HttpStatus.CREATED).body(eventTypeService.create(eventTypeRequest))

    override fun listAdminEventTypes(): ResponseEntity<List<EventType>> =
        ResponseEntity.ok(eventTypeService.list())

    override fun listBookings(): ResponseEntity<List<Booking>> =
        ResponseEntity.ok(bookingService.listUpcoming())

    override fun cancelBooking(id: UUID): ResponseEntity<Unit> {
        bookingService.cancelBooking(id)
        return ResponseEntity.noContent().build()
    }
}
