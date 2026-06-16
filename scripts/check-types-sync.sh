#!/bin/bash
# Простой скрипт для проверки синхронизации типов

echo "🔍 Checking type synchronization between Java and TypeScript..."

echo ""
echo "📋 Java DTOs in backend:"
find backend/src/main/java/ru/dto -name "*Dto.java" -exec basename {} \; | sed 's/.java$//' | sort

echo ""
echo "📋 TypeScript interfaces in frontend:"
grep -r "export interface.*Dto" frontend/src/types/ | sed 's/.*interface //' | sed 's/ .*//' | sort

echo ""
echo "⚠️  If you added new fields to Java DTOs, don't forget to update TypeScript types!"
echo "💡 Tip: Use ./scripts/generate-types.sh for automatic type generation (when Swagger is configured)"
