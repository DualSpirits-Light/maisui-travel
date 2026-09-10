import assert from "node:assert/strict";
import { execFile, execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { promisify } from "node:util";
import test from "node:test";

const execFileAsync = promisify(execFile);
let sqliteAvailable = true;
try {
  execFileSync("sqlite3", ["-version"], { stdio: "ignore" });
} catch {
  sqliteAvailable = false;
}

test("conditional device insert enforces maxDevices across concurrent SQLite writers", { skip: !sqliteAvailable }, async () => {
  const directory = mkdtempSync(join(tmpdir(), "maisui-license-"));
  const database = join(directory, "test.sqlite");
  try {
    const migration = readFileSync(new URL("../migrations/0001_initial.sql", import.meta.url), "utf8");
    execFileSync("sqlite3", [database], { input: migration });
    execFileSync("sqlite3", [database], {
      input: `INSERT INTO licenses VALUES ('lic_test','hmac','subject',NULL,1,7,NULL,1700000000);`,
    });

    const insert = (deviceId) => `
      PRAGMA busy_timeout=10000;
      INSERT INTO devices (license_id, device_id, secret_hash, created_at, updated_at)
      SELECT l.id, '${deviceId}', 'hash-${deviceId}', 1700000001, 1700000001 FROM licenses l
      WHERE l.id = 'lic_test'
        AND (EXISTS (SELECT 1 FROM devices d WHERE d.license_id=l.id AND d.device_id='${deviceId}')
          OR (SELECT COUNT(*) FROM devices d WHERE d.license_id=l.id) < l.max_devices)
      ON CONFLICT(license_id, device_id) DO UPDATE SET secret_hash=excluded.secret_hash;
    `;
    await Promise.all(Array.from({ length: 8 }, (_, index) => execFileAsync("sqlite3", [database, insert(`device-${index}`)])));
    const count = Number(execFileSync("sqlite3", [database, "SELECT COUNT(*) FROM devices;"]).toString().trim());
    assert.equal(count, 1);

    execFileSync("sqlite3", [database], { input: insert("device-0") });
    const stillOne = Number(execFileSync("sqlite3", [database, "SELECT COUNT(*) FROM devices;"]).toString().trim());
    assert.equal(stillOne, 1);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});
