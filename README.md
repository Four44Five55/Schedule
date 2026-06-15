# Schedule - University Scheduling System

Monorepo containing backend (Spring Boot) and frontend (React) for automated university class scheduling with constraint-based optimization.

## Project Overview

This system automates the creation of class schedules for university study groups, taking into account multiple constraints:
- Teacher availability
- Auditorium accessibility
- Curriculum requirements
- Vacation periods
- Exam sessions and other factors

### Key Components

- **ScheduleGrid** — Core data structure representing the schedule grid (dates × time slots)
- **Lesson, Educator, Group, Auditorium** — Main schedule entities
- **DistributionDisciplineUniform, UnifiedScheduleManager** — Services for automatic lesson distribution with constraint validation
- **Excel Export** — Export final schedules for groups and teachers

### How It Works

1. **Data Initialization**: Create disciplines, teachers, groups, auditoriums, and curriculum plans
2. **Constraint Management**: Set up vacation periods, exam sessions, and other constraints for teachers and groups
3. **Automatic Distribution**: Services place lessons into the schedule grid while respecting all constraints
4. **Export**: Export final schedules to Excel for further use

## Architecture

### Backend (Spring Boot 3.2.0 + Java 21)
- CQRS architecture with command/query separation
- PostgreSQL database with Liquibase migrations
- 50+ services covering scheduling, constraints, and resource management
- RESTful API with OpenAPI documentation

### Frontend (React 19.2.6 + TypeScript)
- Vite 7.3.2 build system
- Tailwind CSS 4.1.17 for styling
- Axios for API communication
- Single-file bundle distribution

## Project Structure

```
Schedule/
├── backend/          # Spring Boot application
├── frontend/         # React application
├── scripts/          # Shared development scripts
├── docs/             # Documentation
├── docker-compose.yml
└── .env
```

## Quick Start

### Prerequisites
- Java 21
- Node.js 22+
- PostgreSQL 15+ (or Docker)

### Development

1. **Clone and setup:**
   ```bash
   git clone <repository>
   cd Schedule
   cp .env.example .env  # Configure your environment
   ```

2. **Start services:**
   ```bash
   # Using Docker (recommended)
   docker-compose up -d postgres_db

   # Or start development environment
   ./scripts/start-all.sh
   ```

3. **Access applications:**
   - Backend API: http://localhost:8080
   - Frontend: http://localhost:5173
   - API Documentation: http://localhost:8080/swagger-ui.html

### Individual Services

**Backend development:**
```bash
cd backend
./gradlew bootRun
```

**Frontend development:**
```bash
cd frontend
npm run dev
```

## Building

```bash
./scripts/build-all.sh
```

## Testing

**Backend:**
```bash
cd backend
./gradlew test
```

**Frontend:**
```bash
cd frontend
npm run test
```

## Documentation

- [API Examples](docs/API_EXAMPLES.md)
- [CQRS Architecture](docs/CQRS_ARCHITECTURE.md)
- [Testing Guide](docs/TESTING_GUIDE.md)
- [Development Context](docs/DEVELOPMENT_CONTEXT.md)
- [Architecture Refactoring Plan](docs/ARCHITECTURE_REFACTORING_PLAN.md)

## Docker Deployment

```bash
# Start all services
docker-compose up -d

# Stop all services
docker-compose down

# View logs
docker-compose logs -f
```

## Development Scripts

- `./scripts/start-all.sh` — Start all development services
- `./scripts/build-all.sh` — Build both backend and frontend
- `./scripts/dev-backend.sh` — Start backend only
- `./scripts/dev-frontend.sh` — Start frontend only
- `./scripts/clean.sh` — Clean build artifacts

## Contributing

See [Development Guide](docs/DEVELOPMENT_CONTEXT.md) for architecture details and contribution guidelines.

## License

[Your License]
