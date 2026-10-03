# Architecture decisions

## Phase 1 foundation

- **Modular monolith:** keep the API and storefront in one repository while keeping their builds and runtime processes separate. This makes local development straightforward and leaves clear module boundaries for future work.
- **Java 21 and Spring Boot 4.1.1:** Java 21 is the project target; the official Spring Boot 4.1 system requirements list Java 17 through 26 as supported. Maven dependencies inherit the pinned Spring Boot parent version.
- **Maven Wrapper 3.9.16:** use the checked-in wrapper scripts and pinned distribution so developers do not need a separately installed Maven.
- **PostgreSQL 17.6:** use the pinned official PostgreSQL container image for local persistent storage. The API receives its connection URL and credentials through environment variables.
- **Flyway owns schema changes:** apply versioned SQL migrations, then use Hibernate `validate`. Automatic schema updates can hide missing migrations and are disabled.
- **Health checks:** expose only Spring Boot Actuator's health endpoint. The database health contributor also makes the response useful for checking the API's database connection.
- **Frontend shell:** use React, TypeScript and Vite with exact dependency versions committed in npm's lockfile. The dev server proxies API requests to port 8081.
- **Temporary starter model:** retain the original `Item` sample and include its table in the first migration so the existing endpoint keeps working while later catalog work is pending. It is not the planned product model.
- **Test database:** use H2 only in tests to check context startup, migration application, Hibernate validation and the HTTP health response without requiring Docker. The PostgreSQL Compose smoke check has also been completed.

## Phase 2 authentication

- **Browser sessions:** use server-side HTTP sessions and same-origin HTTP-only cookies instead of storing bearer tokens in browser storage. Docker's Nginx proxy and Vite's development proxy keep API calls same-origin.
- **CSRF protection:** use Spring Security CSRF tokens for state-changing requests; the frontend obtains a token from `/api/auth/csrf` and sends it in the returned header.
- **Password storage:** hash passwords with BCrypt. Registration requires 12 or more characters; password values and hashes are never returned by the API.
- **Account identity:** trim and lowercase email addresses before storage and lookup. The database enforces email uniqueness.
- **Role assignment:** public registration always creates `CUSTOMER` accounts. `ADMIN` cannot be selected by the client; a startup initializer can provision one from `APP_ADMIN_EMAIL` and `APP_ADMIN_PASSWORD` environment values. It refuses an email already assigned to a customer and does not overwrite an existing administrator's password.
- **Session lifecycle:** sessions expire after 30 minutes and remain in API memory in this phase. Production HTTPS must enable the secure cookie flag; shared session storage is deferred to the operations/performance phases.

## Phase 3 product catalog

- **Product identity:** products have a unique, normalized uppercase SKU, name, optional description, category, order unit, decimal price, three-letter currency code and active state. The database enforces SKU uniqueness, positive prices and uppercase currency codes.
- **Money:** catalog prices use `NUMERIC(12, 2)` / `BigDecimal`; product entry accepts at most two fractional digits. Each product carries its own ISO-style currency code rather than assuming one global currency.
- **Browsing:** signed-in customers and administrators can search case-insensitively across name, SKU, description and category, filter by category, and page results in a deterministic name/ID order. Public registration does not make catalog access anonymous.
- **Product lifecycle:** administrator deactivation is soft deletion. Inactive products remain in storage and admin views, can be edited or reactivated, but are excluded from customer browse, category lists and single-product lookup.
- **Authorization:** create, update, list-all/manage, deactivate and reactivate routes require `ROLE_ADMIN`; normal catalog endpoints require authentication. State-changing calls remain protected by the Phase 2 CSRF mechanism.
- **Out of scope:** inventory quantity, stock reservation, carts, checkout and order transitions belong to later phases.

## Phase 4 cart, inventory and orders

- **Inventory:** store non-negative on-hand quantity on each product, bounded to 10,000,000. New products begin at zero stock; only admins can change stock. Adding an item to a cart does not reserve stock.
- **Cart ownership:** one persistent cart per customer account, with one line per product and quantities from 1 to 99. Cart and order routes require the `CUSTOMER` role; admin routes remain separate.
- **Currency:** all items in a cart/order must use the same product currency. This keeps the order total meaningful without adding FX conversion.
- **Checkout correctness:** revalidate active status and stock at checkout, lock product rows in UUID order, decrement inventory and create the order in one transaction. An `Idempotency-Key` unique per account makes retries safe.
- **Order record:** preserve SKU, product name, unit, quantity, unit price and line totals as immutable order-item snapshots. A later product edit does not rewrite purchase history.
- **Order lifecycle:** allow `PLACED → PROCESSING → COMPLETED`, or cancellation from `PLACED`/`PROCESSING`. Cancellation restores stock atomically; completed and cancelled orders are terminal.
- **Out of scope:** payment, shipping, stock reservations while items are in carts and background fulfillment are deferred.

## Phase 5 quality and operations

- **Test database:** keep service integration tests repeatable on H2 using a test-only schema fixture. CI starts PostgreSQL separately to exercise the production Flyway migrations and Compose health checks.
- **API errors:** return Problem Details for common client and database errors; validation responses include field names/messages but never rejected values.
- **Request correlation:** generate an opaque request ID for every request, return it as `X-Request-Id`, and include it in structured request logs. Do not log request bodies, cookies or credentials.
- **Health probes:** expose liveness separately from readiness; readiness includes PostgreSQL so Compose does not start the web container before the API/database are ready.
- **Continuous integration:** verify Maven tests/package, frontend typecheck/build, Compose syntax and container startup on pushes and pull requests.

## Phase 6 performance and Redis

- **Shared sessions:** store Spring Security HTTP sessions in Redis so authentication survives API restarts and can be shared by multiple API replicas. Use a 30-minute TTL, application-specific namespace, and Redis append-only persistence for the local Compose stack.
- **Redis availability:** include Redis in API readiness and make Compose wait for a healthy Redis service before starting the API. Bind the optional host port only to loopback so developers running the API from an IDE can connect without exposing Redis to the network.
- **Conservative caching:** cache only active product categories for five minutes. Invalidate on product creation, edits, activation and deactivation; never cache stock, prices or checkout data, which must remain current.
- **Catalog search:** enable PostgreSQL `pg_trgm` and add GIN trigram indexes to support case-insensitive substring searches across product fields. Add a composite active/category/name index for filtered catalog ordering.
- **Testing:** keep automated tests independent of Redis using in-memory sessions and cache; verify production Redis and PostgreSQL wiring in the Compose CI smoke test.
