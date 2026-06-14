/**
 * Ledger Service — Integration Tests (@integration)
 *
 * Validates that the ledger service correctly tracks cash balances,
 * transaction history, and portfolio positions. The ledger is the source
 * of truth for trader account state — compliance uses it for fund checks,
 * and the settlement service writes to it when orders fill.
 *
 * Tests run directly against the ledger service (not via gateway) to
 * isolate ledger contract behavior from auth middleware.
 *
 * Runs on: merge to master
 * Services required: ledger-service (localhost:8085)
 */
import { test, expect, APIRequestContext } from '@playwright/test';

const LEDGER_URL = process.env.LEDGER_URL || 'http://localhost:8085';

// Seeded trader1 user ID from auth-service startup data
const TRADER1_USER_ID = 'a1000000-0000-0000-0000-000000000001';

test.describe('Ledger Service', { tag: '@integration' }, () => {
  let apiRequest: APIRequestContext;

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
    // Validates the account lookup that compliance and order services call
    // to determine available cash before routing an order. A 404 or missing
    // cashBalance field here means compliance fund checks would fail silently.
    const response = await apiRequest.get(`/ledger/accounts/${TRADER1_USER_ID}`);
    expect(response.status()).toBe(200);

    const body = await response.json();
    expect(body).toHaveProperty('userId');
    expect(body).toHaveProperty('cashBalance');
    expect(typeof body.cashBalance).toBe('number');
  });

  test('account cash balance is non-negative', async () => {
    // Financial invariant: a cash balance below zero indicates an accounting
    // error in settlement or a missing transaction debit. This must never
    // occur in a correctly functioning system.
    const response = await apiRequest.get(`/ledger/accounts/${TRADER1_USER_ID}`);
    const body = await response.json();
    expect(body.cashBalance).toBeGreaterThanOrEqual(0);
  });

  test('GET /ledger/history/{userId} returns transaction list', async () => {
    // Validates the transaction history endpoint used for account statements
    // and dispute resolution. Must return an array — a 500 or non-array
    // response would break the settlement reconciliation process.
    const response = await apiRequest.get(`/ledger/history/${TRADER1_USER_ID}`);
    expect(response.status()).toBe(200);

    const body = await response.json();
    expect(Array.isArray(body)).toBe(true);
  });

  test('transaction history entries contain required fields', async () => {
    // Validates the shape of transaction records. Each entry must have an id,
    // amount, and type so downstream consumers (reporting, audit correlation)
    // can process history entries without null-checking every field.
    const response = await apiRequest.get(`/ledger/history/${TRADER1_USER_ID}`);
    const body = await response.json();

    if (body.length > 0) {
      const tx = body[0];
      expect(tx).toHaveProperty('id');
      expect(tx).toHaveProperty('amount');
      expect(tx).toHaveProperty('type');
    }
  });

  test('GET /ledger/positions/{userId} returns positions list', async () => {
    // Validates the positions endpoint used to display portfolio holdings.
    // The E2E saga test depends on this to confirm a BUY order resulted in
    // a new position being created after settlement.
    const response = await apiRequest.get(`/ledger/positions/${TRADER1_USER_ID}`);
    expect(response.status()).toBe(200);

    const body = await response.json();
    expect(Array.isArray(body)).toBe(true);
  });

  test('position entries contain required fields when present', async () => {
    // Validates position record shape. Both ticker and quantity are required
    // for the frontend portfolio view and for compliance's position-limit
    // checks on future orders.
    const response = await apiRequest.get(`/ledger/positions/${TRADER1_USER_ID}`);
    const body = await response.json();

    if (body.length > 0) {
      const pos = body[0];
      expect(pos).toHaveProperty('ticker');
      expect(pos).toHaveProperty('quantity');
    }
  });
});
