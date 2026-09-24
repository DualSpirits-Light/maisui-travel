-- Additive migration. Historical HMACs and all existing device credentials remain unchanged.
ALTER TABLE licenses ADD COLUMN code_ciphertext TEXT;
ALTER TABLE licenses ADD COLUMN code_key_id TEXT;
ALTER TABLE licenses ADD COLUMN source_license_id TEXT;

-- No foreign key: retain the audit trail after deliberate license deletion.
CREATE TABLE license_code_audit (
  id TEXT PRIMARY KEY,
  license_id TEXT NOT NULL,
  actor TEXT NOT NULL,
  action TEXT NOT NULL,
  outcome TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX license_code_audit_lookup ON license_code_audit(license_id, created_at);
