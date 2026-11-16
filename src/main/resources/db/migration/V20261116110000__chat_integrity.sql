-- Follow-up constraints for chat schema. Keep original migration immutable after deployment.

ALTER TABLE conversations
    ADD COLUMN initial_client_message_id UUID;

CREATE UNIQUE INDEX uq_conversations_initial_idempotency
    ON conversations (user_id, initial_client_message_id)
    WHERE initial_client_message_id IS NOT NULL;

CREATE INDEX idx_conversations_deleted_at
    ON conversations (deleted_at)
    WHERE deleted_at IS NOT NULL;

ALTER TABLE chat_messages
    ALTER COLUMN content DROP NOT NULL;

ALTER TABLE chat_messages
    ADD CONSTRAINT ck_chat_messages_content_status CHECK (
        (status = 'COMPLETED' AND content IS NOT NULL)
        OR (status IN ('PENDING', 'FAILED') AND role = 'ASSISTANT' AND content IS NULL)
    );

CREATE INDEX idx_chat_messages_parent
    ON chat_messages (parent_message_id)
    WHERE parent_message_id IS NOT NULL;

CREATE UNIQUE INDEX uq_chat_messages_pending_assistant
    ON chat_messages (conversation_id)
    WHERE role = 'ASSISTANT' AND status = 'PENDING';
