-- Test data for load testing: one user and 2,000 realistic posts around NTU campus.
-- Run:   docker exec -i infra-postgres-1 psql -U lostfound -d lostfound < load-tests/seed.sql
-- Undo:  docker exec -i infra-postgres-1 psql -U lostfound -d lostfound < load-tests/unseed.sql

CREATE EXTENSION IF NOT EXISTS pgcrypto; -- for crypt(): makes a BCrypt hash Spring Security accepts

INSERT INTO users (email, password_hash, display_name)
VALUES ('loadtest@test.com', crypt('password123', gen_salt('bf', 10)), 'Load Test')
ON CONFLICT (email) DO NOTHING;

WITH
  colors(c) AS (VALUES ('黑色'), ('白色'), ('藍色'), ('紅色'), ('咖啡色'), ('灰色'), ('粉紅色'), ('綠色')),
  things(category_id, name) AS (VALUES
      (1, 'iPhone'), (1, '三星手機'), (1, '手機'),
      (2, '皮夾'), (2, '錢包'), (2, '零錢包'),
      (3, '鑰匙'), (3, '機車鑰匙'), (3, '門禁卡鑰匙圈'),
      (4, '後背包'), (4, '帆布袋'), (4, '筆電包'),
      (5, '學生證'), (5, '悠遊卡'), (5, '身分證'),
      (6, '雨傘'), (6, '水壺'), (6, '耳機')),
  places(name, lat, lng) AS (VALUES
      ('總圖書館', 25.0173, 121.5397), ('學生活動中心', 25.0182, 121.5402),
      ('第一學生餐廳', 25.0158, 121.5385), ('體育館', 25.0215, 121.5352),
      ('新生大樓', 25.0189, 121.5375), ('醉月湖', 25.0201, 121.5389),
      ('公館捷運站', 25.0147, 121.5343), ('辛亥路公車站', 25.0207, 121.5434))
INSERT INTO items (owner_id, type, title, description, category_id, location_text, lat, lng, event_date, status)
SELECT
    (SELECT id FROM users WHERE email = 'loadtest@test.com'),
    CASE WHEN n % 2 = 0 THEN 'LOST' ELSE 'FOUND' END,
    CASE WHEN n % 2 = 0 THEN '遺失' ELSE '撿到' END || color.c || thing.name,
    '在' || place.name || '附近，' || color.c || thing.name || '，編號 ' || n,
    thing.category_id,
    place.name,
    place.lat + (random() - 0.5) * 0.004,
    place.lng + (random() - 0.5) * 0.004,
    CURRENT_DATE - (random() * 60)::int,
    CASE WHEN n % 10 = 0 THEN 'RETURNED' ELSE 'OPEN' END
FROM generate_series(1, 2000) AS n
CROSS JOIN LATERAL (SELECT c FROM colors OFFSET (n * 7) % 8 LIMIT 1) AS color
CROSS JOIN LATERAL (SELECT * FROM things OFFSET (n * 13) % 18 LIMIT 1) AS thing
CROSS JOIN LATERAL (SELECT * FROM places OFFSET (n * 5) % 8 LIMIT 1) AS place;

ANALYZE items;
SELECT count(*) AS seeded_items FROM items WHERE owner_id = (SELECT id FROM users WHERE email = 'loadtest@test.com');
