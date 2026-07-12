@echo off
setlocal
cd /d "%~dp0"

rem ============================================================
rem  Schedule - offline PC management (no internet, no Docker)
rem ============================================================
rem  Put this file in ONE folder together with:
rem    - backend-1.0-SNAPSHOT.jar
rem    - .env
rem    - schedule_db.dump
rem
rem  Requirements (installed once from offline installers):
rem    - JRE 21 (java in PATH)
rem    - PostgreSQL 15 (path below in PGBIN)
rem
rem  FIRST TIME create user and DB (in psql as postgres):
rem    CREATE USER devuser WITH PASSWORD 'your_password';
rem    CREATE DATABASE schedule_db OWNER devuser;
rem  (password must match DB_PASSWORD in .env)
rem ============================================================

rem === Settings (must match .env) ===
set "DB_USER=devuser"
set "DB_PORT=5433"
set "DB_NAME=schedule_db"
set "JAR=backend-1.0-SNAPSHOT.jar"
set "DUMP=schedule_db.dump"
set "PGBIN=C:\Program Files\PostgreSQL\15\bin"

rem === Environment checks ===
where java >nul 2>&1 || (echo [ERROR] java not found in PATH. Install JRE 21. & pause & exit /b 1)
if not exist "%PGBIN%\pg_restore.exe" (
    echo [ERROR] PostgreSQL not found at: %PGBIN%
    echo Fix the PGBIN variable at the top of this file.
    pause & exit /b 1
)

:menu
echo.
echo ============================================
echo   Schedule - offline PC
echo ============================================
echo   1 - Restore DB from dump (%DUMP%)
echo   2 - Run application (http://localhost:8080)
echo   3 - Export DB to dump (to transfer back)
echo   0 - Exit
echo.
set /p choice="Select action: "

if "%choice%"=="1" goto restore
if "%choice%"=="2" goto run
if "%choice%"=="3" goto dump
if "%choice%"=="0" exit /b 0
echo Invalid choice.
goto menu

:restore
if not exist "%DUMP%" (echo [ERROR] Dump file not found: %DUMP% & goto menu)
echo.
echo Restoring %DB_NAME% from %DUMP%
echo (existing DB content will be overwritten; enter password for %DB_USER%)
"%PGBIN%\pg_restore.exe" -h localhost -p %DB_PORT% -U %DB_USER% --clean --if-exists --no-owner -d %DB_NAME% "%DUMP%"
echo Done.
goto menu

:run
echo.
echo Starting application... press Ctrl+C in this window to stop.
echo Open in browser: http://localhost:8080
echo.
java -jar "%JAR%"
goto menu

:dump
echo.
echo Exporting %DB_NAME% to %DUMP% (enter password for %DB_USER%)...
"%PGBIN%\pg_dump.exe" -h localhost -p %DB_PORT% -U %DB_USER% -F c -f "%DUMP%" %DB_NAME%
echo Done: %DUMP%
goto menu
