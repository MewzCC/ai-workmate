-- Apply once to databases created before the enterprise authentication module.
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS display_name VARCHAR(50);
ALTER TABLE "user" ALTER COLUMN username TYPE VARCHAR(120);
CREATE UNIQUE INDEX IF NOT EXISTS ux_user_email ON "user" (LOWER(email)) WHERE email IS NOT NULL;
