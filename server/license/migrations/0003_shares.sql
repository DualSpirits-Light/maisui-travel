CREATE TABLE shares (
 id TEXT PRIMARY KEY,
 size INTEGER NOT NULL CHECK(size BETWEEN 1 AND 33554432),
 created_at INTEGER NOT NULL,
 expires_at INTEGER NOT NULL,
 ready INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX shares_expiry_idx ON shares(expires_at);
CREATE TABLE share_limits (
 bucket TEXT PRIMARY KEY,
 count INTEGER NOT NULL,
 expires_at INTEGER NOT NULL
);
