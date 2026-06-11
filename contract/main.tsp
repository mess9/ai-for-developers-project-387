import "@typespec/http";
import "@typespec/openapi";

using Http;
using OpenAPI;

/**
 * API сервиса «Запись на звонок» — упрощённая вариация Cal.com.
 *
 * Владелец (один на инсталляцию) заводит **типы событий** (например «Intro call»,
 * 30 мин), анонимный гость выбирает тип, видит свободные слоты в ближайшие 14 дней
 * и бронирует один из них. Регистрации и авторизации гостей нет.
 *
 * ## Типы событий и слоты
 * - У каждого **типа события** своя длительность (кратна 15 мин, от 15 до 120).
 * - Доступность **не хранится** в БД, а вычисляется на лету: рабочие часы режутся
 *   на сетку с шагом 15 мин; для типа длительностью `D` слот стартует на каждой
 *   15-мин границе и существует, если целиком влезает в рабочие часы (`start+D ≤ end`)
 *   и не пересекается ни с одной существующей бронью.
 * - **Слот идентифицируется парой** (`eventTypeId`, `startAt`): без типа неизвестна
 *   длительность, а значит и проверяемый интервал `[startAt, startAt+D)`.
 *
 * ## Правило занятости
 * На одно и то же время нельзя создать две брони — **даже разных типов**. Формально:
 * интервалы броней не пересекаются. Бронь 09:00–10:00 блокирует любой слот любого
 * типа, чей интервал её задевает.
 *
 * ## Идентификаторы
 * - **Тип события** — `id` (UUID, генерит сервер).
 * - **Бронь** — `id` (UUID).
 *
 * ## Время
 * Все моменты времени на проводе — ISO-8601 `OffsetDateTime` (со смещением).
 * Часовой пояс владельца отдаётся отдельным полем (`ownerTimeZone`, IANA-имя),
 * т.к. смещение не несёт имени зоны. Группировка слотов по дням — в часовом
 * поясе владельца.
 *
 * ## Ошибки
 * Ошибки — RFC 9457 ProblemDetail (`application/problem+json`) с машиночитаемым
 * `errorCode` и (для валидации) массивом `errors[]`.
 */
@service(#{ title: "Booking Service API" })
@info(#{ version: "2.0.0" })
@server("/api/v1", "Базовый путь API (версия 1)")
namespace BookingService;

// ──────────────────────────────────────────────
// Конфигурация
// ──────────────────────────────────────────────

model Config {
  /** IANA-имя часового пояса владельца. */
  ownerTimeZone: string;

  workingHours: WorkingHours;

  /** Шаг сетки в минутах (на каких границах могут начинаться слоты). Фиксирован — 15. */
  gridMinutes: int32;

  /** На сколько дней вперёд (от сегодня) доступно бронирование. Окно — 14 дней. */
  horizonDays: int32;
}

/**
 * Рабочие часы владельца (локальное время в его часовом поясе).
 * Границы задаются **только целыми часами** (минуты всегда `00`).
 */
model WorkingHours {
  /** Начало рабочего дня, формат `HH:00` (целый час). */
  @pattern("^([01]\\d|2[0-3]):00$")
  start: string;

  /** Конец рабочего дня, формат `HH:00` (целый час). */
  @pattern("^([01]\\d|2[0-3]):00$")
  end: string;
}

// ──────────────────────────────────────────────
// Типы событий
// ──────────────────────────────────────────────

/** Тип события (вид брони): название, описание и длительность. */
model EventType {
  /** Идентификатор типа события (генерит сервер). */
  @format("uuid")
  id: string;

  /** Название типа события (видно гостю). */
  name: string;

  /** Описание (видно гостю, опционально). */
  description?: string | null;

  /** Длительность встречи в минутах. Кратна 15, в диапазоне 15…120. */
  durationMinutes: int32;
}

/**
 * Запрос на создание типа события (владелец).
 * `durationMinutes` должна быть кратна 15 — это правило проверяет бэк
 * (диапазон ограничен здесь, кратность — на сервере).
 */
model EventTypeRequest {
  /** Название типа события. */
  @minLength(1)
  @maxLength(255)
  name: string;

  /** Описание (опционально). */
  @maxLength(2000)
  description?: string | null;

  /** Длительность встречи в минутах: кратна 15, от 15 до 120. */
  @minValue(15)
  @maxValue(120)
  durationMinutes: int32;
}

// ──────────────────────────────────────────────
// Доступность (вычисляемые слоты по типу события)
// ──────────────────────────────────────────────

/** Свободный слот: интервал, на который можно создать бронь. */
model AvailableSlot {
  /** Момент начала слота (часть идентификатора слота). */
  startAt: offsetDateTime;

  /** Момент окончания слота (`startAt + durationMinutes`). */
  endAt: offsetDateTime;
}

/** День окна доступности со свободными слотами выбранного типа события. */
model AvailabilityDay {
  /** Дата дня в часовом поясе владельца. */
  date: plainDate;

  /** Есть ли в этот день хотя бы один свободный слот (для подсветки в сетке). */
  hasFreeSlots: boolean;

  /** Только свободные старты этого дня (занятые/прошлые не включаются). */
  slots: AvailableSlot[];
}

/**
 * Доступность одного типа события на окно «сегодня…сегодня+`horizonDays`».
 * Содержит все дни окна (день без свободных слотов имеет пустой `slots`).
 */
model Availability {
  /** Тип события, для которого построена доступность. */
  @format("uuid")
  eventTypeId: string;

  /** Длительность типа в минутах (для отображения и расчётов на клиенте). */
  durationMinutes: int32;

  /** Часовой пояс владельца (границы дней считаются в нём). */
  ownerTimeZone: string;

  days: AvailabilityDay[];
}

// ──────────────────────────────────────────────
// Брони
// ──────────────────────────────────────────────

/** Запрос на бронирование слота выбранного типа события. */
model BookingRequest {
  /** Тип события, на который записывается гость. */
  @format("uuid")
  eventTypeId: string;

  /** Начало слота (часть идентификатора слота). Длительность берётся из типа. */
  startAt: offsetDateTime;

  /** Имя гостя. */
  @minLength(1)
  @maxLength(255)
  name: string;

  /** Ссылка на встречу (обязательно). */
  @format("uri")
  @minLength(1)
  @maxLength(2048)
  meetingLink: string;

  /** Описание встречи (опционально). */
  @maxLength(2000)
  description?: string | null;
}

/**
 * Подтверждение брони, возвращаемое гостю. Идентификатор брони (`id`) не
 * раскрывается — отмена доступна только владельцу. Несёт название типа встречи.
 */
model BookingConfirmation {
  /** Название типа события, на который записан гость. */
  eventTypeName: string;

  startAt: offsetDateTime;
  endAt: offsetDateTime;
  name: string;

  @format("uri")
  meetingLink: string;

  description?: string | null;
  createdAt: offsetDateTime;
}

/**
 * Детали брони, видимые владельцу в списке предстоящих встреч.
 * Несёт и `eventTypeId`, и денормализованное `eventTypeName` (чтобы список
 * был читаем без дополнительного запроса). `endAt` хранится у брони — прошлые
 * встречи не «едут», даже если тип когда-нибудь изменят.
 */
model Booking {
  /** Идентификатор брони. */
  @format("uuid")
  id: string;

  /** Идентификатор типа события. */
  @format("uuid")
  eventTypeId: string;

  /** Название типа события (денормализовано для удобства отображения). */
  eventTypeName: string;

  startAt: offsetDateTime;
  endAt: offsetDateTime;

  /** Имя гостя. */
  name: string;

  /** Ссылка на встречу. */
  @format("uri")
  meetingLink: string;

  /** Описание встречи (опционально). */
  description?: string | null;

  /** Когда бронь создана. */
  createdAt: offsetDateTime;
}

// ──────────────────────────────────────────────
// Модель ошибок
// ──────────────────────────────────────────────

/**
 * Машиночитаемый код ошибки:
 * - `VALIDATION_FAILED` — провал валидации полей (см. `errors[]`), `400`;
 * - `MALFORMED_REQUEST` — битый JSON или неверный формат параметра, `400`;
 * - `EVENT_TYPE_NOT_FOUND` — тип события с таким `id` не найден, `404`;
 * - `BOOKING_NOT_FOUND` — бронь с таким `id` не найдена, `404`;
 * - `SLOT_ALREADY_BOOKED` — выбранный интервал пересекается с существующей бронью
 *   (в т.ч. другого типа), `409`;
 * - `SLOT_IN_PAST` — старт слота уже в прошлом, `422`;
 * - `SLOT_OUT_OF_HORIZON` — старт за пределами окна бронирования (14 дней), `422`;
 * - `SLOT_NOT_ON_GRID` — `startAt` не на 15-мин границе либо интервал не влезает
 *   в рабочие часы, `422`;
 * - `UNAUTHORIZED` — отсутствует или неверен токен владельца, `401`.
 */
enum ErrorCode {
  VALIDATION_FAILED,
  MALFORMED_REQUEST,
  EVENT_TYPE_NOT_FOUND,
  BOOKING_NOT_FOUND,
  SLOT_ALREADY_BOOKED,
  SLOT_IN_PAST,
  SLOT_OUT_OF_HORIZON,
  SLOT_NOT_ON_GRID,
  UNAUTHORIZED,
}

/** Ошибка валидации конкретного поля. */
model ValidationError {
  /** Имя поля, не прошедшего валидацию. */
  field: string;

  /** Человекочитаемое сообщение. */
  message: string;
}

/**
 * Ошибка в формате RFC 9457 (`application/problem+json`), расширенная полями
 * `errorCode` и `errors[]`.
 */
model ProblemDetail {
  /** URI типа проблемы. */
  @format("uri")
  type?: string;

  /** Краткое название проблемы. */
  title?: string;

  /** HTTP-статус. */
  status: int32;

  /** Человекочитаемое описание. */
  detail?: string;

  /** URI запроса, вызвавшего ошибку. */
  @format("uri")
  instance?: string;

  errorCode: ErrorCode;

  /** Детали валидации по полям (для `VALIDATION_FAILED`). */
  errors?: ValidationError[];
}

// ──────────────────────────────────────────────
// Ответы-ошибки (RFC 9457, application/problem+json)
// ──────────────────────────────────────────────

/** Битый запрос (невалидный JSON, неверный формат параметра, провал валидации полей). */
@error
model BadRequestResponse {
  @statusCode statusCode: 400;
  @header contentType: "application/problem+json";
  @body body: ProblemDetail;
}

/** Отсутствует или неверен токен владельца. */
@error
model UnauthorizedResponse {
  @statusCode statusCode: 401;
  @header contentType: "application/problem+json";
  @body body: ProblemDetail;
}

/** Ресурс не найден (тип события или бронь). */
@error
model NotFoundResponse {
  @statusCode statusCode: 404;
  @header contentType: "application/problem+json";
  @body body: ProblemDetail;
}

/** Выбранный интервал пересекается с существующей бронью. */
@error
model ConflictResponse {
  @statusCode statusCode: 409;
  @header contentType: "application/problem+json";
  @body body: ProblemDetail;
}

/** Семантически некорректный запрос (прошлое, вне горизонта, старт не из сетки). */
@error
model UnprocessableEntityResponse {
  @statusCode statusCode: 422;
  @header contentType: "application/problem+json";
  @body body: ProblemDetail;
}

// ──────────────────────────────────────────────
// Эндпоинты — публичные
// ──────────────────────────────────────────────

/**
 * Параметры, нужные клиенту для построения сетки: часовой пояс владельца,
 * рабочие часы, шаг сетки и горизонт бронирования. Тянется один раз на старте.
 */
@tag("Config")
@route("/config")
@get
@summary("Конфигурация инсталляции")
op getConfig(): Config;

/**
 * Список типов событий (видов брони) для гостя: название, описание, длительность.
 * Гость выбирает тип, затем запрашивает его доступность.
 */
@tag("EventTypes")
@route("/event-types")
@get
@summary("Список типов событий (публичный)")
op listEventTypes(): EventType[];

/**
 * Доступность выбранного типа события на окно «сегодня…сегодня+`horizonDays`»,
 * сгруппированная по дням (границы дней — в часовом поясе владельца). В выдаче —
 * только свободные старты; занятые (пересекающиеся с любой бронью) и прошлые
 * не включаются.
 */
@tag("Availability")
@route("/event-types/{id}/availability")
@get
@summary("Доступность типа события (публичный)")
op getAvailability(
  /** Идентификатор типа события. */
  @path @format("uuid") id: string,
): Availability | BadRequestResponse | NotFoundResponse;

/**
 * Создаёт бронь на свободный слот типа события. Сервер берёт длительность из типа,
 * считает `endAt` и проверяет: тип существует → старт на 15-мин границе → интервал
 * в рабочих часах → не прошлое → в пределах окна 14 дней → не пересекается с
 * существующими бронями (любого типа).
 */
@tag("Bookings")
@route("/bookings")
@post
@summary("Забронировать слот")
op createBooking(@body body: BookingRequest):
  | BookingCreated
  | BadRequestResponse
  | NotFoundResponse
  | ConflictResponse
  | UnprocessableEntityResponse;

/** Бронь создана. */
model BookingCreated {
  @statusCode statusCode: 201;
  @body body: BookingConfirmation;
}

// ──────────────────────────────────────────────
// Эндпоинты — владелец (Bearer)
// ──────────────────────────────────────────────

/** Создаёт новый тип события (владелец). */
@tag("Admin")
@route("/admin/event-types")
@post
@summary("Создать тип события (владелец)")
@useAuth(BearerAuth)
op createEventType(@body body: EventTypeRequest):
  | EventTypeCreated
  | BadRequestResponse
  | UnauthorizedResponse;

/** Тип события создан. */
model EventTypeCreated {
  @statusCode statusCode: 201;
  @body body: EventType;
}

/** Список всех типов событий (владелец). */
@tag("Admin")
@route("/admin/event-types")
@get
@summary("Список типов событий (владелец)")
@useAuth(BearerAuth)
op listAdminEventTypes(): EventType[] | UnauthorizedResponse;

/**
 * Страница предстоящих встреч: плоский список броней **всех типов** в одном списке
 * (с деталями гостя и названием типа), отсортированный по времени начала.
 */
@tag("Admin")
@route("/admin/bookings")
@get
@summary("Предстоящие встречи (владелец)")
@useAuth(BearerAuth)
op listBookings(): Booking[] | UnauthorizedResponse;

/** Отменяет бронь по её `id`. Время снова становится свободным. */
@tag("Admin")
@route("/admin/bookings/{id}")
@delete
@summary("Отменить бронь (владелец)")
@useAuth(BearerAuth)
op cancelBooking(
  /** Идентификатор брони. */
  @path @format("uuid") id: string,
): NoContentResponse | UnauthorizedResponse | NotFoundResponse;

/** Бронь отменена. */
model NoContentResponse {
  @statusCode statusCode: 204;
}
