-- Supabase Auth now owns credentials and sessions. Keep customer profiles and all
-- order relationships, but remove the obsolete backend password/session records.
DROP TABLE IF EXISTS public.sessions;
DROP TABLE IF EXISTS public.login_attempts;
ALTER TABLE public.customers DROP COLUMN IF EXISTS password_hash;
