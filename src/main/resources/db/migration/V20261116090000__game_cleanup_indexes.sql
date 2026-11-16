CREATE INDEX idx_friendships_declined_responded
    ON friendships (responded_at)
    WHERE status = 'DECLINED';
