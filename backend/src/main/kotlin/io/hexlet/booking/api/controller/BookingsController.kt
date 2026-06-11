package io.hexlet.booking.api.controller

import io.hexlet.booking.api.BookingsApi
import io.hexlet.booking.model.BookingConfirmation
import io.hexlet.booking.model.BookingRequest
import io.hexlet.booking.service.BookingService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class BookingsController(private val bookingService: BookingService) : BookingsApi {

    override fun createBooking(bookingRequest: BookingRequest): ResponseEntity<BookingConfirmation> =
        ResponseEntity.status(HttpStatus.CREATED).body(bookingService.createBooking(bookingRequest))
}
