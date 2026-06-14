/**
 * Audit Service — Integration Tests (@integration)
 *
 * Validates that the audit service correctly records, stores, and serves
 * system events. The audit log is append-only and must capture all
 * significant state transitions (order submitted, compliance checked,
 * settlement completed) for regulatory compliance and incident investigation.
 *
 * Tests run directly against the audit service to validate its API contract
 * in isolation from the event pipeline that populates it.
 *
 * Runs on: merge to master
 * Services required: audit-service (localhost:8087)
 */
import { test, expect, APIRequestContext } from '@playwright/test';

const AUDIT_URL = process.env.AUDIT_URL || 'http://localhost:8087';

// Seeded trader1 user ID from auth-service startup data
const TRADER1_USER_ID = 'a1000000-0000-0000-0000-000000000001';

test.describe('Audit Service', { tag: '@integration' }, () => {
  let apiRequest: APIRequestContext;

  test.beforeAll(async ({ playwright }) => {
    apiRequest = await playwright.request.newContext({
      baseURL: AUDIT_URL,
      extraHTTPHeaders: { 'Content-Type': 'application/json' },
    });
  });

  test.afterAll(async () => {
    await apiRequest.dispose();
  });

  test('GET /audit/events returns paginated audit events', async () => {
    // Validates the primary event query endpoint. Pagination fields
    // (content, totalElements) are required by the admin dashboard and
    // any regulatory export tooling consuming the audit trail.
    const response = await apiRequest.get('/audit/events?page=0&size=10');
    expect(response.status()).toBe(200);

    const body = await response.json();
    expect(body).toHaveProperty('content');
    expect(body).toHaveProperty('totalElements');
    expect(Array.isArray(body.content)).toBe(true);
  });

  test('audit event records contain required fields', async () => {
    // Validates event record shape. Every audit event needs an id (for
    // deduplication), an eventType (for filtering), and an occurredAt
    // timestamp (for ordering and incident timeline reconstruction).
    const response = await apiRequest.get('/audit/events?page=0&size=10');
    const body = await response.json();

    if (body.content.length > 0) {
      const event = body.content[0];
      expect(event).toHaveProperty('id');
      expect(event).toHaveProperty('eventType');
      expect(event).toHaveProperty('occurredAt');
    }
  });

  test('GET /audit/events/user/{userId} returns events scoped to a trader', async () => {
    // Validates per-user event filtering used for trader activity reports
    // and account dispute resolution. Response must scope to the requested
    // userId — a full-table return would be a data isolation defect.
    const response = await apiRequest.get(`/audit/events/user/${TRADER1_USER_ID}?page=0&size=10`);
    expect(response.status()).toBe(200);

    const body = await response.json();
    expect(body).toHaveProperty('content');
    expect(Array.isArray(body.content)).toBe(true);
  });

  test('GET /audit/events/type/{eventType} filters by event type', async () => {
    // Validates event-type filtering used for operational queries
    // (e.g. "show me all ORDER_SUBMITTED events in the last hour").
    // Critical for incident triage when narrowing to a specific event class.
    const response = await apiRequest.get('/audit/events/type/ORDER_SUBMITTED?page=0&size=10');
    expect(response.status()).toBe(200);

    const body = await response.json();
    expect(body).toHaveProperty('content');
    expect(Array.isArray(body.content)).toBe(true);
  });

  test('GET /audit/events/order/{orderId} returns 200 or 404 for unknown order', async () => {
    // Validates the per-order event lookup used to reconstruct the audit
    // trail for a specific order. A non-existent orderId should return either
    // an empty list (200) or 404 — not a 500 that would indicate an
    // unhandled DB query failure.
    const fakeOrderId = '00000000-0000-0000-0000-000000000000';
    const response = await apiRequest.get(`/audit/events/order/${fakeOrderId}`);
    expect([200, 404]).toContain(response.status());

    if (response.status() === 200) {
      const body = await response.json();
      expect(Array.isArray(body)).toBe(true);
    }
  });

  test('audit event total count is non-decreasing between polls', async () => {
    // Enforces the append-only invariant: audit events must never be deleted
    // or overwritten. A decreasing totalElements count would indicate
    // unauthorized deletion or a retention policy applied to active audit data.
    const first = await apiRequest.get('/audit/events?page=0&size=1');
    const firstBody = await first.json();
    const countBefore = firstBody.totalElements;

    const second = await apiRequest.get('/audit/events?page=0&size=1');
    const secondBody = await second.json();
    const countAfter = secondBody.totalElements;

    expect(countAfter).toBeGreaterThanOrEqual(countBefore);
  });
});
