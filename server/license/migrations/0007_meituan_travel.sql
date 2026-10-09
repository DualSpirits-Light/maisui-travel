-- One atomic INSERT reserves both daily quota and a short concurrency lease.
-- No FK: deleting a license must not refund the global provider budget.
CREATE TABLE travel_requests (
  id TEXT PRIMARY KEY,
  license_id TEXT NOT NULL,
  day INTEGER NOT NULL,
  active_until INTEGER NOT NULL
);
CREATE INDEX travel_requests_day ON travel_requests(day, license_id);
CREATE INDEX travel_requests_active ON travel_requests(active_until, license_id);
