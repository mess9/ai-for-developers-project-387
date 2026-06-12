package io.hexlet.booking.exception

import io.hexlet.booking.model.ValidationError

sealed class BookingException(msg: String) : RuntimeException(msg)

class EventTypeNotFoundException  : BookingException("Event type not found")
class BookingNotFoundException    : BookingException("Booking not found")
class SlotAlreadyBookedException  : BookingException("Slot already booked")
class SlotInPastException         : BookingException("Slot is in the past")
class SlotOutOfHorizonException   : BookingException("Slot is beyond booking horizon")
class SlotNotOnGridException      : BookingException("Slot startAt does not match the grid")

/** Провал доменной валидации полей (ручные проверки): ссылка, кратность длительности и т.п. */
class FieldValidationException(val errors: List<ValidationError>) :
    BookingException("Field validation failed") {
    constructor(field: String, message: String) : this(listOf(ValidationError(field, message)))
}
