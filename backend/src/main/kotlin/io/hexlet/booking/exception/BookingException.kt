package io.hexlet.booking.exception

sealed class BookingException(msg: String) : RuntimeException(msg)

class SlotNotFoundException       : BookingException("Slot not found")
class BookingNotFoundException    : BookingException("Booking not found")
class SlotAlreadyBookedException  : BookingException("Slot already booked")
class SlotInPastException         : BookingException("Slot is in the past")
class SlotOutOfHorizonException   : BookingException("Slot is beyond booking horizon")
class SlotNotOnGridException      : BookingException("Slot startAt does not match the grid")
class InvalidMeetingLinkException : BookingException("Invalid meeting link format")
