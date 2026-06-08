# CLAUDE.md

Учебный проект «Запись на звонок» — упрощённая вариация Cal.com (бронирование 30-мин
звонков). Монорепа, contract-first, фронт и бэк реализуются в отдельных сессиях по
неизменяемому OpenAPI-контракту.

## Где что

- Замысел и решения: `docs/plans/booking-service.md`
- План бэка: `docs/plans/backend.md`
- План фронта: `docs/plans/frontend.md`
- Контракт API (источник правды): `contract/openapi.yaml`
- Бэк: `backend/` (Spring Boot + jOOQ, Kotlin, JDK 25, Gradle)
- Фронт: `frontend/` (React + Vite + TypeScript, npm)
- Запуск: `compose.yaml` (postgres + app)

## Рабочие договорённости

- **Контракт неизменяем** в рамках реализации. Менять `openapi.yaml` — только отдельным
  осознанным шагом, не по ходу написания фронта/бэка.
- **Сгенерённый код не коммитим** (jOOQ-классы, OpenAPI DTO/интерфейсы, типы фронта) —
  генерится при сборке. Должен быть в `.gitignore`.
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

CI/CD, Playwright (E2E), линтер контракта и примеры в OpenAPI, rate-limiting,
обработка гонки бронирования, очистка прошлых слотов. См. `docs/plans/booking-service.md` §11.
