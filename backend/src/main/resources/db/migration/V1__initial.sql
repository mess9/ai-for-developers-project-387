CREATE TABLE slots (
    id       BIGSERIAL    PRIMARY KEY,
    start_at TIMESTAMPTZ  NOT NULL,
    end_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT slots_start_at_uq UNIQUE (start_at)
);

CREATE TABLE bookings (
    id           UUID         PRIMARY KEY,
    slot_id      BIGINT       NOT NULL REFERENCES slots (id),
    name         VARCHAR(255) NOT NULL,
    meeting_link VARCHAR(2048) NOT NULL,
    description  TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT bookings_slot_id_uq UNIQUE (slot_id)
);

-- Одна строка: токен владельца. id всегда = 1.
CREATE TABLE app_settings (
    id    SMALLINT PRIMARY KEY,
    token TEXT     NOT NULL
);
