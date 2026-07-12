#!/bin/bash
# Общий запуск окружения: PostgreSQL (docker) + backend + frontend.
# Напрямую не вызывается — есть две обёртки:
#   ./scripts/start-dev.sh   — Vite dev server (HMR; для профилирования НЕ годится)
#   ./scripts/start-prod.sh  — прод-бандл через vite preview (то же, что уезжает в jar)
#
# Использование: start-core.sh <dev|prod>
set -euo pipefail

MODE="${1:-}"
if [[ "$MODE" != "dev" && "$MODE" != "prod" ]]; then
  echo "ERROR: режим не задан или неизвестен: '${MODE}'. Ожидается dev | prod." >&2
  echo "Запускайте через ./scripts/start-dev.sh или ./scripts/start-prod.sh" >&2
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "Schedule — режим ${MODE} (DB + backend + frontend)"

# Docker Compose: v2 (docker compose) с фолбэком на v1 (docker-compose)
if docker compose version >/dev/null 2>&1; then
  COMPOSE="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE="docker-compose"
else
  echo "ERROR: ни 'docker compose', ни 'docker-compose' не найдены в PATH." >&2
  exit 1
fi

echo "[1/3] PostgreSQL..."
$COMPOSE -f "$ROOT/docker-compose.yml" up -d postgres_db

echo "[2/3] Зависимости фронтенда..."
if [[ ! -d "$ROOT/frontend/node_modules" ]]; then
  echo "      node_modules нет — npm install..."
  ( cd "$ROOT/frontend" && npm install )
else
  echo "      node_modules на месте — пропуск"
fi

echo "[3/3] Фронтенд (фоном) и бэкенд (в этом окне)..."
if [[ "$MODE" == "dev" ]]; then
  # Dev server: неминифицированный React, двойной рендер StrictMode, jsxDEV/createTask.
  # Удобно править код, но мерить производительность здесь бессмысленно.
  ( cd "$ROOT/frontend" && npm run dev ) &
  FRONT_URL="http://localhost:5173"
else
  # Прод-бандл. `vite preview` наследует server.proxy из vite.config.ts,
  # поэтому /api по-прежнему уходит на :8080.
  ( cd "$ROOT/frontend" && npm run build && npm run preview ) &
  FRONT_URL="http://localhost:4173"
fi
FRONTEND_PID=$!

# Корректное завершение фронта и контейнеров при выходе
trap 'kill $FRONTEND_PID 2>/dev/null; $COMPOSE -f "$ROOT/docker-compose.yml" down; exit' INT TERM

echo ""
echo "Backend:  http://localhost:8080"
echo "Frontend: ${FRONT_URL}"
echo "Swagger:  http://localhost:8080/swagger-ui.html"
if [[ "$MODE" == "prod" ]]; then
  echo ""
  echo "ВНИМАНИЕ: сначала идёт vite build — страница поднимется только после сборки."
fi
echo ""
echo "Ctrl+C — остановить всё"

# Бэкенд держим на переднем плане: его логи и ошибки сразу видны в этом окне.
cd "$ROOT/backend"
./gradlew bootRun
