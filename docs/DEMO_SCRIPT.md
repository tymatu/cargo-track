# Local Demo Script

Use this script when presenting CargoTrack locally.

## Start

```bash
cp .env.example .env
docker compose -f docker-compose.prod.yml up -d --build
```

Open http://localhost:8088.

The sample `.env.example` enables `SPRING_PROFILES_ACTIVE=prod,demo`, demo seeding, and simulation for local review.

## Flow

1. Open `/track/CT-DEMO00001`.
   - Show public tracking without login.
   - Point out the auto-refresh timestamp.

2. Register a new customer.
   - Create an account.
   - Log in.
   - Create a parcel from the customer area.

3. Log in as dispatcher.
   - Use `dispatcher.prague@cargotrack.local` / `CargoTrack123!`.
   - Accept a parcel.
   - Create or open a shipment.
   - Load parcels into the truck.

4. Log in as driver.
   - Use `driver.prague@cargotrack.local` / `CargoTrack123!`.
   - Open the assigned shipment.
   - Mark departure and watch the route map update.

5. Log in as admin.
   - Use `admin@cargotrack.local` / `CargoTrack123!`.
   - Show dashboard stats, live fleet, user search, and audit details.

## Suggested Screenshots

- Public tracking page with `CT-DEMO00001`.
- Customer parcel detail with map and timeline.
- Dispatcher shipment loading screen.
- Driver shipment detail with route map.
- Admin dashboard live fleet.
- Admin audit detail panel.
