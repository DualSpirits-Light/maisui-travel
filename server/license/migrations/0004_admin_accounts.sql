-- Additive: existing licenses, devices, shares and rate limits remain untouched.
CREATE TABLE admin_accounts (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  password_salt TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  password_iterations INTEGER NOT NULL,
  session_version INTEGER NOT NULL DEFAULT 1,
  used_recovery_hash TEXT NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE TABLE admin_sessions (
  token_hash TEXT PRIMARY KEY,
  account_id INTEGER NOT NULL REFERENCES admin_accounts(id) ON DELETE CASCADE,
  session_version INTEGER NOT NULL,
  expires_at INTEGER NOT NULL
);
CREATE INDEX admin_sessions_expiry ON admin_sessions(expires_at);
