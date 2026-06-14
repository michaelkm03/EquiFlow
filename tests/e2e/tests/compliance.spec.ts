/**
 * Compliance Service — Integration Tests (@integration)
 *
 * Validates the compliance service's rule engine: fund checks, violation
 * detection, audit trail creation, and input validation. The compliance
 * service is a synchronous gate in the order flow — every order must pass
 * a compliance check before the order service routes it to settlement.
 *
 * These tests call the compliance service directly (not via the gateway)
 * to isolate contract behavior from routing and auth middleware.
 *
 * Runs on: merge to master
 * Services required: compliance-service (localhost:8084)
 */
import { test, expect, APIRequestContext } from '@playwright/test';

const COMPLIANCE_URL = process.env.COMPLIANCE_URL || 'http://localhost:8084';

test.describe('Compliance Service', { tag: '@integration' }, () => {
  let apiRequest: APIRequestContext;

  test.beforeAll(async ({ playwright }) => {
    apiRequest = await playwright.request.newContext({
      baseURL: COMPLIANCE_URL,
      extraHTTPHeaders: { 'Content-Type': 'application/json' },
    });
  });

  test.afterAll(async () => {
    await apiRequest.dispose();
  });

  test('order with sufficient funds is approved', async () => {
    // Happy path: trader with $100K cash buying ~$1,895 of AAPL should be
    // approved with zero violations. Validates the core approval flow that
    // the majority of real orders will take.
    const response = await apiRequest.post('/compliance/check', {
      data: {
        orderId: '00000000-0000-0000-0000-000000000001',
        userId: 'a1000000-0000-0000-0000-000000000001',
        ticker: 'AAPL',
        side: 'BUY',
        quantity: 10,
        estimatedValue: 1895.00,
        availableCash: 100000.00,
      },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body).toHaveProperty('approved', true);
    expect(body.violations).toHaveLength(0);
  });

  test('order with insufficient funds is rejected with INSUFFICIENT_FUNDS violation', async () => {
    // Critical rejection path: order estimated at $178,200 against $5,000
    // available cash must be rejected. Validates the INSUFFICIENT_FUNDS rule
    // fires correctly and the violation code is included in the response for
    // downstream systems (order service, audit) to act on.
    const response = await apiRequest.post('/compliance/check', {
      data: {
        orderId: '00000000-0000-0000-0000-000000000002',
        userId: 'a1000000-0000-0000-0000-000000000001',
        ticker: 'TSLA',
        side: 'BUY',
        quantity: 1000,
        estimatedValue: 178200.00,
        availableCash: 5000.00,
      },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body).toHaveProperty('approved', false);
    expect(body.violations.length).toBeGreaterThan(0);

    const violationCodes = body.violations.map((v: any) => v.code);
    expect(violationCodes).toContain('INSUFFICIENT_FUNDS');
  });

  test('compliance check response includes checkId for audit trail', async () => {
    // Every compliance check must produce a checkId. This ID is the link
    // between the compliance decision and the audit log — without it,
    // rejected orders cannot be traced back to the rule that fired.
    const response = await apiRequest.post('/compliance/check', {
      data: {
        orderId: '00000000-0000-0000-0000-000000000003',
        userId: 'a1000000-0000-0000-0000-000000000002',
        ticker: 'NVDA',
        side: 'BUY',
        quantity: 1,
        estimatedValue: 875.40,
        availableCash: 50000.00,
      },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body).toHaveProperty('checkId');
    expect(body).toHaveProperty('orderId');
    expect(body).toHaveProperty('checkedAt');
    expect(typeof body.checkId).toBe('string');
  });

  test('compliance history endpoint returns check records for a user', async () => {
    // Validates the history endpoint used for regulatory reporting and
    // trader dispute resolution. Must return an array (empty or populated)
    // for a known user — a 404 or 500 here would break audit trail queries.
    const userId = 'a1000000-0000-0000-0000-000000000001';
    const response = await apiRequest.get(`/compliance/history/${userId}`);
    expect(response.status()).toBe(200);

    const body = await response.json();
    expect(Array.isArray(body)).toBe(true);
  });

  test('missing required fields returns 400', async () => {
    // Input validation boundary: requests missing orderId, userId, and ticker
    // must be rejected before reaching the rule engine. Validates the service
    // does not partially evaluate incomplete compliance requests.
    const response = await apiRequest.post('/compliance/check', {
      data: {
        side: 'BUY',
        quantity: 10,
      },
    });

    expect(response.status()).toBe(400);
  });
});
