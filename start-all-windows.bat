@echo off
echo ====================================
echo Starting Schedule Monorepo...
echo ====================================
echo.

echo This will start:
echo   - Backend:  http://localhost:8080
echo   - Frontend: http://localhost:5173
echo.

echo Starting PostgreSQL if needed...
docker-compose up -d postgres_db

echo.
echo Starting Backend...
start "Schedule Backend" cmd /k "cd backend && gradlew.bat bootRun"

timeout /t 5 /nobreak >nul

echo.
echo Starting Frontend...
start "Schedule Frontend" cmd /k "cd frontend && npm run dev"

echo.
echo ====================================
echo All services started!
echo ====================================
echo.
echo Backend:  http://localhost:8080
echo Frontend: http://localhost:5173
echo Swagger:  http://localhost:8080/swagger-ui.html
echo.
echo Check individual windows for logs
echo.
