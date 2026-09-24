const MONEY_RE = /^(0|[1-9][0-9]{0,8})(?:\.([0-9]{1,2}))?$/u;
const CURRENCY_RE = /^[A-Z]{3}$/u;
const DATE_RE = /^[0-9]{4}-[0-9]{2}-[0-9]{2}$/u;
const ID_RE = /^[A-Za-z0-9_-]{8,128}$/u;

function invalid(HttpError, field) {
  throw new HttpError(400, "INVALID_REQUEST", `Invalid ${field}`);
}

function text(value, field, maxLength, HttpError, required = true) {
  if (value === undefined && !required) return null;
  if (typeof value !== "string") invalid(HttpError, field);
  const normalized = value.trim();
  if ((required && normalized.length === 0) || normalized.length > maxLength) invalid(HttpError, field);
  return normalized;
}

function money(value, HttpError) {
  const amount = text(value, "amount", 12, HttpError);
  const match = MONEY_RE.exec(amount);
  if (!match) invalid(HttpError, "amount");
  return Number(match[1]) * 100 + Number((match[2] || "").padEnd(2, "0"));
}

function displayAmount(amountMinor) {
  const whole = Math.floor(amountMinor / 100);
  return `${whole}.${String(amountMinor % 100).padStart(2, "0")}`;
}

function date(value, HttpError) {
  const normalized = text(value, "date", 10, HttpError);
  const parsed = new Date(`${normalized}T00:00:00Z`);
  if (!DATE_RE.test(normalized) || Number.isNaN(parsed.valueOf()) || parsed.toISOString().slice(0, 10) !== normalized) invalid(HttpError, "date");
  return normalized;
}

function currency(value, HttpError) {
  const normalized = text(value === undefined ? "CNY" : value, "currency", 3, HttpError).toUpperCase();
  if (!CURRENCY_RE.test(normalized)) invalid(HttpError, "currency");
  return normalized;
}

function optionalMessage(value, HttpError) {
  if (value === null) return null;
  const message = text(value, "message", 500, HttpError, false);
  return message || null;
}

function publicRecord(row) {
  const record = {
    platform: row.platform,
    name: row.name,
    amount: displayAmount(row.amountMinor),
    currency: row.currency,
    date: row.date,
  };
  if (row.message) record.message = row.message;
  return record;
}

function adminRecord(row) {
  return {
    id: row.id,
    ...publicRecord(row),
    isPublic: Boolean(row.isPublic),
    createdAt: row.createdAt,
    updatedAt: row.updatedAt,
  };
}

function validatePublic(value, HttpError) {
  if (typeof value !== "boolean") invalid(HttpError, "isPublic");
  return value;
}

function rowQuery() {
  return `SELECT id, platform, donor_name AS name, amount_minor AS amountMinor, currency,
    donated_on AS date, message, is_public AS isPublic, created_at AS createdAt, updated_at AS updatedAt
    FROM donations`;
}

export function createDonationHandlers({ readJson, HttpError, json }) {
  async function create(request, env) {
    const body = await readJson(request);
    const now = Math.floor(Date.now() / 1000);
    const row = {
      id: crypto.randomUUID(),
      platform: text(body.platform, "platform", 40, HttpError),
      name: text(body.name, "name", 80, HttpError),
      amountMinor: money(body.amount, HttpError),
      currency: currency(body.currency, HttpError),
      date: date(body.date, HttpError),
      message: optionalMessage(body.message, HttpError),
      isPublic: body.isPublic === undefined ? false : validatePublic(body.isPublic, HttpError),
      createdAt: now,
      updatedAt: now,
    };
    await env.DB.prepare(`INSERT INTO donations
      (id, platform, donor_name, amount_minor, currency, donated_on, message, is_public, created_at, updated_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`)
      .bind(row.id, row.platform, row.name, row.amountMinor, row.currency, row.date, row.message, row.isPublic ? 1 : 0, now, now).run();
    return json(201, adminRecord(row));
  }

  async function list(env) {
    const result = await env.DB.prepare(`${rowQuery()} ORDER BY donated_on DESC, created_at DESC LIMIT 500`).all();
    return json(200, { donations: (result.results || []).map(adminRecord) });
  }

  async function update(id, request, env) {
    if (!ID_RE.test(id)) invalid(HttpError, "donation id");
    const body = await readJson(request);
    const allowed = ["platform", "name", "amount", "currency", "date", "message", "isPublic"];
    if (!allowed.some((key) => Object.hasOwn(body, key)) || Object.keys(body).some((key) => !allowed.includes(key))) invalid(HttpError, "donation fields");
    const current = await env.DB.prepare(`${rowQuery()} WHERE id = ?`).bind(id).first();
    if (!current) throw new HttpError(404, "DONATION_NOT_FOUND", "Donation not found");
    const row = {
      id,
      platform: Object.hasOwn(body, "platform") ? text(body.platform, "platform", 40, HttpError) : current.platform,
      name: Object.hasOwn(body, "name") ? text(body.name, "name", 80, HttpError) : current.name,
      amountMinor: Object.hasOwn(body, "amount") ? money(body.amount, HttpError) : current.amountMinor,
      currency: Object.hasOwn(body, "currency") ? currency(body.currency, HttpError) : current.currency,
      date: Object.hasOwn(body, "date") ? date(body.date, HttpError) : current.date,
      message: Object.hasOwn(body, "message") ? optionalMessage(body.message, HttpError) : current.message,
      isPublic: Object.hasOwn(body, "isPublic") ? validatePublic(body.isPublic, HttpError) : Boolean(current.isPublic),
      createdAt: current.createdAt,
      updatedAt: Math.floor(Date.now() / 1000),
    };
    const columns = {
      platform: ["platform", row.platform], name: ["donor_name", row.name],
      amount: ["amount_minor", row.amountMinor], currency: ["currency", row.currency],
      date: ["donated_on", row.date], message: ["message", row.message],
      isPublic: ["is_public", row.isPublic ? 1 : 0],
    };
    const changed = allowed.filter(key => Object.hasOwn(body, key)).map(key => columns[key]);
    const result = await env.DB.prepare(`UPDATE donations SET ${changed.map(([column]) => `${column} = ?`).join(", ")}, updated_at = ? WHERE id = ?`)
      .bind(...changed.map(([, value]) => value), row.updatedAt, id).run();
    if (!result.success || result.meta?.changes !== 1) throw new HttpError(404, "DONATION_NOT_FOUND", "Donation not found");
    const updated = await env.DB.prepare(`${rowQuery()} WHERE id = ?`).bind(id).first();
    return json(200, adminRecord(updated));
  }

  async function remove(id, env) {
    if (!ID_RE.test(id)) invalid(HttpError, "donation id");
    const result = await env.DB.prepare("DELETE FROM donations WHERE id = ?").bind(id).run();
    if (!result.success || result.meta?.changes !== 1) throw new HttpError(404, "DONATION_NOT_FOUND", "Donation not found");
    return json(200, { id, deleted: true });
  }

  async function publicFeed(env) {
    const result = await env.DB.prepare(`SELECT platform, donor_name AS name, amount_minor AS amountMinor, currency,
      donated_on AS date, message FROM donations WHERE is_public = 1 ORDER BY donated_on DESC, created_at DESC LIMIT 200`).all();
    return json(200, { records: (result.results || []).map(publicRecord) }, { "Cache-Control": "public, max-age=300" });
  }

  return { create, list, update, remove, publicFeed };
}
