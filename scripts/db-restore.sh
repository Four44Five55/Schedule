#!/bin/bash
# Восстанавливает БД расписания (schedule_db) из дампа pg_dump (-Fc).
# ВНИМАНИЕ: --clean --if-exists перетирает существующее содержимое БД.
# Креды берутся из .env в корне проекта. БД schedule_db должна существовать,
# сервер PostgreSQL — запущен.
#
# Режим выбирается автоматически:
#   - если запущен docker-контейнер БД (schedule-postgres) → через `docker exec`;
#   - иначе → нативный pg_restore из PATH (как на офлайн-ПК).
#
# Запуск:  ./scripts/db-restore.sh [путь_к_дампу]
#   без аргумента → берёт ./schedule_db.dump в корне проекта.
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT/.env"
DB_NAME="schedule_db"
CONTAINER="schedule-postgres"
IN="${1:-$ROOT/schedule_db.dump}"

get_env() { grep -E "^$1=" "$ENV_FILE" 2>/dev/null | head -n1 | cut -d= -f2- | tr -d '\r'; }

DB_USERNAME="$(get_env DB_USERNAME)"; DB_USERNAME="${DB_USERNAME:-postgres}"
DB_PASSWORD="$(get_env DB_PASSWORD)"
DB_PORT="$(get_env DB_PORT)"; DB_PORT="${DB_PORT:-5433}"

[ -f "$IN" ] || { echo "ОШИБКА: файл дампа не найден: $IN"; exit 1; }

if docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "$CONTAINER"; then
  echo "==> Восстановление $DB_NAME через Docker-контейнер '$CONTAINER' из $IN"
  echo "    (существующее содержимое БД будет перезаписано)"
  # Симметрично db-dump: не подаём бинарь на stdin (`docker exec -i ... < файл`) — под Git Bash
  # это ломается. Копируем дамп ВНУТРЬ контейнера docker cp и восстанавливаем из файла.
  TMP_IN_CONTAINER="/tmp/schedule_db.restore.$$"
  docker cp "$IN" "$CONTAINER:$TMP_IN_CONTAINER"
  # MSYS_NO_PATHCONV=1: под Git Bash (MSYS) аргумент вида /tmp/... подменяется на
  # Windows-путь (C:\...\Temp\...) ДО передачи в docker.exe — pg_restore внутри контейнера
  # тогда не находит файл. Отключаем конвертацию ТОЛЬКО для exec-команд (у docker cp выше
  # конвертация нужна: $IN — хостовый путь дампа).
  MSYS_NO_PATHCONV=1 docker exec -e PGPASSWORD="$DB_PASSWORD" "$CONTAINER" \
    pg_restore -U "$DB_USERNAME" --clean --if-exists --no-owner -d "$DB_NAME" "$TMP_IN_CONTAINER"
  MSYS_NO_PATHCONV=1 docker exec "$CONTAINER" rm -f "$TMP_IN_CONTAINER"
elif command -v pg_restore >/dev/null 2>&1; then
  echo "==> Восстановление $DB_NAME нативным pg_restore (localhost:$DB_PORT) из $IN"
  echo "    (существующее содержимое БД будет перезаписано)"
  export PGPASSWORD="$DB_PASSWORD"
  pg_restore -h localhost -p "$DB_PORT" -U "$DB_USERNAME" --clean --if-exists --no-owner -d "$DB_NAME" "$IN"
else
  echo "ОШИБКА: не найден ни docker-контейнер '$CONTAINER', ни нативный pg_restore."
  echo "  - на офлайн-ПК: добавьте C:\\Program Files\\PostgreSQL\\15\\bin в PATH."
  exit 1
fi

echo "Готово: расписание загружено в $DB_NAME."
