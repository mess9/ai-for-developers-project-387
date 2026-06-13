#!/bin/sh
# Самодостаточный запуск: если внешняя БД не задана (как при запуске одного контейнера
# на учебной платформе — `docker run -e PORT=... образ`), поднимаем встроенный PostgreSQL
# прямо здесь. Если DB_URL указывает на внешний хост (compose) — ничего не трогаем.
set -e

needs_embedded_db() {
  [ -z "${DB_URL:-}" ] || printf '%s' "${DB_URL}" | grep -q 'localhost\|127\.0\.0\.1'
}

if needs_embedded_db; then
  export PGDATA="${PGDATA:-/var/lib/postgresql/data}"
  DB_NAME=booking
  DB_OWNER=booking
  DB_PASS=booking

  mkdir -p "$PGDATA"
  chown -R postgres:postgres "$PGDATA"

  if [ ! -s "$PGDATA/PG_VERSION" ]; then
    su postgres -c "initdb -D '$PGDATA' -U postgres -E UTF8 --locale=C" >/dev/null
    # Локальные подключения по TCP без пароля — БД доступна только внутри контейнера.
    echo "host all all 127.0.0.1/32 trust" >> "$PGDATA/pg_hba.conf"
  fi

  su postgres -c "pg_ctl -D '$PGDATA' -o '-c listen_addresses=127.0.0.1' -w start"

  # Идемпотентно: роль и БД создаём, если их ещё нет.
  su postgres -c "psql -U postgres -c \"CREATE ROLE $DB_OWNER LOGIN PASSWORD '$DB_PASS';\"" 2>/dev/null || true
  su postgres -c "psql -U postgres -c \"CREATE DATABASE $DB_NAME OWNER $DB_OWNER;\"" 2>/dev/null || true

  export DB_URL="jdbc:postgresql://localhost:5432/$DB_NAME"
  export DB_USER="$DB_OWNER"
  export DB_PASSWORD="$DB_PASS"
fi

exec java -jar /app/app.jar
