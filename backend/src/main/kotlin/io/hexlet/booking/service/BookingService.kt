package io.hexlet.booking.service

import io.hexlet.booking.config.BookingProperties
import io.hexlet.booking.db.Tables.BOOKINGS
import io.hexlet.booking.exception.*
import io.hexlet.booking.model.BookingConfirmation
import io.hexlet.booking.model.BookingRequest
import org.jooq.DSLContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.time.OffsetDateTime
import java.util.*

@Service
class BookingService(
    private val dsl: DSLContext,
    private val slotService: SlotService,
    private val props: BookingProperties,
) {
    @Transactional
    fun createBooking(request: BookingRequest): BookingConfirmation {
        if (!isValidUri(request.meetingLink)) {
            throw InvalidMeetingLinkException()
        }
        
        // Проверка на соответствие сетке: секунды и наносекунды должны быть 0
        if (request.startAt.second != 0 || request.startAt.nano != 0) {
            throw SlotNotOnGridException()
        }
        
        val now  = OffsetDateTime.now()
        val slot = slotService.findByStartAt(request.startAt)
            ?: throw SlotNotFoundException()

        if (slot.startAt.isBefore(now))
            throw SlotInPastException()
        if (slot.startAt.isAfter(now.plusDays(props.horizonDays.toLong())))
            throw SlotOutOfHorizonException()

        if (slotService.bookingForSlot(slot.id) != null)
            throw SlotAlreadyBookedException()

        val saved = dsl.insertInto(BOOKINGS)
            .set(BOOKINGS.ID,           UUID.randomUUID())
            .set(BOOKINGS.SLOT_ID,      slot.id)
            .set(BOOKINGS.NAME,         request.name)
            .set(BOOKINGS.MEETING_LINK, request.meetingLink)
            .set(BOOKINGS.DESCRIPTION,  request.description)
            .set(BOOKINGS.CREATED_AT,   OffsetDateTime.now())
            .returning()
            .fetchOne()!!

        return BookingConfirmation(
            startAt     = slot.startAt,
            endAt       = slot.endAt,
            name        = saved.name,
            meetingLink = saved.meetingLink,
            description = saved.description,
            createdAt   = saved.createdAt,
        )
    }

    private fun isValidUri(link: String): Boolean =
        try {
            URI(link)
            link.startsWith("http://") || link.startsWith("https://")
        } catch (_: Exception) {
            false
        }

    @Transactional
    fun cancelBooking(id: UUID) {
        val deleted = dsl.deleteFrom(BOOKINGS)
            .where(BOOKINGS.ID.eq(id))
            .execute()
        if (deleted == 0) throw BookingNotFoundException()
    }
}
