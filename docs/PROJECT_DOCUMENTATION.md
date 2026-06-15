# CargoTrack Project Documentation

CargoTrack is a local-first cargo and parcel tracking system. It is designed for coursework review, demos, and manual testing without requiring a paid server or public deployment.

## 1. Scope

The system supports four operational roles:

- `USER`: registers, logs in, creates parcels, views personal parcel history, cancels eligible parcels, and tracks parcels on a map.
- `DISPATCHER`: accepts parcels, plans shipments, assigns trucks and drivers, loads or unloads parcels, and marks final delivery.
- `DRIVER`: views assigned shipments, starts trips, monitors the route, and marks arrival.
- `ADMIN`: manages users, employees, trucks, warehouses, dashboard analytics, live fleet position, and audit logs.

Public visitors can track a parcel by tracking number without logging in.

## 2. Technology Stack

- Backend: Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, WebSocket/STOMP, Flyway, Testcontainers.
- Frontend: Angular 21, Angular Material, RxJS, Leaflet, Chart.js, Playwright.
- Database: PostgreSQL 16.
- Runtime packaging: Docker Compose, nginx reverse proxy, Spring Boot container.
- Optional route provider: OSRM. If OSRM is disabled or unavailable, CargoTrack creates fallback route geometry from warehouse coordinates.

## 3. Repository Layout

```text
backend/                  Spring Boot API, domain logic, migrations, tests
frontend/                 Angular SPA, nginx config, unit/e2e tests
docs/                     Project documentation and demo scripts
docker-compose.yml        Development database and local support services
docker-compose.prod.yml   Local review stack: PostgreSQL, backend, frontend
SECURITY.md               Security notes and audit guidance
README.md                 Quick start and project overview
```

## 4. Local Review Setup

The recommended review path is the Docker stack.

```bash
cp .env.example .env
docker compose -f docker-compose.prod.yml up -d --build
```

PowerShell:

```powershell
Copy-Item .env.example .env
docker compose -f docker-compose.prod.yml up -d --build
```

Open:

```text
http://localhost:8088
```

The sample `.env.example` enables `prod,demo`, demo seeding, simulation, and demo life generation for local review. This is intentional for coursework and presentation. For a clean production-like local stack, set:

```env
SPRING_PROFILES_ACTIVE=prod
SIMULATION_ENABLED=false
DEMO_SEED_ENABLED=false
DEMO_LIFE_ENABLED=false
```

## 5. Demo Accounts

All seeded accounts use the password `CargoTrack123!`.

| Role | Email |
|---|---|
| User | `user@cargotrack.local` |
| Driver | `driver.prague@cargotrack.local` |
| Dispatcher | `dispatcher.prague@cargotrack.local` |
| Admin | `admin@cargotrack.local` |

Useful tracking numbers:

- `CT-DEMO00001`
- `CT-DEMO00031`
- `CT-DEMO00032`

## 6. Core Workflows

### Registration and Login

1. A visitor creates an account through the registration page.
2. The backend normalizes the email, validates the password, stores the user, and returns access plus refresh tokens.
3. The frontend stores tokens locally and uses the access token on API calls.
4. Expired access tokens are refreshed automatically.
5. Refresh-token reuse is treated as suspicious and causes token revocation.

### Parcel Creation

1. A user selects origin and destination warehouses.
2. The frontend requests a price quote.
3. The backend validates distance, weight, and size, then calculates a price.
4. The user creates the parcel.
5. A tracking number and initial tracking event are created.

### Dispatch and Shipment Planning

1. Dispatcher opens parcels waiting for processing.
2. Dispatcher accepts parcels at their origin warehouse.
3. Dispatcher creates a shipment with origin, destination, truck, driver, and planned departure time.
4. Dispatcher loads eligible parcels into the shipment.
5. Loaded parcels move through shipment-driven status changes.

### Driver Trip

1. Driver opens assigned shipments.
2. Driver starts departure.
3. Simulation creates moving truck positions along the route.
4. Live updates are pushed through WebSocket topics.
5. Driver marks arrival at destination.

### Admin Operations

Admin can:

- View dashboard totals and revenue period charts.
- Watch live fleet movement.
- Manage users, employee roles, trucks, and warehouses.
- Open audit logs with old/new JSON details.

## 7. HTTP API Map

All API routes use the `/api/v1` prefix.

### Authentication

- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/refresh`
- `POST /auth/logout`
- `GET /auth/me`

### Public Tracking

- `GET /tracking/{trackingNumber}`

### User Parcels

- `POST /parcels`
- `POST /parcels/calculate-price`
- `GET /parcels/my`
- `GET /parcels/{id}`
- `POST /parcels/{id}/cancel`

### Warehouses

- `GET /warehouses`

### Dispatcher

- `GET /dispatcher/parcels`
- `POST /dispatcher/parcels/{id}/accept`
- `POST /dispatcher/parcels/{id}/deliver`
- `GET /dispatcher/trucks`
- `GET /dispatcher/drivers`
- `POST /dispatcher/shipments`
- `GET /dispatcher/shipments`
- `GET /dispatcher/shipments/{id}`
- `POST /dispatcher/shipments/{id}/parcels`
- `DELETE /dispatcher/shipments/{id}/parcels/{parcelId}`

### Driver

- `GET /driver/shipments/my`
- `GET /driver/shipments/{id}`
- `POST /driver/shipments/{id}/depart`
- `POST /driver/shipments/{id}/arrive`

### Admin

- `GET /admin/dashboard`
- `GET /admin/stats/dashboard`
- `GET /admin/fleet`
- `GET /admin/shipments`
- `GET /admin/users`
- `POST /admin/users`
- `POST /admin/users/{id}/block`
- `POST /admin/users/{id}/unblock`
- `PATCH /admin/users/{id}/role`
- `GET /admin/trucks`
- `POST /admin/trucks`
- `PUT /admin/trucks/{id}`
- `DELETE /admin/trucks/{id}`
- `GET /admin/warehouses`
- `POST /admin/warehouses`
- `PUT /admin/warehouses/{id}`
- `DELETE /admin/warehouses/{id}`
- `GET /admin/audit`

## 8. WebSocket Topics

WebSocket endpoint:

```text
/ws
```

The client sends the JWT during STOMP `CONNECT`. Subscriptions are authorized by role and ownership.

Topics:

- `/topic/admin/fleet`
- `/topic/trucks/{truckId}/position`
- `/topic/shipments/{shipmentId}/position`
- `/topic/parcels/{parcelId}/events`
- `/topic/parcels/{trackingNumber}/events`

## 9. Status Lifecycles

Parcel statuses are guarded by explicit transition rules. Invalid or null transitions are rejected instead of throwing unchecked exceptions.

Shipment statuses are also guarded by explicit transition rules. Trips can only start and finish through the allowed lifecycle.

## 10. Security Model

- JWT access tokens and rotating refresh tokens.
- Refresh-token reuse detection and revocation.
- Role-based access control for all protected routes.
- Ownership checks for user parcels, driver shipments, and live topic subscriptions.
- Auth endpoint rate limiting.
- Global API error handling without stack traces or binding internals.
- Swagger/OpenAPI disabled in `prod`.
- nginx hardening headers for the packaged frontend.

See also:

- [Security notes](../SECURITY.md)
- [Hardening checklist](HARDENING.md)

## 11. Development

Start support services:

```bash
docker compose up -d
```

Run backend:

```bash
cd backend
./mvnw spring-boot:run
```

Run frontend:

```bash
cd frontend
npm start
```

Default development URLs:

- Frontend: `http://localhost:4200`
- Backend: `http://localhost:8080`
- PostgreSQL: `localhost:5433`
- pgAdmin: `http://localhost:5050`

## 12. Verification

Backend:

```bash
cd backend
./mvnw -B verify
```

Frontend:

```bash
cd frontend
npm run lint
npm test -- --watch=false
npm run build
```

Production compose config:

```bash
DB_PASSWORD=local-db-password JWT_SECRET=local-jwt-secret-local-jwt-secret-local-jwt-secret-123456 docker compose -f docker-compose.prod.yml config --quiet
```

E2E smoke test:

```bash
SPRING_PROFILES_ACTIVE=prod,demo SIMULATION_ENABLED=true DEMO_SEED_ENABLED=true DEMO_LIFE_ENABLED=true docker compose -f docker-compose.prod.yml up -d --build
cd frontend
npm run test:e2e
```

## 13. Troubleshooting

### Java 21 Is Not Found

Set `JAVA_HOME` to a Java 21 installation before running Maven.

PowerShell example:

```powershell
$env:JAVA_HOME='E:\jdk-21.0.10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
java -version
```

### Backend Fails on Startup

Check that:

- `DB_PASSWORD` is set.
- `JWT_SECRET` is set and at least 32 characters long.
- PostgreSQL is healthy.
- Flyway migrations are not edited after being applied to an existing database volume.

### Frontend Cannot Reach API

Check that:

- Docker frontend is opened through `http://localhost:8088`.
- `CORS_ALLOWED_ORIGINS` contains the frontend origin.
- The backend container is healthy.
- nginx proxies `/api/v1` and `/ws` to the backend.

### Demo Data Does Not Appear

For seeded demo data, use:

```env
SPRING_PROFILES_ACTIVE=prod,demo
DEMO_SEED_ENABLED=true
DEMO_LIFE_ENABLED=true
SIMULATION_ENABLED=true
```

If a previous empty database volume exists, recreate the stack:

```bash
docker compose -f docker-compose.prod.yml down -v
docker compose -f docker-compose.prod.yml up -d --build
```

### Live Map Does Not Move

Check that:

- `SIMULATION_ENABLED=true`.
- A shipment is in `IN_TRANSIT`.
- The truck has a route with at least two geometry points.
- The browser WebSocket connection to `/ws` is established.

## 14. Review Checklist

- Register a new user.
- Log in and log out.
- Create a parcel and verify the price quote.
- Open public tracking by tracking number.
- Accept a parcel as dispatcher.
- Create a shipment and load parcels.
- Depart and arrive as driver.
- Confirm parcel timeline and map changes.
- Open admin dashboard and live fleet.
- Search audit entries and inspect JSON details.
- Run backend, frontend, compose, and e2e verification commands.
