package io.hexlet.booking.exception

import io.hexlet.booking.model.ErrorCode
import io.hexlet.booking.model.ValidationError
import jakarta.validation.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import io.hexlet.booking.model.ProblemDetail as ApiProblemDetail

@RestControllerAdvice
class GlobalExceptionHandler {

    // ──── 404 ────────────────────────────────────────────────────────────────

    @ExceptionHandler(SlotNotFoundException::class)
    fun slotNotFound() = problem(404, ErrorCode.SLOT_NOT_FOUND, "Слот не найден")

    @ExceptionHandler(BookingNotFoundException::class)
    fun bookingNotFound() = problem(404, ErrorCode.BOOKING_NOT_FOUND, "Бронь не найдена")

    // ──── 409 ────────────────────────────────────────────────────────────────

    @ExceptionHandler(SlotAlreadyBookedException::class)
    fun slotBooked() = problem(409, ErrorCode.SLOT_ALREADY_BOOKED, "Слот уже занят")

    /** Backstop: гонка при двойном бронировании пробивается через UNIQUE-констрейнт */
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun integrityViolation(ex: DataIntegrityViolationException): ResponseEntity<ApiProblemDetail> {
        val msg = ex.message?.lowercase() ?: ""
        return if ("bookings_slot_id_uq" in msg || "slot_id" in msg)
            problem(409, ErrorCode.SLOT_ALREADY_BOOKED, "Слот уже занят")
        else
            problem(409, ErrorCode.SLOT_ALREADY_BOOKED, "Конфликт данных")
    }

    // ──── 422 ────────────────────────────────────────────────────────────────

    @ExceptionHandler(SlotInPastException::class)
    fun slotInPast() = problem(422, ErrorCode.SLOT_IN_PAST, "Слот находится в прошлом")

    @ExceptionHandler(SlotOutOfHorizonException::class)
    fun slotOutOfHorizon() = problem(422, ErrorCode.SLOT_OUT_OF_HORIZON, "Слот за горизонтом бронирования")

    @ExceptionHandler(SlotNotOnGridException::class)
    fun slotNotOnGrid() = problem(422, ErrorCode.SLOT_NOT_ON_GRID, "Старт слота не соответствует сетке")

    @ExceptionHandler(InvalidMeetingLinkException::class)
    fun invalidMeetingLink() = problem(400, ErrorCode.VALIDATION_FAILED, "Неверный формат ссылки на встречу", 
        listOf(ValidationError(field = "meetingLink", message = "must be a valid URI")))

    // ──── 400 ────────────────────────────────────────────────────────────────

    @ExceptionHandler(ConstraintViolationException::class)
    fun constraintViolation(ex: ConstraintViolationException): ResponseEntity<ApiProblemDetail> {
        val errors = ex.constraintViolations.map { cv ->
            ValidationError(field = cv.propertyPath.toString(), message = cv.message)
        }
        return problem(400, ErrorCode.VALIDATION_FAILED, "Ошибка валидации параметров", errors)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validationFailed(ex: MethodArgumentNotValidException): ResponseEntity<ApiProblemDetail> {
        val errors = ex.bindingResult.fieldErrors.map { fe ->
            ValidationError(field = fe.field, message = fe.defaultMessage ?: "invalid value")
        }
        return problem(400, ErrorCode.VALIDATION_FAILED, "Ошибка валидации полей", errors)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun malformedJson() = problem(400, ErrorCode.MALFORMED_REQUEST, "Не удалось разобрать запрос")

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun missingParam(ex: MissingServletRequestParameterException) =
        problem(400, ErrorCode.MALFORMED_REQUEST, "Обязательный параметр отсутствует: ${ex.parameterName}")

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun typeMismatch(ex: MethodArgumentTypeMismatchException) =
        problem(400, ErrorCode.MALFORMED_REQUEST, "Неверный формат параметра: ${ex.name}")

    // ──── helpers ────────────────────────────────────────────────────────────

    private fun problem(
        status: Int,
        code: ErrorCode,
        detail: String,
        errors: List<ValidationError>? = null,
    ): ResponseEntity<ApiProblemDetail> =
        ResponseEntity
            .status(status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(ApiProblemDetail(
                type = "about:blank",
                title = when (code) {
                    ErrorCode.VALIDATION_FAILED -> "Validation Failed"
                    ErrorCode.MALFORMED_REQUEST -> "Malformed Request"
                    ErrorCode.SLOT_NOT_FOUND -> "Slot Not Found"
                    ErrorCode.BOOKING_NOT_FOUND -> "Booking Not Found"
                    ErrorCode.SLOT_ALREADY_BOOKED -> "Slot Already Booked"
                    ErrorCode.SLOT_IN_PAST -> "Slot In Past"
                    ErrorCode.SLOT_OUT_OF_HORIZON -> "Slot Out Of Horizon"
                    ErrorCode.SLOT_NOT_ON_GRID -> "Slot Not On Grid"
                    ErrorCode.UNAUTHORIZED -> "Unauthorized"
                },
                status = status,
                errorCode = code,
                detail = detail,
                errors = errors
            ))
}
