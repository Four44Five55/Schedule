@echo off
setlocal
cd /d "%~dp0"

rem ============================================================
rem  Schedule - управление на офлайн-ПК (без интернета, без Docker)
rem ============================================================
rem  Положите этот файл в ОДНУ папку с:
rem    - backend-1.0-SNAPSHOT.jar
rem    - .env
rem    - schedule_db.dump
rem
rem  Требования (ставятся один раз из офлайн-инсталляторов):
rem    - JRE 21 (java в PATH)
rem    - PostgreSQL 15 (путь ниже в PGBIN)
rem
rem  ПЕРВЫЙ РАЗ создайте пользователя и БД (в psql под postgres):
rem    CREATE USER devuser WITH PASSWORD 'ваш_пароль';
rem    CREATE DATABASE schedule_db OWNER devuser;
rem  (пароль должен совпадать с DB_PASSWORD в .env)
rem ============================================================

rem === Настройки (должны совпадать с .env) ===
set "DB_USER=devuser"
set "DB_PORT=5433"
set "DB_NAME=schedule_db"
set "JAR=backend-1.0-SNAPSHOT.jar"
set "DUMP=schedule_db.dump"
set "PGBIN=C:\Program Files\PostgreSQL\15\bin"

rem === Проверки окружения ===
where java >nul 2>&1 || (echo [ОШИБКА] java не найден в PATH. Установите JRE 21. & pause & exit /b 1)
if not exist "%PGBIN%\pg_restore.exe" (
    echo [ОШИБКА] PostgreSQL не найден по пути: %PGBIN%
    echo Поправьте переменную PGBIN в начале этого файла.
    pause & exit /b 1
)

:menu
echo.
echo ============================================
echo   Schedule - офлайн-ПК
echo ============================================
echo   1 - Восстановить БД из дампа (%DUMP%)
echo   2 - Запустить приложение (http://localhost:8080)
echo   3 - Выгрузить БД в дамп (для переноса обратно)
echo   0 - Выход
echo.
set /p choice="Выберите действие: "

if "%choice%"=="1" goto restore
if "%choice%"=="2" goto run
if "%choice%"=="3" goto dump
if "%choice%"=="0" exit /b 0
echo Неверный выбор.
goto menu

:restore
if not exist "%DUMP%" (echo [ОШИБКА] Файл дампа не найден: %DUMP% & goto menu)
echo.
echo Восстановление %DB_NAME% из %DUMP%
echo (существующее содержимое БД будет перезаписано; введите пароль %DB_USER%)
"%PGBIN%\pg_restore.exe" -h localhost -p %DB_PORT% -U %DB_USER% --clean --if-exists --no-owner -d %DB_NAME% "%DUMP%"
echo Готово.
goto menu

:run
echo.
echo Запуск приложения... нажмите Ctrl+C в этом окне, чтобы остановить.
echo Откройте в браузере: http://localhost:8080
echo.
java -jar "%JAR%"
goto menu

:dump
echo.
echo Выгрузка %DB_NAME% в %DUMP% (введите пароль %DB_USER%)...
"%PGBIN%\pg_dump.exe" -h localhost -p %DB_PORT% -U %DB_USER% -F c -f "%DUMP%" %DB_NAME%
echo Готово: %DUMP%
goto menu
