-- CHAT module schema. UUID matches users.id.

CREATE TABLE conversations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title           VARCHAR(120),
    summary         TEXT,
    message_count   INTEGER NOT NULL DEFAULT 0 CHECK (message_count >= 0),
    last_message_at TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_conversations_user_recent
    ON conversations (user_id, last_message_at DESC NULLS LAST, id DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE chat_messages (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id   UUID NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    role              VARCHAR(16) NOT NULL CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM')),
    content           TEXT NOT NULL,
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
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_messages_conv_time
    ON chat_messages (conversation_id, created_at, id);

CREATE UNIQUE INDEX uq_chat_messages_idempotency
    ON chat_messages (conversation_id, client_message_id)
    WHERE client_message_id IS NOT NULL;

CREATE INDEX idx_chat_messages_pending
    ON chat_messages (status, created_at)
    WHERE status = 'PENDING';
