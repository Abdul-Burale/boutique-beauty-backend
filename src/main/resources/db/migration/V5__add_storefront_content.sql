CREATE TABLE content_pages (
  id BIGSERIAL PRIMARY KEY,
  title VARCHAR(160) NOT NULL,
  slug VARCHAR(120) NOT NULL,
  eyebrow VARCHAR(100),
  body TEXT NOT NULL,
  published BOOLEAN NOT NULL DEFAULT FALSE,
  footer_group VARCHAR(80),
  display_order INTEGER NOT NULL DEFAULT 0 CHECK (display_order >= 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT content_pages_slug_key UNIQUE (slug)
);

CREATE INDEX content_pages_public_order_idx ON content_pages(published, footer_group, display_order, id);

CREATE TABLE storefront_settings (
  id BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (id),
  website_url VARCHAR(2048),
  customer_email VARCHAR(254),
  postal_address TEXT,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO storefront_settings (id) VALUES (TRUE);

CREATE TABLE storefront_help_links (
  id BIGSERIAL PRIMARY KEY,
  label VARCHAR(100) NOT NULL,
  url VARCHAR(2048) NOT NULL,
  display_order INTEGER NOT NULL CHECK (display_order >= 0)
);

CREATE INDEX storefront_help_links_order_idx ON storefront_help_links(display_order, id);

-- New content is served through the backend. Prevent direct Supabase Data API
-- access unless explicit policies are intentionally introduced later.
DO $$
DECLARE
  table_name TEXT;
BEGIN
  FOREACH table_name IN ARRAY ARRAY['content_pages', 'storefront_settings', 'storefront_help_links'] LOOP
    EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', table_name);
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
      EXECUTE format('REVOKE ALL PRIVILEGES ON TABLE public.%I FROM anon', table_name);
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
      EXECUTE format('REVOKE ALL PRIVILEGES ON TABLE public.%I FROM authenticated', table_name);
    END IF;
  END LOOP;
END $$;
