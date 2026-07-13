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
- Axios for API communication (relative `/api` base — proxied in dev, same-origin in the jar)
- In development served by the Vite dev server; for offline delivery the production bundle
  is baked into the backend's static resources (see `scripts/build-offline-jar.sh`)

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

2. **Start everything (DB + backend + frontend):**
   ```bash
   ./scripts/start-dev.sh
   ```
   Wraps `start-core.sh`, which brings up PostgreSQL in Docker, installs frontend
   dependencies if missing, and launches backend + frontend.

3. **Access applications:**
   - Backend API: http://localhost:8080
   - Frontend (dev): http://localhost:5173
   - API Documentation: http://localhost:8080/swagger-ui.html

### Dev mode vs production mode

`start-dev.sh` runs the **Vite dev server**: unminified React, `StrictMode` double-render,
and `jsxDEV`/`createTask` owner-stack instrumentation. Great for editing — but **never
profile performance here**, the overhead is several times the real cost and does not exist
in a real build.

To run the code users actually get:
```bash
./scripts/start-prod.sh
```
This does `vite build` + `vite preview` on **http://localhost:4173** — the same bundle that
gets baked into the offline jar. `vite preview` inherits `server.proxy` from `vite.config.ts`,
so `/api` still reaches the backend on `:8080`. The frontend window first runs the build, so
the page appears only after it finishes.

### Individual Services

**Backend only:**
```bash
cd backend
./gradlew bootRun
```

**Frontend only:**
```bash
cd frontend
npm run dev
```

## Building

**Offline jar** (frontend baked into backend static — one `java -jar` serves UI + API):
```bash
./scripts/build-offline-jar.sh
```
Note the frontend is baked in **at build time**: after changing frontend code, re-run this
script or the jar will keep serving the old bundle.

## Testing

**Backend:**
```bash
cd backend
./gradlew test
```

**Frontend:** no test suite yet. Note that `vite build` does **not** typecheck — run
`npx tsc --noEmit` in `frontend/` to verify types.

## Documentation

- [Follow-ups / tech debt](docs/FOLLOWUPS.md) — **the living doc**: open issues, decisions, and what was already done (incl. tests and dead code)
- [API Examples](docs/API_EXAMPLES.md) — actual REST contracts
- [Database schema](docs/DATABASE.md) — tables, fields, relations (Liquibase is the source of truth)
- [CQRS Architecture](docs/CQRS_ARCHITECTURE.md) — read/write split, where it leaks
- [Development Context](docs/DEVELOPMENT_CONTEXT.md) — architecture and distribution algorithm
- [Types & Swagger](docs/TYPES_AND_SWAGGER.md) — keeping Java DTOs and TS types in sync
- [Order highlight: rolled-back attempt](docs/ORDER_HIGHLIGHT_ROLLBACK.md) — why three rule formulations failed; read before revisiting

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

- `scripts/start-dev.sh` — full environment, frontend via Vite dev server (`:5173`)
- `scripts/start-prod.sh` — full environment, frontend as a production build (`:4173`)
- `scripts/start-core.sh` — shared launcher used by the two above; takes `dev|prod`, not run directly
- `scripts/build-offline-jar.sh` — self-contained jar (UI + API in one `java -jar`)
- `scripts/db-dump.sh` / `scripts/db-restore.sh` — database dump and restore

## Contributing

See [Development Guide](docs/DEVELOPMENT_CONTEXT.md) for architecture details and contribution guidelines.

## License

[Your License]
