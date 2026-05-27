import { test, expect, APIRequestContext } from '@playwright/test';

const LEDGER_URL = process.env.LEDGER_URL || 'http://localhost:8085';

// trader1 UUID from auth-service seed
const TRADER1_USER_ID = 'a1000000-0000-0000-0000-000000000001';

let apiRequest: APIRequestContext;

test.describe('Ledger Service', () => {
  test.beforeAll(async ({ playwright }) => {
    apiRequest = await playwright.request.newContext({
      baseURL: LEDGER_URL,
      extraHTTPHeaders: { 'Content-Type': 'application/json' },
    });
  });

  test.afterAll(async () => {
    await apiRequest.dispose();
  });

  test('GET /ledger/accounts/{userId} returns account for trader1', async () => {
    const response = await apiRequest.get(`/ledger/accounts/${TRADER1_USER_ID}`);
    expect(response.status()).toBe(200);

    const body = await response.json();
    expect(body).toHaveProperty('userId');
    expect(body).toHaveProperty('cashBalance');
    expect(typeof body.cashBalance).toBe('number');
  });

  test('account cash balance is non-negative', async () => {
    const response = await apiRequest.get(`/ledger/accounts/${TRADER1_USER_ID}`);
    const body = await response.json();
    expect(body.cashBalance).toBeGreaterThanOrEqual(0);
  });

  test('GET /ledger/history/{userId} returns transaction list for trader1', async () => {
    const response = await apiRequest.get(`/ledger/history/${TRADER1_USER_ID}`);
    expect(response.status()).toBe(200);

    const body = await response.json();
    expect(Array.isArray(body)).toBe(true);
  });

  test('transaction history entries have required fields', async () => {
    const response = await apiRequest.get(`/ledger/history/${TRADER1_USER_ID}`);
    const body = await response.json();

    if (body.length > 0) {
      const tx = body[0];
      expect(tx).toHaveProperty('id');
      expect(tx).toHaveProperty('amount');
      expect(tx).toHaveProperty('type');
    }
  });

  test('GET /ledger/positions/{userId} returns positions list for trader1', async () => {
    const response = await apiRequest.get(`/ledger/positions/${TRADER1_USER_ID}`);
    expect(response.status()).toBe(200);

    const body = await response.json();
    expect(Array.isArray(body)).toBe(true);
  });

  test('position entries have required fields when present', async () => {
    const response = await apiRequest.get(`/ledger/positions/${TRADER1_USER_ID}`);
    const body = await response.json();

    if (body.length > 0) {
      const pos = body[0];
      expect(pos).toHaveProperty('ticker');
      expect(pos).toHaveProperty('quantity');
    }
  });
});
