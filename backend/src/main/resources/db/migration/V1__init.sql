CREATE TABLE categories (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

INSERT INTO categories (name) VALUES
    ('Phone'),
    ('Wallet'),
    ('Keys'),
    ('Bag'),
    ('ID Card'),
    ('Other');
