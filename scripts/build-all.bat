@echo off
echo Building Schedule Monorepo...

echo Building Backend...
cd backend
call gradlew.bat clean build

echo Building Frontend..
cd ..\frontend
call npm run build

echo Build completed successfully!
