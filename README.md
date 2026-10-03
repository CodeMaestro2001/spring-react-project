# SupplyCart

SupplyCart is a B2B foodservice ordering application for restaurants. It is being built as a small modular monolith with a Spring Boot API, a React storefront and PostgreSQL. The repository includes the Phase 1 foundation, Phase 2 customer authentication, Phase 3 product catalog and Phase 4 cart, inventory and orders.

## Requirements

- Java 21
- Node.js 22.12 or newer (the Vite toolchain is locked in `frontend/package-lock.json`)
- Docker Desktop with Docker Compose for the complete local stack

The Maven Wrapper pins Maven 3.9.16. Spring Boot 4.1.1 supports Java 21. The local database image is pinned to PostgreSQL 17.6.

## Start the local database

From the repository root, create a local environment file and start PostgreSQL:

```powershell
Copy-Item .env.example .env
docker compose --env-file .env -f infra/compose.yaml up -d postgres
```

Change `POSTGRES_PASSWORD` in `.env` before using this setup outside a local development machine. The Compose service stores data in a named volume and publishes PostgreSQL on host port `5433` by default, leaving the standard `5432` port available for a local PostgreSQL server.

## Run the complete project with Docker

From the repository root, build and start PostgreSQL, the API, and the frontend:

```powershell
docker compose --env-file .env -f infra/compose.yaml up --build -d
```

Open the frontend at <http://localhost:5173>. The API is available at <http://localhost:8081>; health, readiness and liveness checks are at `/actuator/health`, `/actuator/health/readiness` and `/actuator/health/liveness`. The frontend container proxies `/api` and `/actuator` requests to the API. PostgreSQL data persists in a Docker volume when containers stop.

View service logs with:

```powershell
docker compose --env-file .env -f infra/compose.yaml logs -f
```

Stop the services without deleting database data with:

```powershell
docker compose --env-file .env -f infra/compose.yaml down
```

## Customer authentication

Use the account panel in the frontend to create a customer account and sign in. Registration requires a name, email and password of at least 12 characters. New accounts are always assigned the `CUSTOMER` role; the public registration API cannot grant administrator access.

The API uses server-side Redis sessions with an HTTP-only, same-site cookie and CSRF protection. The authenticated account can be read from `GET /api/auth/me`; use `POST /api/auth/logout` to end the session. Sessions expire after 30 minutes and are shared across API instances; Redis uses a persistent Docker volume in the local stack. For an HTTPS deployment, set `SESSION_COOKIE_SECURE=true`.

## Run the API

The API expects PostgreSQL and Redis. Start the local dependencies from the repository root:

```powershell
docker compose --env-file .env -f infra/compose.yaml up -d postgres redis
```

Then, in PowerShell, set the connection values and start Spring Boot:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5433/supplycart"
$env:DB_USERNAME = "supplycart"
$env:DB_PASSWORD = "change-me-local-only"
.\backend\mvnw.cmd -f backend\pom.xml spring-boot:run
```

Use the password you set in `.env`. The API runs at <http://localhost:8081>. Flyway applies migrations at startup, and Hibernate validates the mapped schema without changing it. The health endpoint is <http://localhost:8081/actuator/health>.

## Product catalog

Sign in to browse the catalog. Customers can search by name, SKU, description or category, filter by category, and navigate paginated results. Product prices use decimal precision and an ISO 4217 currency code; new products default to `LKR` in the form. The API also reports the current stock quantity.

Administrator product management is available in the same UI to accounts with the `ADMIN` role. Configure `APP_ADMIN_EMAIL` and `APP_ADMIN_PASSWORD` in your local `.env`, then rebuild/recreate the API so it can provision the initial administrator at startup:

```dotenv
APP_ADMIN_EMAIL=admin@example.com
APP_ADMIN_PASSWORD=use-a-unique-password-of-at-least-12-characters
```

Do not register that administrator email as a customer first. A configured administrator is created only when the email is not already in the database; an existing customer account with that email causes startup to fail rather than silently changing its role. Leave both values blank to disable initial administrator provisioning. Keep `.env` private and never commit real credentials.

Administrators can create and edit products, and deactivate or reactivate them. Deactivation is a soft state change, so product records are retained; inactive products do not appear in the customer catalog. Product writes use the authenticated session and CSRF protection. The API routes are:

- `GET /api/products?q=&category=&page=&size=` — authenticated product browse.
- `GET /api/products/categories` — active product categories.
- `GET /api/products/{id}` — one active product.
- `GET /api/admin/products?q=&category=&active=&page=&size=` — admin product management view.
- `POST /api/admin/products` and `PUT /api/admin/products/{id}` — create and edit.
- `DELETE /api/admin/products/{id}` or `PATCH /api/admin/products/{id}/active?active=true|false` — deactivate/reactivate.

The original starter's `GET /api/items` sample remains temporarily and is unrelated to the catalog.

## Cart, inventory and orders

Each customer has a persistent cart. Customers can add in-stock products, change quantities or remove cart items. A cart can contain products in one currency; the application rejects mixed-currency carts so each order has an unambiguous total. Adding to a cart does not reserve inventory.

Checkout rechecks product status, price and stock inside a database transaction, locks inventory rows to prevent two checkouts overselling the same stock, decrements quantities and writes an order with price/name snapshots. Checkout requires an `Idempotency-Key` header so a retried request returns the original order rather than creating a duplicate. The cart is cleared only after a successful order commit.

Customers can review up to 50 recent orders. Admins can set product stock (0–10,000,000), inspect up to 100 recent orders and move orders through `PLACED → PROCESSING → COMPLETED`. Admins can cancel `PLACED` or `PROCESSING` orders; cancellation restores the reserved stock in the same transaction. Completed and cancelled orders are terminal. Payments, shipping and automated fulfillment are not implemented.

Key API routes (cart and customer orders require the `CUSTOMER` role; admin routes require `ADMIN`):

- `GET /api/cart`, `POST /api/cart/items`, `PUT /api/cart/items/{productId}`, `DELETE /api/cart/items/{productId}` — retrieve and update the current cart.
- `POST /api/orders/checkout` — place an order with the `Idempotency-Key` request header.
- `GET /api/orders` and `GET /api/orders/{orderId}` — see only the signed-in customer's orders.
- `PATCH /api/admin/products/{id}/stock` — set stock with `{"quantity": 25}`.
- `GET /api/admin/orders` and `PATCH /api/admin/orders/{orderId}/status` — review and transition recent orders.

## Run the frontend

In another terminal:

```powershell
cd frontend
npm ci
npm run dev
```

Open <http://localhost:5173>. The frontend includes customer registration and sign-in and checks `/actuator/health` through the Vite development proxy.

## Checks

From the repository root:

```powershell
.\backend\mvnw.cmd -f backend\pom.xml test
.\backend\mvnw.cmd -f backend\pom.xml package
cd frontend
npm ci
npm run build
```

The backend integration tests validate the schema against in-memory H2 and use in-memory test sessions/caches, so the test suite does not require Docker. The Compose CI smoke test exercises the PostgreSQL migrations, Redis sessions and health checks.

GitHub Actions runs backend verification, frontend typecheck/build, Compose configuration validation and container image builds on pushes and pull requests. Product search uses PostgreSQL trigram indexes, and active category lookups use a short-lived Redis cache that is invalidated by catalog changes. API errors use Problem Details responses; each API response includes an `X-Request-Id` that can be used to find its request log without logging credentials or request bodies.

## Current scope and limitations

Payment, shipping, Kafka, AI integration and cloud deployment are not implemented yet. Account email verification and password recovery are deferred. There are no real payments or production credentials.
