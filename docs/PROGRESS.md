# Implementation progress

This file records work against the phases in `B2B_Ecommerce_Codex_Plan.md`.

- [x] Phase 1: Foundation — PostgreSQL Compose stack and API health verified
- [x] Phase 2: Authentication — customer registration, session login/logout and account UI implemented
- [x] Phase 3: Catalog
- [x] Phase 4: Cart, inventory and orders
- [x] Phase 5: Quality, CI and operations
- [x] Phase 6: Performance and Redis
- [ ] Phase 7: Kafka notifications
- [ ] Phase 8: AI-assisted catalog search
- [ ] Phase 9: AWS deployment preparation and authorized deployment

## Phase 1: Foundation

### Implemented

- Moved the Spring Boot application and Maven Wrapper into `backend/`; added a React, TypeScript and Vite shell in `frontend/` with a lockfile.
- Added a PostgreSQL 17.6 Compose service, environment example, Flyway V1 migration and Hibernate schema validation.
- Added an Actuator health endpoint and a backend test that starts the HTTP server, runs Flyway and Hibernate validation against H2, then requests the endpoint.
- Added setup, architecture and test documentation. Later phase checkboxes remain open.

### Verification commands and results

- From `backend/`, `.\mvnw.cmd -B verify` — PASS. One test passed; Flyway applied V1, Hibernate validated the schema, the health endpoint returned HTTP 200 with `UP`, and Maven packaged the executable JAR.
- From `frontend/`, `npm run build` — PASS. TypeScript typecheck and Vite production build succeeded.
- From `frontend/`, `npm run dev -- --host 127.0.0.1`, then `Invoke-WebRequest http://127.0.0.1:5173/` — PASS; Vite served the shell with HTTP 200.
- In a clean isolated copy, `npm ci --no-audit --no-fund` followed by `npm run build` — PASS; the lockfile installs reproducibly and the frontend build succeeds.
- `npm ci --no-audit --no-fund` in the project directory — BLOCKED by Windows `EPERM` while unlinking the TypeScript executable held open by the IDE. `npm install --no-audit --no-fund` restored dependencies and `npm run build` passed.
- `docker compose --env-file .env -f infra/compose.yaml up --build -d` — PASS. PostgreSQL and API reported healthy; frontend and API returned HTTP 200; `GET /api/items` returned an empty list.
- Phase 2 changes are included in the Docker images; registration/login/logout have not been exercised with a test account in this workspace.

### Phase 3: Product catalog

- [x] Added the Flyway V3 product table with SKU uniqueness, positive-price and uppercase-currency constraints.
- [x] Added authenticated, searchable and category-filtered product browsing with pagination and active product details.
- [x] Added admin-only create, edit, soft-deactivate and reactivate API routes; product fields are validated and SKU conflicts are handled.
- [x] Added initial administrator provisioning from `APP_ADMIN_EMAIL` and `APP_ADMIN_PASSWORD`; customer self-registration remains customer-only.
- [x] Added catalog UI with search, category selection, pagination and admin product management controls.
- [x] Documented setup, API routes, security behavior, money decisions and Phase 3 limitations.

### Limitations and next step

- PostgreSQL is exposed on host port 5433 because a local PostgreSQL process already owns port 5432.
- The original `GET /api/items` starter sample remains temporarily; it is unrelated to the new catalog.
- Phase 3 is implemented; Phase 4 details follow.

## Phase 4: Cart, inventory and orders

- [x] Added Flyway V4 inventory quantity, persistent customer carts, cart lines, orders and immutable order-item snapshots.
- [x] Added role-protected cart APIs and stock validation for add/change operations.
- [x] Added transactional checkout with deterministic pessimistic product-row locks, price/stock revalidation and `Idempotency-Key` protection.
- [x] Added customer order history and admin order management with validated status transitions; cancelling before completion restores stock atomically.
- [x] Added storefront cart quantity controls, checkout, stock display/admin stock entry, customer order history and admin fulfillment controls.
- [x] Added delivery recipient/address capture, bank-transfer or cash-on-delivery selection, persisted payment/delivery status, and admin fulfilment controls for payment and dispatch progress.
- [x] Documented inventory, transaction, idempotency and single-currency cart decisions and manual verification steps.

### Limitations and next step

- The application records payment method and delivery details, but does not collect card data, call a payment gateway, or integrate with a courier provider.
- Carts do not reserve stock; stock is checked and decremented only during checkout.
- Phase 4 is implemented; Phase 5 details follow.

## Phase 5: Quality, CI and operations

- [x] Added H2-backed Spring integration coverage for migration/schema startup, health/readiness/liveness, request IDs, unauthenticated protection, transactional checkout, idempotent retries, order snapshots and stock restoration on cancellation.
- [x] Added safe Problem Details responses for invalid input, domain conflicts, malformed JSON and database constraint conflicts.
- [x] Added generated `X-Request-Id` response headers and request-duration logs with MDC correlation; request bodies and credentials are not logged.
- [x] Added separate liveness/readiness health groups and Compose health checks for API readiness and frontend availability.
- [x] Added GitHub Actions jobs for Maven verification, frontend typecheck/build, Compose validation, container image builds and PostgreSQL-backed stack startup.
- [x] Updated testing, operations and implementation documentation.
- [x] `mvn verify` passes all five integration tests; `npm run build` and Compose configuration validation pass; PostgreSQL, API readiness and frontend Compose health checks all report healthy.

### Limitations and next step

- Dedicated frontend component/browser tests and production-grade centralized log storage are deferred.
- Phase 5 is implemented. Phase 6 is covered below.

## Phase 6: Performance and Redis

- [x] Added Redis-backed, shared Spring sessions with a 30-minute expiration and a scoped key namespace.
- [x] Added persistent Redis Compose service, health checks, API startup dependency and Redis readiness reporting.
- [x] Added a five-minute Redis cache for active product categories with catalog-write invalidation; stock and price data are never cached.
- [x] Added PostgreSQL trigram indexes for case-insensitive product substring search and a composite active/category/name index.
- [x] Added integration coverage for category-cache invalidation and updated local setup, architecture and test documentation.
- [x] Verified the Redis/PostgreSQL-backed Compose stack on this machine; all services report healthy and the V5 migration applied.

### Next step

- Phase 7: Kafka notifications.
