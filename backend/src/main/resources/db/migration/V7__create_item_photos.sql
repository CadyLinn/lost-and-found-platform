CREATE TABLE item_photos (
    id         BIGSERIAL   PRIMARY KEY,
    item_id    BIGINT      NOT NULL REFERENCES items (id) ON DELETE CASCADE,
    -- Random UUID chosen by the server; the file is <key>.jpg and the thumbnail <key>_thumb.jpg.
    photo_key  UUID        NOT NULL UNIQUE,
    sort_order INT         NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_item_photos_item ON item_photos (item_id, sort_order);
