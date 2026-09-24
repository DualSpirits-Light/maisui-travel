CREATE TABLE donations (
  id TEXT PRIMARY KEY,
  platform TEXT NOT NULL,
  donor_name TEXT NOT NULL,
  amount_minor INTEGER NOT NULL CHECK (amount_minor >= 0),
  currency TEXT NOT NULL,
  donated_on TEXT NOT NULL,
  message TEXT,
  is_public INTEGER NOT NULL DEFAULT 0 CHECK (is_public IN (0, 1)),
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE INDEX donations_public_feed_idx ON donations(is_public, donated_on DESC, created_at DESC);
