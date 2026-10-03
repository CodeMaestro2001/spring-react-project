# Testing and local verification

## Automated checks

Run from the repository root:

```powershell
./backend/mvnw.cmd -f backend/pom.xml test
./backend/mvnw.cmd -f backend/pom.xml package
cd frontend
npm ci
npm run typecheck
npm run build
```

The Spring integration tests start the application against in-memory H2 using a test-only schema fixture that mirrors the mapped entities. They verify Hibernate schema validation, readiness, request IDs, protected routes, transactional checkout, idempotent retries, historical order snapshots and stock restoration on cancellation. CI additionally starts PostgreSQL and exercises the production Flyway migrations and Compose health checks.

The test profile uses in-memory sessions and caching to remain independent of Redis. The Compose CI smoke test starts Redis and PostgreSQL together, applies all production migrations (including the PostgreSQL trigram search indexes), and checks API readiness with both dependencies available.

For the PostgreSQL smoke check, copy `.env.example` to `.env`, start `docker compose --env-file .env -f infra/compose.yaml up -d postgres`, run the backend with matching `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` values, and request `http://localhost:8081/actuator/health`.

There is no dedicated frontend unit or browser-test suite yet. CI runs the TypeScript typecheck and production frontend build.

## Phase 2 manual account check

Start the full Compose stack, open <http://localhost:5173>, and create a customer account with a password of at least 12 characters. Sign in, refresh the page to confirm the session is restored, then sign out. The health endpoint stays public; protected API requests return HTTP 401 when signed out. Use a disposable local-development account for this check.

## Phase 3 manual catalog check

Set `APP_ADMIN_EMAIL` and a unique `APP_ADMIN_PASSWORD` (12–72 characters) in the private root `.env` before starting/recreating the API. Do not first create a customer account with this email. Rebuild with `docker compose --env-file .env -f infra/compose.yaml up --build -d`, sign in as the administrator at <http://localhost:5173>, and create a product. Check customer catalog search/category filtering and pagination with a separate customer account. As the administrator, edit the product, deactivate it and reactivate it; confirm customers cannot see inactive products. Try the admin API while signed out (401) and as a customer (403). Product state persists in the PostgreSQL Compose volume.

## Phase 4 manual cart and order check

As an administrator, set product stock to a positive quantity. Sign in as a customer, add products to the cart, change quantities and remove a line; confirm quantities above stock are rejected. Complete checkout and verify the cart is empty, stock decreased, and the order appears in customer history with its original product and price snapshot. Retry checkout with the same `Idempotency-Key` and verify it returns the same order. As an admin, move the order from `PLACED` to `PROCESSING` to `COMPLETED`; verify terminal transitions are rejected. On a separate order, cancel before completion and verify stock is restored. Verify one customer's order detail is not visible to another customer.

## Phase 5 CI and operations check

Run `./backend/mvnw -B -ntp -f backend/pom.xml verify`, then run `npm ci --no-audit --no-fund` and `npm run build` from `frontend/`. Validate and build containers with `docker compose --env-file .env.example -f infra/compose.yaml config --quiet` and `docker compose --env-file .env.example -f infra/compose.yaml build`. Confirm Redis and PostgreSQL become healthy, `/actuator/health/readiness` and `/actuator/health/liveness` respond `UP`, API responses include an `X-Request-Id`, and invalid requests return safe Problem Details without stack traces or submitted values. Sign in, restart only the API container, and confirm the Redis-backed session remains valid.
