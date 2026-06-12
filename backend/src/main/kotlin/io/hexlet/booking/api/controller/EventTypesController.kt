package io.hexlet.booking.api.controller

import io.hexlet.booking.api.EventTypesApi
import io.hexlet.booking.model.Availability
import io.hexlet.booking.model.EventType
import io.hexlet.booking.service.AvailabilityService
import io.hexlet.booking.service.EventTypeService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/api/v1")
class EventTypesController(
    private val eventTypeService: EventTypeService,
    private val availabilityService: AvailabilityService,
) : EventTypesApi {

    override fun listEventTypes(): ResponseEntity<List<EventType>> =
        ResponseEntity.ok(eventTypeService.list())

    override fun getAvailability(id: UUID): ResponseEntity<Availability> =
        ResponseEntity.ok(availabilityService.forEventType(id))
}
