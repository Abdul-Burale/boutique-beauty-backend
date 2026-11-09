-- Link application profiles to Supabase Auth without dropping customer data.
ALTER TABLE customers ADD COLUMN auth_user_id UUID;
ALTER TABLE customers ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE customers ADD CONSTRAINT customers_auth_user_id_key UNIQUE (auth_user_id);
