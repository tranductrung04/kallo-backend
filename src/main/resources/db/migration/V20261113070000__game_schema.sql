-- GAME module schema

CREATE TABLE user_game_settings (
    user_id      UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    friend_code  VARCHAR(12) NOT NULL,
    discoverable BOOLEAN     NOT NULL DEFAULT TRUE,
    share_streak BOOLEAN     NOT NULL DEFAULT TRUE,
    share_points BOOLEAN     NOT NULL DEFAULT TRUE,
    share_steps  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_user_game_settings_friend_code
    ON user_game_settings (friend_code);

CREATE INDEX idx_users_display_name_prefix
    ON users (lower(display_name) text_pattern_ops);

CREATE TABLE friendships (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    addressee_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_friendships_not_self CHECK (requester_id <> addressee_id),
    CONSTRAINT chk_friendships_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED'))
);

CREATE UNIQUE INDEX uq_friendships_pair
    ON friendships (LEAST(requester_id, addressee_id), GREATEST(requester_id, addressee_id));
CREATE INDEX idx_friendships_addressee ON friendships (addressee_id, status);
CREATE INDEX idx_friendships_requester ON friendships (requester_id, status);

CREATE TABLE streaks (
    user_id             UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    current_streak      INTEGER     NOT NULL DEFAULT 0,
    longest_streak      INTEGER     NOT NULL DEFAULT 0,
    last_completed_date DATE,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_streaks_values CHECK (current_streak >= 0 AND longest_streak >= current_streak)
);

CREATE TABLE badges (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(50)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500) NOT NULL,
    icon_key    VARCHAR(200),
    rule_type   VARCHAR(30)  NOT NULL,
    threshold   INTEGER,
    points      INTEGER      NOT NULL DEFAULT 0 CHECK (points >= 0),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE user_badges (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    badge_id  UUID        NOT NULL REFERENCES badges (id),
    earned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_badges UNIQUE (user_id, badge_id)
);
CREATE INDEX idx_user_badges_user ON user_badges (user_id, earned_at DESC);

CREATE TABLE challenges (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title         VARCHAR(150)  NOT NULL,
    description   VARCHAR(1000) NOT NULL,
    metric        VARCHAR(30)   NOT NULL,
    target_value  INTEGER       NOT NULL CHECK (target_value > 0),
    start_date    DATE          NOT NULL,
    end_date      DATE          NOT NULL,
    points_reward INTEGER       NOT NULL DEFAULT 0 CHECK (points_reward >= 0),
    badge_id      UUID REFERENCES badges (id),
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_challenges_dates CHECK (end_date >= start_date)
);
CREATE INDEX idx_challenges_active_end ON challenges (active, end_date);

CREATE TABLE user_challenges (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    challenge_id      UUID        NOT NULL REFERENCES challenges (id),
    progress          INTEGER     NOT NULL DEFAULT 0 CHECK (progress >= 0),
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    joined_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    joined_local_date DATE        NOT NULL,
    completed_at      TIMESTAMPTZ,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_challenges UNIQUE (user_id, challenge_id),
    CONSTRAINT chk_user_challenges_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'EXPIRED'))
);
CREATE INDEX idx_user_challenges_user ON user_challenges (user_id, status);
CREATE INDEX idx_user_challenges_challenge ON user_challenges (challenge_id);

CREATE TABLE point_transactions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    points      INTEGER     NOT NULL CHECK (points > 0),
    source_type VARCHAR(30) NOT NULL,
    source_id   UUID        NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_point_source UNIQUE (user_id, source_type, source_id)
);
CREATE INDEX idx_point_transactions_user_time ON point_transactions (user_id, occurred_at);
