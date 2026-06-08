# План бэкенда

Контекст и домен — в [booking-service.md](booking-service.md). Контракт — `contract/openapi.yaml`
(источник правды, не менять в рамках реализации). Этот документ — про реализацию бэка.

## Стек

- Kotlin, Spring Boot (свежая ветка с поддержкой JDK 25 — 3.5.x или 4.0.x, уточнить при init).
- JDK 25 (LTS). Gradle Kotlin DSL, single-module. Toolchain фиксирует JDK 25.
- Данные: **jOOQ** (без JPA/Hibernate). Миграции: **Flyway** (источник правды по схеме).
- Валидация: Bean Validation. Security: Spring Security (только Bearer на `/api/v1/admin/**`).
- Шедулер: `@Scheduled`. JSON: Jackson + jsr310 (`OffsetDateTime`).
- `spring-boot-starter-actuator` ради `/actuator/health`.

## Кодген

- **OpenAPI → код**: плагин `org.openapi.generator`, генератор `kotlin-spring` →
  интерфейсы контроллеров + DTO из `contract/openapi.yaml`. Реализуем только тела.
- **jOOQ**: кодген из Flyway-миграций через `org.jooq.meta.extensions.ddl.DDLDatabase`
  (БД на сборке не поднимается). Источник — папка миграций Flyway.
- Сгенерённый код **не коммитим**; генерится перед компиляцией. Пометить как generated
  sources в IDEA. Добавить в `.gitignore`.

## Схема БД (Flyway)

`slots`:
- `id` (uuid/bigserial), `start_at timestamptz`, `end_at timestamptz`
- `UNIQUE(start_at)`

`bookings`:
- `id uuid` PK, `slot_id` FK → slots, `UNIQUE(slot_id)`
- `name`, `meeting_link`, `description` (nullable), `created_at timestamptz`

`app_settings` (или `owner_token`): одна строка с токеном владельца.

## Ключевая логика

- **Генерация слотов**: на старте + ежедневный идемпотентный `@Scheduled`-джоб.
  Для каждого дня в окне [сегодня; сегодня+`BOOKING_HORIZON_DAYS`] нарезать рабочие часы
  `WORKING_HOURS` в `OWNER_TZ` на интервалы по `SLOT_MINUTES`, перевести в UTC через
  `ZoneId`, вставить недостающие (идемпотентно — UNIQUE спасает от дублей).
- **Статус слота вычисляется** при отдаче: PAST/BOOKED/FREE.
- **Группировка по дням и границы месяца** — в `OWNER_TZ`.
- **Токен**: на старте прочитать из БД; если нет — сгенерировать (32 байта base64url),
  сохранить; всегда писать в лог при старте.

## Endpoints (поведение)

Под `/api/v1`. Публичные — open; `/api/v1/admin/**` — Bearer.

- `GET /config` → конфиг (TZ, рабочие часы, slotMinutes, horizonDays).
- `GET /calendar?month=YYYY-MM` → дни со слотами и статусами; занятые — без деталей.
- `GET /slots/{startAt}` → статус слота; 404 если слот вне сетки.
- `POST /bookings` → валидировать поля; проверить, что слот существует, в рабочих часах,
  не прошлое, в горизонте, свободен; создать бронь. Ошибки: 422 (семантика), 409 (занят),
  400 (битый запрос).
- `GET /admin/calendar` → то же + детали/признак занятости.
- `GET /admin/slots/{startAt}` → полные детали брони.
- `DELETE /admin/bookings/{id}` → отмена; 404 если нет.

## Ошибки

`@ControllerAdvice` + `ProblemDetail` (RFC 9457), `application/problem+json`,
`errorCode` (enum), `errors[]` для валидации. Маппинг кодов — см. booking-service.md §7.

## Тесты (только интеграционные)

`@SpringBootTest(webEnvironment = RANDOM_PORT)` + Testcontainers(Postgres) +
RestAssured/TestRestTemplate. Покрыть: генерацию слотов, статусы, бронирование
(успех/занят/прошлое/вне горизонта/невалидно), токен на admin (401), отмену.
Граничные TZ-кейсы (полночь, край горизонта, смена даты) — при необходимости вынести в
дешёвые unit-тесты на функцию генерации.

## Сборка и запуск

- `./gradlew build` — кодген (openapi + jooq) → компиляция → интеграционные тесты.
- Локально Postgres — через `compose.yaml`.
- Статика фронта (`dist`) кладётся в `resources/static` на этапе Docker-сборки.

## Definition of Done (бэк-сессия)

- Все endpoints контракта реализованы и соответствуют `openapi.yaml`.
- Интеграционные тесты зелёные.
- `docker compose up` поднимает `app` + `postgres`, миграции применяются, токен виден в логе.
