-- Типы событий (виды звонков): название, описание, длительность.
CREATE TABLE event_types (
    id               UUID         PRIMARY KEY,
    name             VARCHAR(255) NOT NULL,
    description      TEXT,
    duration_minutes INT          NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Брони: ссылаются на тип события и хранят собственный интервал [start_at, end_at).
-- end_at вычисляется при вставке как start_at + duration_minutes (прошлые встречи не «едут»).
CREATE TABLE bookings (
    id            UUID          PRIMARY KEY,
    event_type_id UUID          NOT NULL REFERENCES event_types (id),
    start_at      TIMESTAMPTZ   NOT NULL,
    end_at        TIMESTAMPTZ   NOT NULL,
    name          VARCHAR(255)  NOT NULL,
    meeting_link  VARCHAR(2048) NOT NULL,
    description   TEXT,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
    -- Глобальная защита от пересечения броней (любого типа) на уровне БД —
    -- бэкстоп против гонки/дабл-сабмита. tstzrange по умолчанию полуинтервал [),
    -- оператор && — ровно start_at < newEnd AND end_at > newStart. Нарушение → 23P01.
    -- Парсер jOOQ 3.20.x не понимает EXCLUDE — прячем под ignore-маркеры
    -- (вместе с запятой, иначе после вырезания останется висячая запятая).
    /* [jooq ignore start] */
    , CONSTRAINT bookings_no_overlap
        EXCLUDE USING gist (tstzrange(start_at, end_at) WITH &&)
    /* [jooq ignore stop] */
);

-- Одна строка: токен владельца. id всегда = 1.
CREATE TABLE app_settings (
    id    SMALLINT PRIMARY KEY,
    token TEXT     NOT NULL
);
