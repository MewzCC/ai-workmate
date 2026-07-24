-- Rename the legacy PostgreSQL reserved-word table without losing existing users.
DO $$
BEGIN
    IF to_regclass('public.app_user') IS NULL
            AND to_regclass('public."user"') IS NOT NULL THEN
        ALTER TABLE "user" RENAME TO app_user;
    END IF;
END
$$;

ALTER TABLE app_user ADD COLUMN IF NOT EXISTS display_name VARCHAR(50);
ALTER TABLE app_user ALTER COLUMN username TYPE VARCHAR(120);
CREATE UNIQUE INDEX IF NOT EXISTS ux_user_email
    ON app_user (LOWER(email))
    WHERE email IS NOT NULL;
