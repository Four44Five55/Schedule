#!/bin/bash
# Снимает дамп БД расписания (schedule_db) в сжатом формате pg_dump (-Fc).
# Креды берутся из .env в корне проекта (DB_USERNAME/DB_PASSWORD/DB_PORT).
#
# Режим выбирается автоматически:
#   - если запущен docker-контейнер БД (schedule-postgres) → дамп через `docker exec`
#     (ничего ставить не надо — используется pg_dump ИЗ контейнера);
#   - иначе → нативный pg_dump из PATH (как на офлайн-ПК с локальным PostgreSQL).
#
# Запуск:  ./scripts/db-dump.sh [путь_к_файлу]
#   без аргумента → пишет в ./schedule_db.dump в корне проекта.
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT/.env"
DB_NAME="schedule_db"
CONTAINER="schedule-postgres"
OUT="${1:-$ROOT/schedule_db.dump}"

# Безопасно достаём значение из .env без eval (пароль содержит спецсимволы),
# заодно срезаем возможный CR от Windows-переводов строк.
get_env() { grep -E "^$1=" "$ENV_FILE" 2>/dev/null | head -n1 | cut -d= -f2- | tr -d '\r'; }

DB_USERNAME="$(get_env DB_USERNAME)"; DB_USERNAME="${DB_USERNAME:-postgres}"
DB_PASSWORD="$(get_env DB_PASSWORD)"
DB_PORT="$(get_env DB_PORT)"; DB_PORT="${DB_PORT:-5433}"

if docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "$CONTAINER"; then
  echo "==> Дамп через Docker-контейнер '$CONTAINER' → $OUT"
  # ВАЖНО: не гоняем бинарный дамп через stdout хоста (`docker exec ... > файл`) — под
  # Git Bash/MSYS перенаправление бинаря ломается и файл выходит 0 байт. Пишем дамп ВНУТРИ
  # контейнера, затем забираем docker cp (он корректно переносит бинарь на любом shell).
  TMP_IN_CONTAINER="/tmp/schedule_db.dump.$$"
  # MSYS_NO_PATHCONV=1: под Git Bash (MSYS) аргумент вида /tmp/... подменяется на
  # Windows-путь (C:\...\Temp\...) ДО передачи в docker.exe — pg_dump внутри контейнера
  # тогда пишет не туда («could not open output file»). Отключаем конвертацию ТОЛЬКО для
  # exec-команд; у docker cp ниже она нужна ($OUT — хостовый путь назначения).
  MSYS_NO_PATHCONV=1 docker exec -e PGPASSWORD="$DB_PASSWORD" "$CONTAINER" \
    pg_dump -U "$DB_USERNAME" -F c -f "$TMP_IN_CONTAINER" "$DB_NAME"
  docker cp "$CONTAINER:$TMP_IN_CONTAINER" "$OUT"
  MSYS_NO_PATHCONV=1 docker exec "$CONTAINER" rm -f "$TMP_IN_CONTAINER"
elif command -v pg_dump >/dev/null 2>&1; then
  echo "==> Дамп нативным pg_dump (localhost:$DB_PORT, пользователь $DB_USERNAME) → $OUT"
  export PGPASSWORD="$DB_PASSWORD"
  pg_dump -h localhost -p "$DB_PORT" -U "$DB_USERNAME" -F c -f "$OUT" "$DB_NAME"
else
  echo "ОШИБКА: не найден ни docker-контейнер '$CONTAINER', ни нативный pg_dump."
  echo "  - на этой машине: запустите БД (start-all), контейнер должен работать;"
  echo "  - на офлайн-ПК: добавьте C:\\Program Files\\PostgreSQL\\15\\bin в PATH."
  exit 1
fi

echo "Готово: $OUT"
echo "Перенесите файл на другой ПК и восстановите: ./scripts/db-restore.sh \"$OUT\""
