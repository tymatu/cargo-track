# Hardening checklist

Status: closed on 2026-06-12.

- [x] All request DTOs use Jakarta Validation and controller request bodies use `@Valid`.
- [x] Database and JWT secrets are read from environment variables.
- [x] Login returns the same error for an unknown email and an invalid password.
- [x] API errors do not expose stack traces, binding details, or internal exception messages.
- [x] CORS accepts explicit frontend origins only and rejects `*`.
- [x] Swagger UI and OpenAPI endpoints are disabled in the `prod` profile.
- [x] Persistence uses JPA and parameterized queries; no SQL string concatenation is used.
- [x] Authentication endpoints are protected by an in-memory Bucket4j rate limit.
- [x] Production compose requires explicit `DB_PASSWORD` and `JWT_SECRET` values.
- [x] nginx sends basic browser hardening headers.
- [x] The role and ownership matrix is covered by integration tests.
- [x] Refresh-token rotation, reuse detection, blocking, and token revocation are tested.
- [x] WebSocket live destinations are covered by publisher and subscription tests.
- [x] No actionable `TODO` markers remain in application code.

Verification:

- Backend: `./mvnw -B verify` passes with PostgreSQL Testcontainers.
- Frontend: `npm run lint`, `npm test -- --watch=false`, and `npm run build` pass.
- E2E: Playwright smoke tests cover public tracking, registration/login, and admin audit navigation against the local demo stack.
- OpenAPI: title, version, tags, and JWT bearer security scheme are covered by a web test.
