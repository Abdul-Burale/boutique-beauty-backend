# Boutique Beauty Retail API

Spring Boot 3 / Java 21 REST API for **boutiquebeautyretail.com**. It provides a beauty catalogue, variants/options, inventory, customer profiles and addresses, baskets, internal orders, and admin catalogue/order tools. The existing Vite/React frontend currently has a mock skincare catalog, search/category filters, price sorting, and newsletter form; account, basket, checkout and admin API capabilities are available for frontend integration.

## Local setup

Requirements: Java 21+, Maven 3.9+, and PostgreSQL 15+ (local PostgreSQL or a Supabase project).

1. Create a local database/user, or use the Supabase **Session pooler** JDBC connection on port 5432. The app expects standard PostgreSQL JDBC with SSL enabled for Supabase, for example `jdbc:postgresql://<session-pooler-host>:5432/postgres?sslmode=require`.
2. Copy `.env.example` to `.env`, set its values, and export them (`set -a; source .env; set +a`). Spring does not load `.env` files by itself.
3. In Supabase Auth configure the desired sign-in methods, email confirmation and redirect allowlist. Configure an asymmetric ES256 or RS256 signing key; the API validates through the project's JWKS endpoint.
4. Optionally create an Auth user for the initial administrator, then set both `BOOTSTRAP_ADMIN_USER_ID` (that Auth user's UUID) and `BOOTSTRAP_ADMIN_EMAIL`. At startup the backend grants that database profile the `ADMIN` role. Remove these values after bootstrap.
5. Run `mvn spring-boot:run`. Flyway applies the migrations on startup.

## Configuration

| Variable | Example | Purpose |
| --- | --- | --- |
| `PORT` | `8080` | HTTP listen port |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/boutique_beauty` | PostgreSQL JDBC URL; Supabase should use Session pooler port 5432 and `sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | `boutique_beauty` | Database/pooler username |
| `SPRING_DATASOURCE_PASSWORD` | `change-me` | Database password; provide through a secret store outside local development |
| `DB_POOL_MAX_SIZE` | `5` | Hikari connections per backend instance; keep conservative and within database connection limits |
| `DB_POOL_MAX_LIFETIME_MS` | `240000` | Retire pooled connections after four minutes, before stale connections linger |
| `DB_POOL_KEEPALIVE_TIME_MS` | `120000` | Validate idle connections every two minutes; keep this below the maximum lifetime |
| `SUPABASE_ISSUER_URI` | `https://your-project-ref.supabase.co/auth/v1` | Expected JWT issuer |
| `SUPABASE_JWKS_URI` | `https://your-project-ref.supabase.co/auth/v1/.well-known/jwks.json` | Supabase signing-key endpoint |
| `SUPABASE_AUDIENCE` | `authenticated` | Required JWT audience |
| `SUPABASE_URL` | `https://your-project-ref.supabase.co` | Project URL used by the backend for Storage API calls and image URLs |
| `SUPABASE_STORAGE_BUCKET` | `product-images` | Product image bucket name |
| `SUPABASE_SECRET_KEY` | *(secret; blank until Storage uploads are configured)* | Preferred backend-only Storage API key; sent in the `apikey` header only |
| `SUPABASE_SERVICE_ROLE_KEY` | *(legacy secret; optional fallback)* | Legacy backend-only Storage key; never expose to Netlify/browser code |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Exact comma-separated browser origins. Production example: `https://boutiquebeautyretail.com,https://www.boutiquebeautyretail.com`; include the actual Netlify hostname if used |
| `BOOTSTRAP_ADMIN_USER_ID` | `00000000-0000-0000-0000-000000000000` | Optional trusted Supabase Auth UUID to promote to ADMIN |
| `BOOTSTRAP_ADMIN_EMAIL` | `admin@example.com` | Email for initial admin profile creation |

Do not use wildcard CORS origins. The API uses bearer tokens, not credentialed browser cookies. Never commit populated environment files or credentials.

## Database and migrations

Flyway in `src/main/resources/db/migration` owns the application schema. V1 creates customers, products/categories, variants/options, inventory, addresses, baskets, orders/order-line snapshots and newsletter subscriptions. V2 adds the nullable unique `customers.auth_user_id` UUID while preserving customer/profile and order data. V3 enables RLS and revokes Supabase `anon`/`authenticated` access to application tables; data access is through this API. V4 removes the obsolete password hash column and custom `sessions`/`login_attempts` tables. V5 adds editable content pages, singleton storefront contact details and ordered footer help links, with RLS and direct Data API access restrictions. Historical migrations should not be edited after deployment; later changes belong in new numbered migrations.

Existing customers are not automatically linked by matching email. After verifying a customer identity, an administrator can link the Supabase Auth UUID to the existing profile using a controlled database operation. New authenticated users are provisioned idempotently on their first API request, keyed by the JWT subject, and receive a basket. Product variants hold shade/size options; product `image_url` stores the resulting image URL/path. Prices are integer minor units in GBP.

## Authentication and authorization

The frontend signs in directly through Supabase Auth and sends `Authorization: Bearer <session.access_token>` to protected API routes. Spring Security validates the JWT signature against the configured JWKS URI, issuer, audience and expiry. Configure asymmetric ES256 or RS256 signing keys; HS256 tokens are not accepted. The backend uses JWT `sub` as the stable Supabase user UUID and links it to `customers.auth_user_id`. Supabase manages passwords and refresh tokens; this API has no password, registration/login, opaque session, or refresh-token endpoints.

At authenticated request time, the backend loads/provisions the customer row and builds authorization from its database role. New users default to `CUSTOMER`. The existing `ADMIN` value is the staff/admin role; only backend/database-controlled bootstrap or administration may grant it. User-editable JWT metadata is never used to grant admin privileges. Profile `active=false` disables API access. Customer SQL is scoped to the internal customer ID, and the `/api/account/me` response includes the linked Auth UUID and database role. Since JWT validation is local, a token already issued may remain valid until expiry after sign-out or account disablement; configure a suitably short Supabase access-token lifetime.

RLS is enabled on public application tables, without direct `anon` or `authenticated` policies/grants. Do not expose a Supabase service-role key in the frontend; the browser may use only the Supabase publishable/anon key for Auth.

## API overview

Routes are prefixed with `/api`; errors use JSON fields `timestamp`, `status`, `error`, and `message`.

| Route | Access | Behavior |
| --- | --- | --- |
| `POST /newsletter` | Public | Save newsletter opt-in locally |
| `GET /catalog/categories` | Public | List active categories |
| `GET /catalog/products?page=0&size=24&q=serum&category=serums&sort=featured` | Public | Search, category filtering, bounded pagination; sort is `featured`, `price_asc`, `price_desc`, or `name_asc` |
| `GET /catalog/products/{slug}` | Public | Product and active variants |
| `GET /content/pages`, `GET /content/pages/{slug}` | Public | Published content pages, ordered by footer group and display order |
| `GET /content/storefront` | Public | Website URL, customer email, postal address and footer help links |
| `GET /account/me` | Customer or Admin | Read own application profile and database role |
| `GET /account/addresses` | Customer | Read own addresses |
| `POST /account/addresses`, `DELETE /account/addresses/{id}` | Customer | Manage own addresses |
| `GET /basket`, `PUT /basket/lines`, `DELETE /basket/lines/{variantId}` | Customer | Manage own basket |
| `POST /orders?addressId={id}`, `GET /orders`, `GET /orders/{id}` | Customer | Place an internal order and view own order history |
| `GET/POST /admin/products`, `PUT /admin/products/{id}`, `PATCH /admin/products/{id}/active` | Admin | List (including each product's active state), create, edit and activate/deactivate products |
| `POST /admin/products/{id}/variants`, `PUT /admin/products/{productId}/variants/{variantId}`, `PUT /admin/inventory/{variantId}` | Admin | Add or edit a variant's SKU/options/price and update its stock |
| `GET /admin/orders`, `PATCH /admin/orders/{id}/status` | Admin | Review orders and advance eligible order statuses |
| `POST /admin/storage/product-images/upload-url` | Admin | Issue a short-lived signed upload URL for a generated product image path |
| `GET/POST /admin/content/pages`, `PUT/DELETE /admin/content/pages/{id}` | Admin | List, create, replace or delete content pages; page slugs are unique |
| `PUT /admin/content/storefront` | Admin | Replace storefront contact details and ordered footer help links |

Content page bodies, titles, labels, and postal addresses are plain text. The API rejects angle brackets in user-authored text and never treats the body as HTML; render it as text in the frontend (do not use an HTML injection/rendering facility). Footer links accept HTTPS URLs or same-site absolute paths only. Page writes use `{ "title", "slug", "eyebrow", "body", "published", "footerGroup", "displayOrder" }`; storefront writes use `{ "websiteUrl", "customerEmail", "postalAddress", "footerHelpLinks": [{ "label", "url", "displayOrder" }] }`. Duplicate slugs return HTTP 409. Public page routes return published pages only; all `/api/admin/**` routes require a valid Supabase JWT and `ADMIN` role from the PostgreSQL customer profile.

## Supabase Storage product images

Create the `product-images` bucket in the Supabase dashboard; this API does not provision external resources. For storefront product images, configure public reads and allow uploads only through signed upload URLs issued by this API. Set a reasonable upload size limit and allow only `image/jpeg`, `image/png`, `image/webp`, and `image/avif`; avoid SVG. Configure the bucket's browser CORS for the actual Netlify origin(s).

An authenticated ADMIN posts `{ "contentType": "image/webp" }` to `/api/admin/storage/product-images/upload-url`. The backend generates `products/<uuid>.<ext>` and asks Storage for a signed upload URL using its server-only service credential. The browser uploads bytes directly to Storage with Supabase's signed-upload client method (for example `uploadToSignedUrl(path, token, file)`), not through Spring. The response includes `bucket`, `path`, `contentType`, `signedUrl`, `token`, `publicUrl`, and `expiresInSeconds`; the signed URL/token is a temporary bearer capability, so do not log or share it. Store the returned public URL or path in the product's existing `imageUrl` field. Supabase signed upload URLs are valid for two hours; see [Supabase upload documentation](https://supabase.com/docs/guides/storage/uploads/standard-uploads) and [signed upload API](https://supabase.com/docs/reference/javascript/file-buckets-createsigneduploadurl).

## Checkout boundary

Checkout creates a `PLACED` internal order, snapshots its address/line details and decrements inventory in one database transaction. It does not authorize or collect payment, quote delivery, create shipping labels, or process refunds. Decide the payment and fulfilment business process before treating online checkout as a complete production purchase flow. No payment or shipping integrations are included.

## Verification

Run `mvn test` for tests and `mvn verify` for the full build. Running the application and applying Flyway migrations requires a reachable PostgreSQL database. For Supabase JDBC configuration and connection modes, see [Supabase's PostgreSQL connection guide](https://supabase.com/docs/guides/database/connecting-to-postgres).
