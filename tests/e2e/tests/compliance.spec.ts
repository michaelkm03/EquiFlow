import { test, expect, APIRequestContext } from '@playwright/test';

const COMPLIANCE_URL = process.env.COMPLIANCE_URL || 'http://localhost:8084';

let apiRequest: APIRequestContext;

test.describe('Compliance Service', () => {
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

  test('order with insufficient funds is rejected (wash-sale check scenario)', async () => {
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

  test('compliance check returns check ID for audit trail', async () => {
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

  test('compliance history endpoint works for a user', async () => {
    const userId = 'a1000000-0000-0000-0000-000000000001';
    const response = await apiRequest.get(`/compliance/history/${userId}`);
    expect(response.status()).toBe(200);

    const body = await response.json();
    expect(Array.isArray(body)).toBe(true);
  });

  test('missing required fields returns 400', async () => {
    const response = await apiRequest.post('/compliance/check', {
      data: {
        // Missing orderId, userId, ticker
        side: 'BUY',
        quantity: 10,
      },
    });

    expect(response.status()).toBe(400);
  });
});
