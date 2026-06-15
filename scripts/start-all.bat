@echo off
echo Starting Schedule Monorepo Development Environment...

echo Starting PostgreSQL...
docker-compose up -d postgres_db

echo Starting Backend...
start "Backend" cmd /k "cd backend && gradlew.bat bootRun"

echo Starting Frontend...
start "Frontend" cmd /k "cd frontend && npm run dev"

echo Development environment started!
echo Backend: http://localhost:8080
echo Frontend: http://localhost:5173
