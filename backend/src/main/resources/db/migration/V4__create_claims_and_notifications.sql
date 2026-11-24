-- A claim is a non-owner saying "this is mine" (on a FOUND post) or "I found it" (on a LOST post).
CREATE TABLE claims (
    id          BIGSERIAL     PRIMARY KEY,
    item_id     BIGINT        NOT NULL REFERENCES items (id) ON DELETE CASCADE,
    claimant_id BIGINT        NOT NULL REFERENCES users (id),
    answer      VARCHAR(500),
    message     VARCHAR(1000) NOT NULL,
    status      VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    decided_at  TIMESTAMPTZ,
    CONSTRAINT uq_claims_item_claimant UNIQUE (item_id, claimant_id)
);

CREATE INDEX idx_claims_claimant ON claims (claimant_id);

CREATE TABLE notifications (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id),
    type       VARCHAR(30) NOT NULL
               CHECK (type IN ('CLAIM_RECEIVED', 'CLAIM_APPROVED', 'CLAIM_REJECTED', 'ITEM_RETURNED')),
    item_id    BIGINT      REFERENCES items (id) ON DELETE CASCADE,
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);
