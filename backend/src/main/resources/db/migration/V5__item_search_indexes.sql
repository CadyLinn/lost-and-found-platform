-- Keyword search uses ILIKE '%word%'. A trigram (pg_trgm) GIN index makes that fast,
-- and unlike Postgres full-text search it also works for Chinese, which has no spaces between words.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_items_title_trgm ON items USING gin (title gin_trgm_ops);
CREATE INDEX idx_items_description_trgm ON items USING gin (description gin_trgm_ops);
CREATE INDEX idx_items_location_trgm ON items USING gin (location_text gin_trgm_ops);

-- "Near me" filters on a lat/lng box first, then exact distance.
CREATE INDEX idx_items_lat_lng ON items (lat, lng) WHERE lat IS NOT NULL;
