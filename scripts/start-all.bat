@echo off
chcp 65001 >nul
setlocal
set "ROOT=%~dp0.."

echo ============================================
echo   Schedule - first run (DB + backend + frontend)
echo ============================================

echo [1/3] Starting PostgreSQL (docker)...
docker-compose -f "%ROOT%\docker-compose.yml" up -d postgres_db

echo [2/3] Frontend dependencies...
if not exist "%ROOT%\frontend\node_modules" (
    echo     node_modules missing - running npm install...
    pushd "%ROOT%\frontend"
    call npm install
    popd
) else (
    echo     node_modules present - skip
)

echo [3/3] Launching backend and frontend in separate windows...
rem chcp 65001 in the backend window so Cyrillic logs render correctly
start "Schedule Backend"  cmd /k "chcp 65001 >nul && cd /d "%ROOT%\backend" && gradlew.bat bootRun"
start "Schedule Frontend" cmd /k "cd /d "%ROOT%\frontend" && npm run dev"

echo.
echo Backend:  http://localhost:8080
echo Frontend: http://localhost:5173/
echo Swagger:  http://localhost:8080/swagger-ui.html
echo.
echo (To restart only the backend after code changes use start-backend.bat)
endlocal
