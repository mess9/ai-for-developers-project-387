# План бэкенда

Контекст и домен — в [booking-service.md](booking-service.md). Контракт — `contract/main.tsp`
(TypeSpec v2.0.0, источник правды, не менять в рамках реализации; OpenAPI генерится в
`contract/tsp-output/openapi.yaml`). Этот документ — про реализацию бэка под актуальный контракт.

> **Важно про текущее состояние кода.** Имеющийся бэк построен вокруг *хранимых* слотов
> (таблица `slots`, `SlotService` с `@PostConstruct`/`@Scheduled`-генерацией, `bookings.slot_id`,
> статус-enum, помесячный календарь, эндпоинты `getSlot`/`getCalendar`). Контракт v2.0.0 этого
> не требует. **Слотовую механику сносим целиком** и реализуем доступность как вычисление на лету
> по `event_types` + `bookings`. Этот план описывает целевое состояние.

## Стек

- Kotlin, Spring Boot (свежая ветка с поддержкой JDK 25 — 3.5.x или 4.0.x, уточнить при init).
- JDK 25 (LTS). Gradle Kotlin DSL, single-module. Toolchain фиксирует JDK 25.
- Данные: **jOOQ** (без JPA/Hibernate). Миграции: **Flyway** (источник правды по схеме).
- Валидация: Bean Validation. Security: Spring Security (только Bearer на `/api/v1/admin/**`).
- JSON: Jackson + jsr310 (`OffsetDateTime`). **Шедулера нет** — доступность не хранится.
- `spring-boot-starter-actuator` ради `/actuator/health`.

## Что сносим (миграция со старой реализации)

- Таблицу `slots` и весь код её генерации (`SlotService.generateSlots`, `@PostConstruct`,
  `@Scheduled`, `scheduledGenerate`).
- Статус-enum слота (`SlotStatus` FREE/BOOKED/PAST) и `computeStatus`.
- Помесячный календарь и эндпоинты `getSlot`/`getCalendar`/`getAdminCalendar`/`getAdminSlot`.
- `bookings.slot_id` (FK на slots) — бронь теперь ссылается на `event_type_id` и хранит
  собственные `start_at`/`end_at`.

## Кодген

- **TypeSpec → OpenAPI**: Gradle-задача `tspCompile` (`npm run build` в `contract/`)
  компилирует `contract/*.tsp` в `contract/tsp-output/openapi.yaml` перед кодгеном.
  Требует Node (в Docker ставится в backend-стадию).
- **OpenAPI → код**: плагин `org.openapi.generator`, генератор `kotlin-spring` →
  интерфейсы контроллеров + DTO из сгенерированного `openapi.yaml`. Реализуем только тела.
  - `format: uri` маппится в `kotlin.String` (`typeMappings("URI" to "kotlin.String")`),
    иначе Hibernate Validator не вешает `@Size` на `java.net.URI`. **Следствие:** формат URI
    на сервере Bean Validation’ом не проверяется — нужна ручная проверка (см. «Ключевая логика»).
- **jOOQ**: кодген из Flyway-миграций через `org.jooq.meta.extensions.ddl.DDLDatabase`
  (БД на сборке не поднимается; SQL разбирает *парсер* jOOQ, не Postgres). Источник — папка
  миграций Flyway.
  - ⚠️ **Парсер jOOQ 3.20.x не понимает `EXCLUDE`-констрейнты** — на голом
    `EXCLUDE USING gist (… WITH &&)` кодген падает (`ParserException: … expected`).
    Поэтому overlap-констрейнт оборачиваем директивами `/* [jooq ignore start] */ … /* [jooq ignore stop] */`,
    причём **запятую перед `CONSTRAINT` прячем внутрь блока** — иначе после вырезания
    останется висячая запятая. Рецепт — в разделе «Схема БД». Проверено: с маркерами кодген зелёный.
- Сгенерённый код **не коммитим**; генерится перед компиляцией. Пометить как generated
  sources в IDEA. Добавить в `.gitignore`.

## Схема БД (Flyway, V1 переписываем начисто)

Релизов не было, БД одноразовая (Testcontainers + локальный compose) — поэтому **переписываем
`V1__initial.sql` под целевую схему**, а не накатываем `V2`-патч поверх старой.

`event_types`:
- `id uuid` PK, `name varchar(255)`, `description text` (nullable),
  `duration_minutes int`, `created_at timestamptz default now()`

`bookings`:
- `id uuid` PK, `event_type_id uuid` FK → `event_types(id)`
- `start_at timestamptz`, `end_at timestamptz` (хранится у брони — `end_at` считается при
  вставке как `start_at + duration_minutes`; прошлые встречи не «едут»)
- `name varchar(255)`, `meeting_link varchar(2048)`, `description text` (nullable),
  `created_at timestamptz default now()`
- **Overlap-констрейнт** (защита от гонки/дабл-сабмита на уровне БД), под ignore-маркерами jOOQ:
  ```sql
      created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
      /* [jooq ignore start] */
      , CONSTRAINT bookings_no_overlap
          EXCLUDE USING gist (tstzrange(start_at, end_at) WITH &&)
      /* [jooq ignore stop] */
  );
  ```
  `tstzrange` по умолчанию полуинтервал `[)`, оператор `&&` — ровно
  `start_at < newEnd AND end_at > newStart`, глобально (любого типа). `btree_gist` не нужен
  (нет столбца-равенства для комбинации). Нарушение → `SQLSTATE 23P01`.

`app_settings`: одна строка с токеном владельца (`id smallint pk = 1`, `token text`).

Колонки `bookings.event_type_name` **нет**: название типа в выдачу берём джойном к `event_types`
(тип удалить нельзя, редактирование отложено — «езда» названия не грозит).

## Ключевая логика

- **Вычисление доступности** (не хранится, без шедулера). Для запрошенного типа длительностью `D`:
  1. Окно = `[сегодня; сегодня+BOOKING_HORIZON_DAYS]` **по датам в `OWNER_TZ`** — ровно
     `horizonDays+1` календарных дней (15 при дефолте 14), включая сегодня.
  2. Одним запросом поднять брони, пересекающие окно (`start_at < windowEnd AND end_at > windowStart`),
     в память. **Никаких запросов на каждый слот.**
  3. Для каждого дня окна в `OWNER_TZ` нарезать `WORKING_HOURS` на старты с шагом
     `GRID_MINUTES` (15); оставить старты, у кого `start+D ≤ end` (интервал влезает в рабочие часы);
     границы дня в UTC — через именованную `ZoneId` (DST-корректно).
  4. Отбросить старты в прошлом (`start < now`, актуально для «сегодня») и старты, чей интервал
     `[start, start+D)` пересекается с любой бронью (линейный проход по отсортированному списку).
  5. Вернуть **все** дни окна: каждый день — `{date(LocalDate в OWNER_TZ), hasFreeSlots, slots[]}`,
     где `slots` несёт `startAt` и `endAt = startAt + D`; день без свободных слотов остаётся в
     выдаче с `hasFreeSlots=false` и пустым `slots`. Конверт ответа —
     `{eventTypeId, durationMinutes, ownerTimeZone, days[]}`.
- **Создание брони** — фиксированная лестница проверок, **стоп на первом провале**:
  1. Парсинг/Bean Validation тела: битый JSON → `MALFORMED_REQUEST` (400); пустой `name`,
     превышения длин, провал `meetingLink` → `VALIDATION_FAILED` (400, `errors[]`).
  2. **`meetingLink` (ручная проверка, т.к. `format:uri` теряется кодгеном)**: `URI(link).parseServerAuthority()`
     **плюс** `uri.isAbsolute && uri.host != null` (схема любая — допускаем deeplink’и). Иначе
     `VALIDATION_FAILED` с `errors[{field:"meetingLink"}]`. (`parseServerAuthority()` в одиночку
     пропускает мусор без схемы вроде «приходите» — поэтому нужны `isAbsolute`+`host`.)
  3. Тип существует? нет → `EVENT_TYPE_NOT_FOUND` (404). Длительность берём из типа.
  4. Старт на 15-мин границе **и** интервал `[start, start+D)` в рабочих часах → иначе
     `SLOT_NOT_ON_GRID` (422).
  5. Не прошлое (`start ≥ now`) → иначе `SLOT_IN_PAST` (422).
  6. В горизонте — дата старта в `OWNER_TZ` ≤ `сегодня+horizonDays` → иначе
     `SLOT_OUT_OF_HORIZON` (422).
  7. Pre-`EXISTS` overlap (`SELECT EXISTS(… WHERE start_at < :newEnd AND end_at > :newStart)`)
     → есть пересечение → `SLOT_ALREADY_BOOKED` (409). Даёт чистый 409 без отлова исключения.
  8. `INSERT`. Если параллельная вставка проскочила pre-check — БД-констрейнт кидает `23P01`,
     ловим в `@ControllerAdvice` → тоже `SLOT_ALREADY_BOOKED` (409). Бэкстоп под гонкой.
  - Ответ 201 `BookingConfirmation`: **без `id`**, с `eventTypeName` (джойн), `startAt`, `endAt`,
    `name`, `meetingLink`, `description?`, `createdAt` (читаем из БД после вставки).
- **Список встреч владельца** (`GET /admin/bookings`): плоский список **только будущих и текущей**
  (`end_at > now`), сортировка по `start_at` ↑, `eventTypeName` джойном к `event_types`.
- **Группировка по дням и границы окна** — в `OWNER_TZ`.
- **Токен**: на старте прочитать из `app_settings`; если нет — сгенерировать (32 байта base64url),
  сохранить; всегда писать в лог при старте. (Перенос из текущей реализации без изменений.)

## Endpoints (поведение)

Под `/api/v1`. Публичные — open; `/api/v1/admin/**` — Bearer (иначе 401 `UNAUTHORIZED`).

- `GET /config` → `{ownerTimeZone, workingHours{start,end}, gridMinutes, horizonDays}`.
- `GET /event-types` → список типов `{id, name, description?, durationMinutes}`.
- `GET /event-types/{id}/availability` → `Availability` (все дни окна, см. «Ключевая логика»);
  404 `EVENT_TYPE_NOT_FOUND` если тип не найден, 400 `MALFORMED_REQUEST` на битый UUID в пути.
- `POST /bookings` → лестница проверок выше; 201 `BookingConfirmation`. Ошибки: 400/404/422/409.
- `POST /admin/event-types` → создать тип; 400 при невалидной длительности (вне 15…120 — ловит
  Bean Validation; **кратность 15 проверяем вручную** на сервере → `VALIDATION_FAILED`).
- `GET /admin/event-types` → список типов для владельца.
- `GET /admin/bookings` → плоский список будущих/текущих встреч всех типов (с `eventTypeName`),
  сортировка по `start_at`.
- `DELETE /admin/bookings/{id}` → отмена; 204, либо 404 `BOOKING_NOT_FOUND` если нет.

## Ошибки

`@ControllerAdvice` + `ProblemDetail` (RFC 9457), `application/problem+json`, `errorCode` (enum
из контракта), `errors[]` для валидации. Маппинг кодов — см. booking-service.md §7 и enum
`ErrorCode` в контракте. Отдельно: `DataIntegrityViolationException`/`23P01` → 409
`SLOT_ALREADY_BOOKED`.

## Тесты (только интеграционные)

`@SpringBootTest(webEnvironment = RANDOM_PORT)` + Testcontainers(Postgres) +
RestAssured/TestRestTemplate. Покрыть:
- создание типа события (валидация длительности: вне 15…120 и не кратная 15);
- доступность: все дни окна присутствуют, `hasFreeSlots` корректен, старты не влезающие в
  рабочие часы отброшены, прошлое (в «сегодня») и вне горизонта исключены, занятые старты убраны;
- бронирование: успех; пересечение с бронью **того же и другого** типа → 409; прошлое; вне
  горизонта; старт не из сетки; невалидные поля; невалидный `meetingLink` (не URL / без host);
- overlap-констрейнт БД как бэкстоп (по возможности — параллельная вставка → 409, иначе хотя бы
  проверка маппинга `23P01`→409);
- токен на admin (401), список встреч (только будущие, сортировка), отмену (204/404).
Граничные TZ-кейсы (полночь, край окна, смена даты) и overlap-логику — при необходимости в
дешёвые unit-тесты на функции доступности/пересечения.

## Сборка и запуск

- `./gradlew build` — кодген (openapi + jooq) → компиляция → интеграционные тесты.
- Локально Postgres — через `compose.yaml`.
- Статика фронта (`dist`) кладётся в `resources/static` на этапе Docker-сборки.

## Definition of Done (бэк-сессия)

- Все endpoints контракта реализованы и соответствуют `openapi.yaml`.
- Интеграционные тесты зелёные.
- `docker compose up` поднимает `app` + `postgres`, миграции применяются, токен виден в логе.
