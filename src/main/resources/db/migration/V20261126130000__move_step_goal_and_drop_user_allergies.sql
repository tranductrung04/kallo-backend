ALTER TABLE user_health_profiles
    ADD COLUMN IF NOT EXISTS step_goal INTEGER;

ALTER TABLE user_health_profiles
    DROP CONSTRAINT IF EXISTS chk_user_health_profiles_step_goal;

ALTER TABLE user_health_profiles
    ADD CONSTRAINT chk_user_health_profiles_step_goal
        CHECK (step_goal IS NULL OR step_goal BETWEEN 1000 AND 100000);

UPDATE user_health_profiles uhp
SET step_goal = u.step_goal
FROM users u
WHERE uhp.user_id = u.id
  AND u.step_goal IS NOT NULL
  AND uhp.step_goal IS NULL;

ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_step_goal;
ALTER TABLE users
    DROP COLUMN IF EXISTS allergies,
    DROP COLUMN IF EXISTS step_goal;
