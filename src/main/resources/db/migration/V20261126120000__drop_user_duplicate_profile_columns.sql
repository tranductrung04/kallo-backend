ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_height_cm;
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_target_weight_kg;
ALTER TABLE users
    DROP COLUMN IF EXISTS height_cm,
    DROP COLUMN IF EXISTS gender,
    DROP COLUMN IF EXISTS birth_date,
    DROP COLUMN IF EXISTS target_weight_kg;
