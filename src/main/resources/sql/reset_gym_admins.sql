-- Reset all gym admins and recreate one admin per gym.
-- MySQL 8+
-- Default password hash here is bcrypt for: Admin@12

START TRANSACTION;

-- 1) Disconnect gyms from current gym admins
UPDATE gyms
SET owner_user_id = NULL
WHERE owner_user_id IN (
    SELECT id FROM users WHERE role IN ('GYM_ADMIN', 'ROLE_GYM_ADMIN')
);

-- 2) Remove all gym-admin users
DELETE FROM users
WHERE role IN ('GYM_ADMIN', 'ROLE_GYM_ADMIN');

-- 3) Recreate 1 gym admin for each gym
INSERT INTO users (
    username,
    password,
    phone,
    email,
    first_name,
    last_name,
    verified,
    role,
    gym_id
)
SELECT
    CONCAT('gymadmin_', g.id) AS username,
    '$2y$10$GP3/dSZS3ZV1vdO9xD2VFedoPmV1YaQ7HydLmcULi8xic4MVvZnr2' AS password,
    CONCAT('8', LPAD(MOD(g.id, 10000000), 7, '0')) AS phone,
    CONCAT('gymadmin_', g.id, '@example.com') AS email,
    'Gym' AS first_name,
    'Admin' AS last_name,
    1 AS verified,
    'GYM_ADMIN' AS role,
    g.id AS gym_id
FROM gyms g;

-- 4) Assign each created admin as gym owner
UPDATE gyms g
JOIN users u ON u.gym_id = g.id AND u.role = 'GYM_ADMIN'
SET g.owner_user_id = u.id;

COMMIT;

-- Verification queries
SELECT COUNT(*) AS gym_count FROM gyms;
SELECT COUNT(*) AS gym_admin_count FROM users WHERE role = 'GYM_ADMIN';
SELECT g.id AS gym_id, g.name, u.username, u.email, u.phone
FROM gyms g
LEFT JOIN users u ON u.id = g.owner_user_id
ORDER BY g.id;
