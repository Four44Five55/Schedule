#!/bin/bash
# Собирает самодостаточный jar для офлайн-запуска: свежий фронт запекается в статику
# бэкенда, поэтому один `java -jar` отдаёт и UI (:8080), и API. Без Docker и nginx.
#
# Запуск:  ./scripts/build-offline-jar.sh
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "==> [1/3] Сборка фронтенда (vite build)..."
cd "$ROOT/frontend"
npm run build

echo "==> [2/3] Обновление статики бэкенда свежей сборкой..."
STATIC="$ROOT/backend/src/main/resources/static"
rm -rf "$STATIC"
mkdir -p "$STATIC"
cp -r "$ROOT/frontend/dist/." "$STATIC/"

echo "==> [3/3] Сборка исполняемого jar (bootJar)..."
cd "$ROOT/backend"
./gradlew bootJar

# Исполняемый (не -plain) jar из build/libs.
JAR="$(ls -1 "$ROOT"/backend/build/libs/*.jar | grep -v -- '-plain' | head -n1)"

echo ""
echo "Готово."
echo "  JAR:    $JAR"
echo "  Запуск: java -jar \"$JAR\"   (из корня проекта — чтобы подхватился .env)"
echo ""
echo "На флешку для офлайн-ПК: этот jar + .env + дамп БД (см. scripts/db-dump.sh)."
