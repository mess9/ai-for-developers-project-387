package io.hexlet.booking.service

import io.hexlet.booking.db.Tables.EVENT_TYPES
import io.hexlet.booking.db.tables.records.EventTypesRecord
import io.hexlet.booking.exception.EventTypeNotFoundException
import io.hexlet.booking.exception.FieldValidationException
import io.hexlet.booking.model.EventType
import io.hexlet.booking.model.EventTypeRequest
import org.jooq.DSLContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class EventTypeService(private val dsl: DSLContext) {

    fun list(): List<EventType> =
        dsl.selectFrom(EVENT_TYPES)
            .orderBy(EVENT_TYPES.CREATED_AT)
            .fetch()
            .map { it.toModel() }

    /** Запись типа события (или null) — для расчёта доступности/брони. */
    fun findRecord(id: UUID): EventTypesRecord? =
        dsl.selectFrom(EVENT_TYPES).where(EVENT_TYPES.ID.eq(id)).fetchOne()

    fun require(id: UUID): EventTypesRecord =
        findRecord(id) ?: throw EventTypeNotFoundException()

    @Transactional
    fun create(request: EventTypeRequest): EventType {
        // Диапазон 15…120 ловит Bean Validation; кратность 15 — вручную (правило бэка).
        if (request.durationMinutes % 15 != 0) {
            throw FieldValidationException("durationMinutes", "must be a multiple of 15")
        }
        val saved = dsl.insertInto(EVENT_TYPES)
            .set(EVENT_TYPES.ID, UUID.randomUUID())
            .set(EVENT_TYPES.NAME, request.name)
            .set(EVENT_TYPES.DESCRIPTION, request.description)
            .set(EVENT_TYPES.DURATION_MINUTES, request.durationMinutes)
            .returning()
            .fetchOne()!!
        return saved.toModel()
    }

    private fun EventTypesRecord.toModel() = EventType(
        id = id,
        name = name,
        durationMinutes = durationMinutes,
        description = description,
    )
}
