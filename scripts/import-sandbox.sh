#!/bin/bash
# Песочница для импорта: копия боевой БД, в которой не жалко ошибиться.
#
# ЗАЧЕМ. Импорт заводит СПРАВОЧНИКИ — преподавателей, дисциплины, аудитории, корпуса. Изоляция по
# учебному периоду тут не помогает: справочники общие для всех периодов. А убрать заведённое трудно —
# как только на строку сошлётся назначение, удаление упрётся в RESTRICT и отдаст сырой 500.
# Отдельная база превращает откат кривого прогона в одну команду.
#
# Выборочного «удалить импортированное» здесь намеренно НЕТ: отличать импортные строки от прочих
# пришлось бы признаком в каждой таблице, а откатывать — в порядке зависимостей. Пересоздать базу
# из боевой и дешевле, и надёжнее.
#
# Запуск:
#   ./scripts/import-sandbox.sh copy    — пересоздать песочницу как копию боевой
#   ./scripts/import-sandbox.sh drop    — удалить песочницу
#   ./scripts/import-sandbox.sh status  — какие базы есть и кто к ним подключён
#
# После copy: в .env добавить DB_NAME=schedule_import_test и перезапустить бэкенд.
# Вернуться на боевую — убрать эту строку из .env.
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT/.env"
CONTAINER="schedule-postgres"
PROD_DB="schedule_db"
TEST_DB="${IMPORT_TEST_DB:-schedule_import_test}"

get_env() { grep -E "^$1=" "$ENV_FILE" 2>/dev/null | head -n1 | cut -d= -f2- | tr -d '\r'; }

DB_USERNAME="$(get_env DB_USERNAME)"; DB_USERNAME="${DB_USERNAME:-postgres}"
DB_PASSWORD="$(get_env DB_PASSWORD)"

if [ "$TEST_DB" = "$PROD_DB" ]; then
  echo "ОШИБКА: имя песочницы совпало с боевой базой ($PROD_DB). Скрипт боевую не трогает."
  exit 1
fi

if ! docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "$CONTAINER"; then
  echo "ОШИБКА: контейнер '$CONTAINER' не запущен."
  exit 1
fi

# MSYS_NO_PATHCONV=1 — под Git Bash аргументы вида /tmp/... подменяются на Windows-пути ДО передачи
# в docker.exe (см. db-dump.sh). Здесь путей нет, но SQL со слэшами лучше беречь тем же способом.
psql_run() {
  local db="$1"; shift
  MSYS_NO_PATHCONV=1 docker exec -e PGPASSWORD="$DB_PASSWORD" "$CONTAINER" \
    psql -U "$DB_USERNAME" -d "$db" -tA -c "$1"
}

# Кто держит базу. Бэкенд отличаем по имени драйвера: его нельзя просто «отключить» — Hikari
# переподключится через секунду, и окно закроется раньше, чем выполнится CREATE DATABASE.
backend_holds() {
  psql_run postgres "SELECT count(*) FROM pg_stat_activity WHERE datname='$1' AND application_name LIKE '%JDBC%';" | tr -d '[:space:]'
}

sessions_on() {
  psql_run postgres "SELECT count(*) FROM pg_stat_activity WHERE datname='$1' AND pid<>pg_backend_pid();" | tr -d '[:space:]'
}

drop_sessions() {
  psql_run postgres "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='$1' AND pid<>pg_backend_pid();" >/dev/null
}

case "${1:-}" in
  copy)
    if [ "$(backend_holds "$PROD_DB")" != "0" ]; then
      echo "ОШИБКА: к боевой базе подключён бэкенд — останови его и повтори."
      echo "  (копирование требует, чтобы к источнику не было ни одного подключения)"
      exit 1
    fi

    echo "==> Отключаю прочие сессии к $PROD_DB (консоли IDEA и клиенты)"
    drop_sessions "$PROD_DB"

    if [ "$(backend_holds "$TEST_DB")" != "0" ]; then
      echo "ОШИБКА: к песочнице подключён бэкенд — останови его и повтори."
      exit 1
    fi
    drop_sessions "$TEST_DB"

    echo "==> Пересоздаю $TEST_DB как копию $PROD_DB"
    psql_run postgres "DROP DATABASE IF EXISTS $TEST_DB;" >/dev/null
    psql_run postgres "CREATE DATABASE $TEST_DB TEMPLATE $PROD_DB OWNER $DB_USERNAME;" >/dev/null

    echo "Готово. Дальше:"
    echo "  1) в .env добавить строку:  DB_NAME=$TEST_DB"
    echo "  2) перезапустить бэкенд — в логе будет WARN «База данных: $TEST_DB — НЕ боевая»"
    ;;

  drop)
    if [ "$(backend_holds "$TEST_DB")" != "0" ]; then
      echo "ОШИБКА: к песочнице подключён бэкенд — останови его и повтори."
      exit 1
    fi
    drop_sessions "$TEST_DB"
    psql_run postgres "DROP DATABASE IF EXISTS $TEST_DB;" >/dev/null
    echo "Песочница $TEST_DB удалена. Не забудь убрать DB_NAME из .env."
    ;;

  status)
    echo "==> Базы:"
    psql_run postgres "SELECT datname FROM pg_database WHERE datistemplate=false ORDER BY 1;"
    echo "==> Подключения:"
    MSYS_NO_PATHCONV=1 docker exec -e PGPASSWORD="$DB_PASSWORD" "$CONTAINER" \
      psql -U "$DB_USERNAME" -d postgres -c \
      "SELECT datname, application_name, count(*) FROM pg_stat_activity WHERE datname IS NOT NULL GROUP BY 1,2 ORDER BY 1,2;"
    echo "==> В .env сейчас:"
    grep -E '^DB_NAME=' "$ENV_FILE" 2>/dev/null || echo "  DB_NAME не задан → боевая ($PROD_DB)"
    echo "  сессий на $PROD_DB: $(sessions_on "$PROD_DB"), на $TEST_DB: $(sessions_on "$TEST_DB")"
    ;;

  *)
    echo "Использование: $0 {copy|drop|status}"
    echo "  copy    — пересоздать $TEST_DB как копию $PROD_DB (бэкенд должен быть остановлен)"
    echo "  drop    — удалить $TEST_DB"
    echo "  status  — базы, подключения и текущий DB_NAME из .env"
    exit 1
    ;;
esac
