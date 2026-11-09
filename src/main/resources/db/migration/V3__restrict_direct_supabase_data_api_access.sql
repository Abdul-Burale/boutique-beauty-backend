-- All storefront data access goes through the authenticated backend API.
-- RLS blocks Supabase Data API roles unless explicit policies are added later.
DO $$
DECLARE
  table_name TEXT;
BEGIN
  FOREACH table_name IN ARRAY ARRAY[
    'customers', 'sessions', 'login_attempts', 'categories', 'newsletter_subscriptions',
    'products', 'product_variants', 'inventory', 'addresses', 'baskets', 'basket_lines',
    'orders', 'order_lines'
  ] LOOP
    EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', table_name);
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
      EXECUTE format('REVOKE ALL PRIVILEGES ON TABLE public.%I FROM anon', table_name);
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
      EXECUTE format('REVOKE ALL PRIVILEGES ON TABLE public.%I FROM authenticated', table_name);
    END IF;
  END LOOP;
END $$;
