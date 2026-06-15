#!/bin/bash
set -e

echo "Building Schedule Monorepo..."

echo "Building Backend..."
cd "$(dirname "$0")/../backend"
./gradlew clean build

echo "Building Frontend..."
cd "$(dirname "$0")/../frontend"
npm run build

echo "Build completed successfully!"
