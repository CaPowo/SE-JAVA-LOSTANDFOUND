CREATE TABLE IF NOT EXISTS user (
    id            TEXT PRIMARY KEY,
    username      TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    salt          TEXT NOT NULL,
    role          TEXT NOT NULL DEFAULT 'ADMIN',
    created_at    TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS lost_item (
    id             TEXT PRIMARY KEY,
    name           TEXT NOT NULL,
    category       TEXT,
    location       TEXT,
    found_time     TEXT,
    finder_contact TEXT,
    description    TEXT,
    image_path     TEXT,
    status         TEXT NOT NULL DEFAULT 'PENDING',
    claimer        TEXT,
    claim_time     TEXT,
    created_at     TEXT NOT NULL,
    updated_at     TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS claim_record (
    id         TEXT PRIMARY KEY,
    item_id    TEXT NOT NULL,
    claimer    TEXT NOT NULL,
    contact    TEXT,
    claim_time TEXT NOT NULL,
    operator   TEXT,
    FOREIGN KEY (item_id) REFERENCES lost_item(id)
);
