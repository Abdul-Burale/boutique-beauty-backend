CREATE TABLE customers (
  id BIGSERIAL PRIMARY KEY, email VARCHAR(254) NOT NULL UNIQUE, password_hash VARCHAR(100) NOT NULL,
  first_name VARCHAR(80) NOT NULL, last_name VARCHAR(80) NOT NULL,
  role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER' CHECK (role IN ('CUSTOMER','ADMIN')),
  active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE sessions (
  token_hash CHAR(64) PRIMARY KEY, customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
  expires_at TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX sessions_expiry_idx ON sessions(expires_at);
CREATE TABLE login_attempts (
  email_hash CHAR(64) PRIMARY KEY, attempts INTEGER NOT NULL DEFAULT 0,
  window_started_at TIMESTAMPTZ NOT NULL DEFAULT now(), blocked_until TIMESTAMPTZ
);
CREATE TABLE categories (id BIGSERIAL PRIMARY KEY, name VARCHAR(100) NOT NULL UNIQUE, slug VARCHAR(120) NOT NULL UNIQUE, active BOOLEAN NOT NULL DEFAULT TRUE);
CREATE TABLE newsletter_subscriptions (
  email VARCHAR(254) PRIMARY KEY, subscribed_at TIMESTAMPTZ NOT NULL DEFAULT now(), active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE products (
 id BIGSERIAL PRIMARY KEY, category_id BIGINT REFERENCES categories(id), brand VARCHAR(100) NOT NULL,
 name VARCHAR(180) NOT NULL, slug VARCHAR(200) NOT NULL UNIQUE, description TEXT, ingredients TEXT,
 image_url TEXT, tag VARCHAR(40), active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX products_category_idx ON products(category_id, active);
CREATE TABLE product_variants (
 id BIGSERIAL PRIMARY KEY, product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
 sku VARCHAR(80) NOT NULL UNIQUE, option_name VARCHAR(80) NOT NULL DEFAULT 'Standard', option_value VARCHAR(120) NOT NULL DEFAULT 'One size',
 price_minor INTEGER NOT NULL CHECK(price_minor >= 0), currency CHAR(3) NOT NULL DEFAULT 'GBP', active BOOLEAN NOT NULL DEFAULT TRUE,
 UNIQUE(product_id, option_name, option_value)
);
CREATE TABLE inventory (variant_id BIGINT PRIMARY KEY REFERENCES product_variants(id) ON DELETE CASCADE, quantity INTEGER NOT NULL DEFAULT 0 CHECK(quantity >= 0), updated_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE addresses (
 id BIGSERIAL PRIMARY KEY, customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
 recipient_name VARCHAR(160) NOT NULL, line1 VARCHAR(180) NOT NULL, line2 VARCHAR(180), city VARCHAR(100) NOT NULL,
 region VARCHAR(100), postal_code VARCHAR(24) NOT NULL, country_code CHAR(2) NOT NULL DEFAULT 'GB', is_default BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX addresses_customer_idx ON addresses(customer_id);
CREATE TABLE baskets (id BIGSERIAL PRIMARY KEY, customer_id BIGINT NOT NULL UNIQUE REFERENCES customers(id) ON DELETE CASCADE, updated_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE basket_lines (basket_id BIGINT NOT NULL REFERENCES baskets(id) ON DELETE CASCADE, variant_id BIGINT NOT NULL REFERENCES product_variants(id), quantity INTEGER NOT NULL CHECK(quantity BETWEEN 1 AND 99), PRIMARY KEY(basket_id,variant_id));
CREATE TABLE orders (
 id BIGSERIAL PRIMARY KEY, customer_id BIGINT NOT NULL REFERENCES customers(id), status VARCHAR(30) NOT NULL DEFAULT 'PLACED' CHECK(status IN ('PLACED','CONFIRMED','CANCELLED','FULFILLED')),
 subtotal_minor INTEGER NOT NULL CHECK(subtotal_minor >= 0), currency CHAR(3) NOT NULL DEFAULT 'GBP', address_snapshot JSONB NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX orders_customer_idx ON orders(customer_id,created_at DESC);
CREATE TABLE order_lines (
 id BIGSERIAL PRIMARY KEY, order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE, variant_id BIGINT REFERENCES product_variants(id),
 product_name VARCHAR(180) NOT NULL, brand VARCHAR(100) NOT NULL, sku VARCHAR(80) NOT NULL, option_name VARCHAR(80) NOT NULL,
 option_value VARCHAR(120) NOT NULL, unit_price_minor INTEGER NOT NULL CHECK(unit_price_minor >= 0), quantity INTEGER NOT NULL CHECK(quantity > 0)
);
INSERT INTO categories(name,slug) VALUES ('Cleansers','cleansers'),('Serums','serums'),('Moisturisers','moisturisers'),('Treatments','treatments'),('Sun care','sun-care'),('Body care','body-care'),('Toners','toners');
