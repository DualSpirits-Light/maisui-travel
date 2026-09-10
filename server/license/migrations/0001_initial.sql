PRAGMA foreign_keys = ON;

CREATE TABLE licenses (
  id TEXT PRIMARY KEY,
  code_hmac TEXT NOT NULL UNIQUE,
  subject TEXT NOT NULL,
  expires_at INTEGER,
  max_devices INTEGER NOT NULL CHECK (max_devices BETWEEN 1 AND 100),
  lease_days INTEGER NOT NULL CHECK (lease_days BETWEEN 1 AND 7),
  revoked_at INTEGER,
  created_at INTEGER NOT NULL
);

CREATE TABLE devices (
  license_id TEXT NOT NULL REFERENCES licenses(id) ON DELETE CASCADE,
  device_id TEXT NOT NULL,
  secret_hash TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (license_id, device_id)
);

CREATE INDEX devices_license_id_idx ON devices(license_id);

CREATE TABLE rate_limits (
  bucket TEXT NOT NULL,
  minute INTEGER NOT NULL,
  count INTEGER NOT NULL,
  PRIMARY KEY (bucket, minute)
);

CREATE INDEX rate_limits_minute_idx ON rate_limits(minute);
