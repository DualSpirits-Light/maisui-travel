import assert from "node:assert/strict";
import test from "node:test";

const { readJson } = await import("../src/index.js");

test("bounded JSON reader cancels a streaming body as soon as it exceeds 4096 bytes", async () => {
  let cancelled = false;
  let pushes = 0;
  const body = new ReadableStream({
    pull(controller) {
      pushes += 1;
      controller.enqueue(new Uint8Array(1024));
      if (pushes > 20) controller.close();
    },
    cancel() {
      cancelled = true;
    },
  });
  const request = new Request("https://license.example/v1/activate", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body,
    duplex: "half",
  });
  await assert.rejects(readJson(request), (caught) => caught.status === 413 && caught.code === "BODY_TOO_LARGE");
  assert.equal(cancelled, true);
  assert.ok(pushes <= 6);
});

test("bounded JSON reader accepts a small object and rejects invalid JSON", async () => {
  const valid = new Request("https://license.example", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ value: "ok" }),
  });
  assert.deepEqual(await readJson(valid), { value: "ok" });
  const invalid = new Request("https://license.example", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: "{",
  });
  await assert.rejects(readJson(invalid), (caught) => caught.status === 400 && caught.code === "INVALID_REQUEST");
});
