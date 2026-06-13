# CLAUDE.md

Учебный проект «Запись на звонок» — упрощённая вариация Cal.com: владелец заводит типы
событий (виды звонков со своей длительностью), гость бронирует свободный слот в ближайшие
14 дней. Монорепа, contract-first, фронт и бэк реализуются в отдельных сессиях по
неизменяемому OpenAPI-контракту.

## Где что

- Замысел и решения: `docs/plans/booking-service.md`
- План бэка: `docs/plans/backend.md`
- План фронта: `docs/plans/frontend.md`
- Контракт API (источник правды): `contract/main.tsp` (TypeSpec).
  OpenAPI генерится из него в `contract/tsp-output/openapi.yaml` (`npm run build` в `contract/`).
- Бэк: `backend/` (Spring Boot + jOOQ, Kotlin, JDK 25, Gradle)
- Фронт: `frontend/` (React + Vite + TypeScript, npm)
- Запуск: `compose.yaml` (postgres + app)

## Рабочие договорённости

- **Контракт неизменяем** в рамках реализации. Менять `contract/*.tsp` — только отдельным
  осознанным шагом, не по ходу написания фронта/бэка.
- **Сгенерённый код не коммитим** (jOOQ-классы, OpenAPI DTO/интерфейсы, типы фронта,
  а также сам `openapi.yaml` из TypeSpec) — генерится при сборке. Должен быть в `.gitignore`.
- Все моменты времени на проводе — `OffsetDateTime`; в БД — `timestamptz`; бизнес-логика
  и группировка по дням — в `OWNER_TZ`.
- API версионирован под `/api/v1`; админка — `/api/v1/admin/**` за Bearer-токеном.
- Общение с пользователем — по-русски.

## Definition of Done

**Бэк-сессия:**
- Все endpoints контракта реализованы и соответствуют `openapi.yaml`.
- Интеграционные тесты (Testcontainers) зелёные.
- `docker compose up` поднимает `app` + `postgres`, Flyway-миграции применяются.

**Фронт-сессия:**
- Оба экрана (посетитель и владелец) работают на MSW-моках по контракту.
- Vitest зелёный.
- `npm run build` проходит без ошибок.

## Отложено

Линтер контракта и примеры в OpenAPI, rate-limiting,
редактирование/удаление типов событий.
См. `docs/plans/booking-service.md` §11.

Гонка бронирования реализована: EXCLUDE-констрейнт на пересечение интервалов
(`bookings_no_overlap`) + маппинг конфликта в 409 `SLOT_ALREADY_BOOKED`.
