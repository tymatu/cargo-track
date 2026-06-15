# CargoTrack Frontend

Angular 21 single-page application for CargoTrack.

## Local Development

```bash
npm install
npm start
```

Open `http://localhost:4200`. The local backend should run on `http://localhost:8080`, or the Docker nginx stack should proxy API requests in the packaged environment.

## Scripts

```bash
npm run lint
npm test -- --watch=false
npm run build
npm run test:e2e
```

`npm run test:e2e` expects the local demo stack to be available at `http://localhost:8088` unless `PLAYWRIGHT_BASE_URL` is set.

## Main Areas

- Public tracking by tracking number.
- Authentication and registration.
- User parcel creation, list, detail, timeline, and map.
- Dispatcher parcel intake, shipment creation, and loading.
- Driver shipment detail, departure, arrival, and live position map.
- Admin dashboard, live fleet, users, trucks, warehouses, shipments, and audit log.

See the full project documentation in `../docs/PROJECT_DOCUMENTATION.md`.
