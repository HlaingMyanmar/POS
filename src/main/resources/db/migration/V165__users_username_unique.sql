-- Ensure every user has a unique, non-null username before enforcing the constraint.
-- When appending '_<id>', truncate the *prefix* so the suffix is never chopped by LEFT(..., 50).

UPDATE users
SET username = NULL
WHERE username IS NOT NULL AND TRIM(username) = '';

-- Backfill null usernames: keep '_<id>' intact (email local-part may be long).
UPDATE users u
SET u.username = CONCAT(
        LEFT(
                COALESCE(NULLIF(SUBSTRING_INDEX(u.email, '@', 1), ''), 'user'),
                GREATEST(1, 50 - CHAR_LENGTH(CONCAT('_', u.id)))
        ),
        '_',
        u.id
)
WHERE u.username IS NULL;

-- Deduplicate: truncate prefix only, always preserve '_<id>' suffix.
UPDATE users u
JOIN (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY username ORDER BY id) AS rn
    FROM users
    WHERE username IS NOT NULL
) ranked ON ranked.id = u.id
SET u.username = CONCAT(
        LEFT(u.username, GREATEST(1, 50 - CHAR_LENGTH(CONCAT('_', u.id)))),
        '_',
        u.id
)
WHERE ranked.rn > 1;

-- Second pass: a rename can collide with an existing '<prefix>_<id>' value.
-- Use id + short hash (always <= 50 chars) so the unique index cannot fail on truncation.
UPDATE users u
JOIN (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY username ORDER BY id) AS rn
    FROM users
    WHERE username IS NOT NULL
) ranked ON ranked.id = u.id
SET u.username = LEFT(
        CONCAT(
                'u',
                u.id,
                '_',
                LOWER(SUBSTRING(SHA2(CONCAT(u.id, '#', IFNULL(u.email, '')), 256), 1, 12))
        ),
        50
)
WHERE ranked.rn > 1;

ALTER TABLE users
    MODIFY COLUMN username VARCHAR(50) NOT NULL,
    ADD UNIQUE INDEX uk_users_username (username);
