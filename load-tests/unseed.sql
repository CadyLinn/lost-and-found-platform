-- Removes everything seed.sql added (claims and notifications on those posts go with them).
DELETE FROM items WHERE owner_id = (SELECT id FROM users WHERE email = 'loadtest@test.com');
DELETE FROM users WHERE email = 'loadtest@test.com';
