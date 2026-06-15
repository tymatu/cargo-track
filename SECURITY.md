# Security Notes

CargoTrack is configured for local demo and coursework review without requiring a paid public deployment.

## Secrets

Production compose requires these variables in `.env`:

- `DB_PASSWORD`
- `JWT_SECRET`
- `CORS_ALLOWED_ORIGINS`

`JWT_SECRET` is validated by the backend and must be at least 32 characters. Use a longer random value for any shared environment.

## Dependency Audit

Current production dependency audit:

```bash
cd frontend
npm audit --omit=dev --audit-level=high
```

Expected result: `found 0 vulnerabilities`.

The full frontend audit may report high-severity issues in the Angular build toolchain through Vite/esbuild. These are development/build dependencies, not runtime browser dependencies, and npm currently reports no direct fix available through `npm audit fix`.

## Local Hardening

- JWT auth uses refresh-token rotation and reuse detection.
- Admin/dispatcher/driver/user routes are protected by RBAC and ownership checks.
- Auth endpoints are rate limited.
- Swagger/OpenAPI is disabled in the `prod` profile.
- nginx sends basic hardening headers: `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, and `Permissions-Policy`.
