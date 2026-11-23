-- CHAT module schema. UUID matches users.id.

-- ---------------------------------------------------------------------------
-- conversations
-- ---------------------------------------------------------------------------
CREATE TABLE conversations (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title                    VARCHAR(120),
    summary                  TEXT,
    message_count            INTEGER NOT NULL DEFAULT 0 CHECK (message_count >= 0),
    last_message_at          TIMESTAMPTZ,
    deleted_at               TIMESTAMPTZ,
    initial_client_message_id UUID,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_conversations_user_recent
    ON conversations (user_id, last_message_at DESC NULLS LAST, id DESC)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uq_conversations_initial_idempotency
    ON conversations (user_id, initial_client_message_id)
    WHERE initial_client_message_id IS NOT NULL;

CREATE INDEX idx_conversations_deleted_at
    ON conversations (deleted_at)
    WHERE deleted_at IS NOT NULL;

-- ---------------------------------------------------------------------------
-- chat_messages
-- ---------------------------------------------------------------------------
CREATE TABLE chat_messages (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id   UUID NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    role              VARCHAR(16) NOT NULL CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM')),
    content           TEXT,
    status            VARCHAR(16) NOT NULL DEFAULT 'COMPLETED'
                      CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    client_message_id UUID,
    parent_message_id UUID REFERENCES chat_messages (id),
    prompt_tokens     INTEGER,
    completion_tokens INTEGER,
    model_version     VARCHAR(64),
    latency_ms        INTEGER,
    metadata          JSONB,
    feedback          SMALLINT CHECK (feedback IN (-1, 1)),
    pending_since     TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_chat_messages_content_status CHECK (
        (status = 'COMPLETED' AND content IS NOT NULL)
        OR (status IN ('PENDING', 'FAILED') AND role = 'ASSISTANT' AND content IS NULL)
    )
);

CREATE INDEX idx_chat_messages_conv_time
    ON chat_messages (conversation_id, created_at, id);

CREATE UNIQUE INDEX uq_chat_messages_idempotency
    ON chat_messages (conversation_id, client_message_id)
    WHERE client_message_id IS NOT NULL;

CREATE INDEX idx_chat_messages_pending_since
    ON chat_messages (status, pending_since)
    WHERE status = 'PENDING';

CREATE INDEX idx_chat_messages_parent
    ON chat_messages (parent_message_id)
    WHERE parent_message_id IS NOT NULL;

CREATE UNIQUE INDEX uq_chat_messages_pending_assistant
    ON chat_messages (conversation_id)
    WHERE role = 'ASSISTANT' AND status = 'PENDING';

-- ---------------------------------------------------------------------------
-- notification_settings (table owned by another module)
-- ---------------------------------------------------------------------------
ALTER TABLE notification_settings
    ADD COLUMN chat_reply_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- ---------------------------------------------------------------------------
-- users (table owned by another module)
-- ---------------------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN allergies JSONB NOT NULL DEFAULT '[]'::jsonb;
