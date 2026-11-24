-- New notification type: "a new post might be the thing you're looking for".
ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('CLAIM_RECEIVED', 'CLAIM_APPROVED', 'CLAIM_REJECTED', 'ITEM_RETURNED', 'MATCH_FOUND'));
