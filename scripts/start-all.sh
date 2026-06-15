#!/bin/bash
echo "Starting Schedule Monorepo Development Environment..."

# Start PostgreSQL if using Docker
echo "Starting PostgreSQL..."
docker-compose up -d postgres_db

# Start Backend (background)
echo "Starting Backend..."
cd backend
./gradlew bootRun &
BACKEND_PID=$!

# Start Frontend (background)
echo "Starting Frontend..."
cd ../frontend
npm run dev &
FRONTEND_PID=$!

echo "Development environment started!"
echo "Backend: http://localhost:8080"
echo "Frontend: http://localhost:5173"
echo ""
echo "Press Ctrl+C to stop all services"

# Handle shutdown
trap "kill $BACKEND_PID $FRONTEND_PID; docker-compose down; exit" INT TERM

wait
