import { test, expect, APIRequestContext } from '@playwright/test';

const AUDIT_URL = process.env.AUDIT_URL || 'http://localhost:8087';

// trader1 UUID from auth-service seed
const TRADER1_USER_ID = 'a1000000-0000-0000-0000-000000000001';

let apiRequest: APIRequestContext;

test.describe('Audit Service', () => {
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
    const response = await apiRequest.get('/audit/events?page=0&size=10');
    expect(response.status()).toBe(200);

    const body = await response.json();
    expect(body).toHaveProperty('content');
    expect(body).toHaveProperty('totalElements');
    expect(Array.isArray(body.content)).toBe(true);
  });

  test('audit events have required fields when present', async () => {
    const response = await apiRequest.get('/audit/events?page=0&size=10');
    const body = await response.json();

    if (body.content.length > 0) {
      const event = body.content[0];
      expect(event).toHaveProperty('id');
      expect(event).toHaveProperty('eventType');
      expect(event).toHaveProperty('occurredAt');
    }
  });

  test('GET /audit/events/user/{userId} returns events for trader1', async () => {
    const response = await apiRequest.get(`/audit/events/user/${TRADER1_USER_ID}?page=0&size=10`);
    expect(response.status()).toBe(200);

    const body = await response.json();
    expect(body).toHaveProperty('content');
    expect(Array.isArray(body.content)).toBe(true);
  });

  test('GET /audit/events/type/{eventType} returns events by type', async () => {
    const response = await apiRequest.get('/audit/events/type/ORDER_SUBMITTED?page=0&size=10');
    expect(response.status()).toBe(200);

    const body = await response.json();
    expect(body).toHaveProperty('content');
    expect(Array.isArray(body.content)).toBe(true);
  });

  test('GET /audit/events/order/{orderId} returns 200 or 404 for a non-existent order', async () => {
    const fakeOrderId = '00000000-0000-0000-0000-000000000000';
    const response = await apiRequest.get(`/audit/events/order/${fakeOrderId}`);
    // Returns empty list (200) or 404 depending on implementation
    expect([200, 404]).toContain(response.status());

    if (response.status() === 200) {
      const body = await response.json();
      expect(Array.isArray(body)).toBe(true);
    }
  });

  test('audit events are append-only — total count does not decrease between polls', async () => {
    const first = await apiRequest.get('/audit/events?page=0&size=1');
    const firstBody = await first.json();
    const countBefore = firstBody.totalElements;

    const second = await apiRequest.get('/audit/events?page=0&size=1');
    const secondBody = await second.json();
    const countAfter = secondBody.totalElements;

    expect(countAfter).toBeGreaterThanOrEqual(countBefore);
  });
});
