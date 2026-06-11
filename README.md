# CargoTrack

Система отслеживания посылок и грузовиков в реальном времени.

**Стек:** Java 21 · Spring Boot 3.5 · PostgreSQL 16 · Flyway · Angular · Leaflet · WebSocket/STOMP

> 🚧 Проект в разработке. План: [SDP_CargoTrack.md](../SDP_CargoTrack.md)

## Быстрый старт (dev)

```bash
# 1. Поднять PostgreSQL + pgAdmin
docker compose up -d

# 2. Запустить backend (http://localhost:8080)
cd backend && ./mvnw spring-boot:run

# 3. Запустить frontend (http://localhost:4200)
cd frontend && npm start
```

pgAdmin: http://localhost:5050 (admin@cargotrack.local / admin)

## Структура

```
/backend   — Spring Boot REST API + WebSocket + симулятор движения
/frontend  — Angular SPA (Material, Leaflet)
```
