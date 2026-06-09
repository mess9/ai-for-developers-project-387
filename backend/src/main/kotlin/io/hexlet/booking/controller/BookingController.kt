package io.hexlet.booking.controller

import io.hexlet.booking.api.BookingsApi
import io.hexlet.booking.model.BookingConfirmation
import io.hexlet.booking.model.BookingRequest
import io.hexlet.booking.service.BookingService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class BookingController(private val bookingService: BookingService) : BookingsApi {

    override fun createBooking(bookingRequest: BookingRequest): ResponseEntity<BookingConfirmation> =
        ResponseEntity.status(201).body(bookingService.createBooking(bookingRequest))
}
