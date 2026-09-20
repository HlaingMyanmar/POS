-- Ensure every user has a unique, non-null username before enforcing the constraint.
-- Duplicate resolution uses id-scoped values only (never LEFT()-truncate a uniqueness suffix).

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

-- Deduplicate: losers get a guaranteed-unique name derived from id (always <= 50 chars).
-- Form: u{id}_{12-hex} — unique per id, no suffix truncation risk.
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
                LOWER(SUBSTRING(SHA2(CONCAT(u.id, '#', IFNULL(u.email, ''), '#', IFNULL(u.username, '')), 256), 1, 12))
        ),
        50
)
WHERE ranked.rn > 1;

-- Final safety pass: if a rename collided with an existing rn=1 value, force another unique form.
-- Prefix 'x{id}_' is unique per primary key, so the unique index cannot fail on truncation.
UPDATE users u
JOIN (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY username ORDER BY id) AS rn
    FROM users
    WHERE username IS NOT NULL
) ranked ON ranked.id = u.id
SET u.username = LEFT(
        CONCAT(
                'x',
                u.id,
                '_',
                LOWER(SUBSTRING(SHA2(CONCAT('retry#', u.id, '#', IFNULL(u.email, '')), 256), 1, 16))
        ),
        50
)
WHERE ranked.rn > 1;

ALTER TABLE users
    MODIFY COLUMN username VARCHAR(50) NOT NULL,
    ADD UNIQUE INDEX uk_users_username (username);
