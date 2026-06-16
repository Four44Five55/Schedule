#!/bin/bash
# Скрипт для генерации TypeScript типов из OpenAPI спецификации

echo "🔧 Generating TypeScript types from OpenAPI spec..."

# Убедитесь, что backend запущен
echo "⚠️  Make sure backend is running on http://localhost:8080"
echo "📥 Downloading OpenAPI spec..."

# Скачиваем спецификацию
curl -s http://localhost:8080/v3/api-docs -o openapi.json

if [ ! -s openapi.json ]; then
    echo "❌ Failed to download OpenAPI spec. Is backend running?"
    exit 1
fi

echo "✅ OpenAPI spec downloaded"

# Для генерации типов нужно установить openapi-typescript
# npm install -g openapi-typescript

# Генерируем типы (если установлен openapi-typescript)
if command -v openapi-typescript &> /dev/null; then
    echo "🔨 Generating TypeScript types..."
    openapi-typescript openapi.json -o frontend/src/types/generated.ts
    echo "✅ Types generated to frontend/src/types/generated.ts"
else
    echo "⚠️  openapi-typescript not installed."
    echo "📦 Install it with: npm install -g openapi-typescript"
    echo "📄 OpenAPI spec saved to openapi.json for manual inspection"
fi

echo "🎉 Done!"
