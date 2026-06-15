# EquiFlow — Project Context

## Stack
- **Backend**: Java 25, Spring Boot 3.x — 9 services + 3 infra containers
- **Frontend**: React + TypeScript + Vite (`frontend/`) — port 5173
- **Tests**: Playwright TypeScript (`tests/e2e/`) — 5 named projects
- **Infra**: Docker Compose (all services), Flyway (auto-migrations on boot), Kafka, PostgreSQL, Redis
- **GitHub**: michaelkm03/EquiFlow

## Service Map

| Container | Port | Health Endpoint |
|-----------|------|-----------------|
| api-gateway | 8080 | `GET /actuator/health` |
| auth-service | 8081 | `GET /actuator/health` |
| order-service | 8082 | `GET /actuator/health` |
| market-data-service | 8083 | `GET /actuator/health` |
| compliance-service | 8084 | `GET /actuator/health` |
| ledger-service | 8085 | `GET /actuator/health` |
| settlement-service | 8086 | `GET /actuator/health` |
| audit-service | 8087 | `GET /actuator/health` |
| saga-orchestrator | 8088 | `GET /actuator/health` |
| surge-simulator | 8089 | `GET /actuator/health` |
| postgres | 5432 | pg_isready |
| kafka | 9092 | kafka-topics --list |
| redis | 6379 | — |
| kafdrop (UI) | 9000 | — |

Debug ports: saga-orchestrator 5005, order-service 5006.

## Key Commands

```powershell
# Start all services
docker-compose up -d

# Check container status
docker-compose ps

# Tail logs for a service
docker-compose logs -f auth-service

# Stop everything
docker-compose down

# Rebuild a single service after code change
docker-compose up -d --build auth-service
```

```powershell
# Run tests (from tests/e2e/)
cd tests/e2e
npm run test:smoke        # @smoke — fast PR gate
npm run test:integration  # @integration — per-service API contracts
npm run test:e2e          # @e2e — cross-service saga
npm run test:ui           # @ui — React dashboard (requires frontend dev server)
npm run test:ui:debug     # @ui — headed with Playwright Inspector for step-through debugging
npm run test              # regression — runs everything

# Open HTML report
npm run report
```

```powershell
# Frontend dev server — runs independently from Docker, required for @ui tests
# /start_equiflow starts this automatically; run manually if needed
cd frontend
npm install               # only needed once
npm run dev               # serves at http://localhost:5173
```

## Slash Commands
- `/start_equiflow` — check service health, start backend + frontend if needed, offer to run tests
- `/stop_equiflow` — stop all Docker containers and kill the Vite dev server
- `/list_tests` — list all 56 tests with sequential IDs across all suites
- `/run_test <ID or pattern>` — run a single test by ID number or name substring

## Notes
- `MARKET_HOURS_BYPASS=true` is set in docker-compose.yml for order-service (avoids NYSE hours block in local dev)
- Flyway runs automatically on first boot — no manual seed step needed
- First `docker-compose up -d` triggers a Maven build inside each container (~3–5 min)
- Subsequent starts are faster since images are cached
- Kafdrop at http://localhost:9000 for Kafka topic inspection
