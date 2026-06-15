#!/bin/bash
echo "Cleaning Schedule Monorepo..."

# Clean Backend
cd "$(dirname "$0")/../backend"
./gradlew clean

# Clean Frontend
cd "$(dirname "$0")/../frontend"
rm -rf dist/ node_modules/.vite

echo "Clean completed!"
