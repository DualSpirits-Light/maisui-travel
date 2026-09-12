ALTER TABLE licenses ADD COLUMN offline_seconds INTEGER CHECK (offline_seconds IS NULL OR offline_seconds >= 0);
ALTER TABLE licenses ADD COLUMN archived_at INTEGER;
ALTER TABLE licenses ADD COLUMN frozen_at INTEGER;
ALTER TABLE licenses ADD COLUMN frozen_remaining_seconds INTEGER CHECK (frozen_remaining_seconds IS NULL OR frozen_remaining_seconds >= 0);

UPDATE licenses SET offline_seconds = lease_days * 86400;

CREATE INDEX licenses_archived_at_idx ON licenses(archived_at);
