CREATE TABLE items (
    id                    BIGSERIAL        PRIMARY KEY,
    owner_id              BIGINT           NOT NULL REFERENCES users (id),
    type                  VARCHAR(10)      NOT NULL CHECK (type IN ('LOST', 'FOUND')),
    title                 VARCHAR(100)     NOT NULL,
    description           TEXT,
    category_id           BIGINT           NOT NULL REFERENCES categories (id),
    location_text         VARCHAR(200)     NOT NULL,
    lat                   DOUBLE PRECISION,
    lng                   DOUBLE PRECISION,
    event_date            DATE             NOT NULL,
    status                VARCHAR(20)      NOT NULL DEFAULT 'OPEN'
                          CHECK (status IN ('OPEN', 'CLAIMED', 'RETURNED', 'CLOSED')),
    verification_question VARCHAR(200),
    created_at            TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ      NOT NULL DEFAULT now()
);

CREATE INDEX idx_items_type_status_category ON items (type, status, category_id);
CREATE INDEX idx_items_event_date ON items (event_date);
CREATE INDEX idx_items_owner ON items (owner_id);
