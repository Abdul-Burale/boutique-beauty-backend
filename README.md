# Muse Beauty Store API

Spring Boot 3 / Java 21 REST API for the Muse beauty storefront. The available frontend in the adjacent `BeautyStore` directory is a Vite/React single page with an in-memory skincare catalog, search, category filters, price sort and bag count. It currently makes no API requests and has no account, checkout, order or admin screens. The API contract below gives it the backend capabilities requested while matching the product fields actually present in that UI (`brand`, `name`, category, image, tag, and price).

## Local setup

Requirements: Java 21+, Maven 3.9+, PostgreSQL 15+.

1. Create a database and local role, for example `createdb muse_beauty` and `createuser muse` (set a local password for the role).
2. Copy `.env.example` to `.env` and set `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`. Export those values in your shell; Spring reads environment variables directly.
3. Optionally set `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` (12 or more characters) for the first administrator. The bootstrap only creates the admin when none exists. Clear these values after the first startup.
4. Run `mvn spring-boot:run`. Flyway applies additive migrations at startup.

The local frontend origin `http://localhost:5173` is allowed by default. For production, set `CORS_ALLOWED_ORIGINS=https://boutiquebeautyretail.com` (include `https://www.boutiquebeautyretail.com` too if the `www` host serves the frontend). Set this to a comma-separated list of exact origins for deployed frontends. Do not use `*`; bearer authorization is enabled without credentialed cookies.

## Configuration

| Variable | Example | Purpose |
| --- | --- | --- |
| `PORT` | `8080` | HTTP port |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/muse_beauty` | PostgreSQL JDBC URL |
| `DATABASE_USERNAME` | `muse` | Database role |
| `DATABASE_PASSWORD` | `change-me` | Database password |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` locally; `https://boutiquebeautyretail.com` in production | Exact frontend origins, comma separated |
| `SESSION_TTL_HOURS` | `24` | Bearer session lifetime |
| `BOOTSTRAP_ADMIN_EMAIL` | `admin@example.com` | Optional first admin email |
| `BOOTSTRAP_ADMIN_PASSWORD` | `change-this-to-a-long-secret` | Optional first admin password (12+ chars) |

Never commit a populated `.env` or deployment secrets.

## Data and migrations

`src/main/resources/db/migration` is the schema source, managed by Flyway. `V1` creates customer accounts and hashed opaque sessions, product categories, products, product variants/options, inventory, customer addresses, baskets, orders and immutable order-line snapshots. Product prices are integer minor units in GBP. Schema changes must be new migrations; do not edit an applied migration or drop existing data.

The storefront currently has one default variant per mock product. Variants carry an option name/value for sizes or shades, and ingredient text is stored on the product. Inventory is decremented in the same transaction that places an order, and the order retains product, option, price, and address snapshots.

## API overview

All routes are under `/api`; JSON errors have `timestamp`, `status`, `error`, and `message` fields.

| Route | Access | Behavior |
| --- | --- | --- |
| `POST /auth/register`, `POST /auth/login` | Public | Create account or issue an opaque bearer token |
| `POST /auth/logout` | Bearer token | Revoke current session |
| `POST /newsletter` | Public | Save the storefront newsletter email locally |
| `GET /catalog/categories` | Public | Active categories |
| `GET /catalog/products?page=0&size=24&q=serum&category=serums&sort=featured` | Public | Search, category filtering, sorting (`featured`, `price_asc`, `price_desc`, `name_asc`) and bounded pagination |
| `GET /catalog/products/{slug}` | Public | Product and variants |
| `GET /account/me`, `GET/POST/DELETE /account/addresses` | Customer | Own profile and addresses only |
| `GET /basket`, `PUT /basket/lines`, `DELETE /basket/lines/{variantId}` | Customer | Own basket; line quantity is 1–99 |
| `POST /orders?addressId={id}`, `GET /orders`, `GET /orders/{id}` | Customer | Place an internal order, then view own order history/details |
| `GET/POST /admin/products`, `PUT /admin/products/{id}`, `POST /admin/products/{id}/variants`, `PATCH /admin/products/{id}/active`, `PUT /admin/inventory/{variantId}` | Admin | Manage catalog details, shade/size variants, visibility and stock |
| `GET /admin/orders`, `PATCH /admin/orders/{id}/status` | Admin | View and advance order status |

Successful registration/login returns `{ token, expiresAt, customer }`. Send `Authorization: Bearer <token>` for protected calls. Passwords use BCrypt; only SHA-256 hashes of random session tokens are persisted. Sessions expire and logout revokes them. Failed logins are counted by a SHA-256 hash of the normalized email; ten failures within a 15-minute window trigger a 15-minute retry delay, and a successful login clears the counter. Roles are enforced in Spring Security, and SQL ownership predicates scope customer records to the authenticated account. Registration always creates `CUSTOMER`; only the configured bootstrap process creates the first admin.

## Checkout and images

Checkout creates a `PLACED` order and reserves stock by decrementing inventory transactionally. There is no payment authorization/capture, delivery quote, shipping label, or refund workflow because no payment or fulfilment provider or business process is represented in the frontend or configured here. The business must decide how staff confirm payment and fulfil an order before this can become a production checkout. No payment or shipping integration is included.

The newsletter form stores an email opt-in in the database. It does not send campaigns or connect to a mailing provider; choose that process before enabling outbound email.

The current UI displays externally hosted image URLs and has no upload form. Product creation accepts HTTPS image URLs or same-origin paths only; there is no upload endpoint. If the admin UI later needs uploads on Google Cloud, use a private Cloud Storage bucket, short-lived signed upload URLs issued after admin authorization, size/type checks and generated object names; serve through controlled public/CDN URLs. Keep bucket configuration in environment or workload identity, not in source.

## Google Cloud deployment

Deploy the containerized Spring Boot service to Cloud Run and use Cloud SQL for PostgreSQL. Build with `mvn -q package` and package the resulting jar in a Java 21 container. Configure Cloud Run's service account for Cloud SQL connectivity and store the database password and bootstrap values in Secret Manager; use workload identity rather than a downloaded service-account key. Set `CORS_ALLOWED_ORIGINS=https://boutiquebeautyretail.com` (plus the `www` origin only if used), set `DATABASE_URL` to the Cloud SQL JDBC endpoint, and size the connection pool for Cloud Run instance limits. Run Flyway on application startup or as a controlled release migration job. No cloud resources are provisioned by this repository.

## Verification

Run `mvn test` for authentication/authorization unit and web-layer tests, and `mvn verify` for the full build. A PostgreSQL instance is required for running the application and applying the database migration.
