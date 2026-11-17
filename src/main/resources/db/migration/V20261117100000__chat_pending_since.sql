ALTER TABLE chat_messages
    ADD COLUMN pending_since TIMESTAMPTZ;

UPDATE chat_messages
SET pending_since = created_at
WHERE status = 'PENDING' AND pending_since IS NULL;

CREATE INDEX idx_chat_messages_pending_since
    ON chat_messages (status, pending_since)
    WHERE status = 'PENDING';
