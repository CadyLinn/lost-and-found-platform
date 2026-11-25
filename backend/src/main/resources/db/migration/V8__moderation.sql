-- Hiding a post (admins only). A hidden post keeps its data; it just isn't shown to the public.
ALTER TABLE items
    ADD COLUMN hidden_at     TIMESTAMPTZ,
    ADD COLUMN hidden_by     BIGINT REFERENCES users (id),
    ADD COLUMN hidden_reason VARCHAR(200);

-- Users report posts; admins review the reports.
CREATE TABLE reports (
    id          BIGSERIAL    PRIMARY KEY,
    item_id     BIGINT       NOT NULL REFERENCES items (id) ON DELETE CASCADE,
    reporter_id BIGINT       NOT NULL REFERENCES users (id),
    reason      VARCHAR(20)  NOT NULL CHECK (reason IN ('SPAM', 'SCAM', 'INAPPROPRIATE', 'OTHER')),
    details     VARCHAR(500),
    status      VARCHAR(20)  NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    handled_at  TIMESTAMPTZ,
    handled_by  BIGINT       REFERENCES users (id),
    CONSTRAINT uq_reports_item_reporter UNIQUE (item_id, reporter_id)
);

CREATE INDEX idx_reports_status_created ON reports (status, created_at DESC);

-- The author is told when their post is hidden.
ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('CLAIM_RECEIVED', 'CLAIM_APPROVED', 'CLAIM_REJECTED', 'ITEM_RETURNED', 'MATCH_FOUND', 'ITEM_HIDDEN'));
