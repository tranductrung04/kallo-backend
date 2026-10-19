CREATE TABLE users (
                       id UUID PRIMARY KEY DEFAULT gen_random_uuid (),
                       email VARCHAR(255) NOT NULL,
                       email_verified BOOLEAN NOT NULL DEFAULT FALSE,
                       password_hash VARCHAR(100),
                       google_sub VARCHAR(64),
                       auth_provider VARCHAR(20) NOT NULL,
                       display_name VARCHAR(100) NOT NULL,
                       avatar_url VARCHAR(500),
                       role VARCHAR(20) NOT NULL DEFAULT 'USER',
                       status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                       timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
                       created_at TIMESTAMPTZ NOT NULL DEFAULT now (),
                       updated_at TIMESTAMPTZ NOT NULL DEFAULT now (),
                       CONSTRAINT chk_users_credential CHECK (
                           password_hash IS NOT NULL
                               OR google_sub IS NOT NULL
                           )
);


CREATE UNIQUE INDEX uq_users_email ON users (lower(email));


CREATE UNIQUE INDEX uq_users_google_sub ON users (google_sub)
    WHERE
  google_sub IS NOT NULL;


CREATE TABLE refresh_tokens (
                                id UUID PRIMARY KEY DEFAULT gen_random_uuid (),
                                user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                                family_id UUID NOT NULL,
                                token_hash VARCHAR(64) NOT NULL UNIQUE,
                                device_info VARCHAR(255),
                                expires_at TIMESTAMPTZ NOT NULL,
                                revoked_at TIMESTAMPTZ,
                                created_at TIMESTAMPTZ NOT NULL DEFAULT now ()
);


CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);


CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);


CREATE TABLE password_reset_tokens (
                                       id UUID PRIMARY KEY DEFAULT gen_random_uuid (),
                                       user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                                       code_hash VARCHAR(64) NOT NULL,
                                       attempts INTEGER NOT NULL DEFAULT 0,
                                       expires_at TIMESTAMPTZ NOT NULL,
                                       used_at TIMESTAMPTZ,
                                       created_at TIMESTAMPTZ NOT NULL DEFAULT now ()
);


CREATE INDEX idx_password_reset_user ON password_reset_tokens (user_id, created_at DESC);