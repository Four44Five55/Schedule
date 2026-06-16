@echo off
echo ====================================
echo Starting Schedule Backend...
echo ====================================
echo.

echo Backend will start on http://localhost:8080
echo Press Ctrl+C to stop
echo.
echo Note: Make sure .env file exists in project root
echo.

cd backend
gradlew.bat bootRun
