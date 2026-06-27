#!/bin/bash
# Запуск всего окружения: PostgreSQL (docker) + backend + frontend.
# Пути привязаны к расположению скрипта, поэтому его можно запускать из любой папки.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "Starting Schedule Monorepo Development Environment..."

# Docker Compose: v2 (docker compose) с фолбэком на v1 (docker-compose)
if docker compose version >/dev/null 2>&1; then
  COMPOSE="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE="docker-compose"
else
  echo "ERROR: ни 'docker compose', ни 'docker-compose' не найдены в PATH." >&2
  exit 1
fi

echo "Starting PostgreSQL..."
$COMPOSE -f "$ROOT/docker-compose.yml" up -d postgres_db

# Frontend (в фоне)
echo "Starting Frontend..."
( cd "$ROOT/frontend" && npm run dev ) &
FRONTEND_PID=$!

# Корректное завершение фронта и контейнеров при выходе
trap 'kill $FRONTEND_PID 2>/dev/null; $COMPOSE -f "$ROOT/docker-compose.yml" down; exit' INT TERM

echo "Development environment started!"
echo "Backend:  http://localhost:8080"
echo "Frontend: http://localhost:5173"
echo ""
echo "Press Ctrl+C to stop all services"

# Backend держим на переднем плане: его логи и ошибки сразу видны в этом окне.
cd "$ROOT/backend"
./gradlew bootRun
