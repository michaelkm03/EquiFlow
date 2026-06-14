import { test, expect, APIRequestContext } from '@playwright/test';

const MARKET_URL = process.env.MARKET_URL || 'http://localhost:8083';

let apiRequest: APIRequestContext;

test.describe('Market Data Service', { tag: '@smoke' }, () => {
  test.beforeAll(async ({ playwright }) => {
    apiRequest = await playwright.request.newContext({
      baseURL: MARKET_URL,
      extraHTTPHeaders: { 'Content-Type': 'application/json' },
    });
  });

  test.afterAll(async () => {
    await apiRequest.dispose();
  });

  test('GET /market/prices returns all ticker prices', async () => {
    const response = await apiRequest.get('/market/prices');
    expect(response.status()).toBe(200);

    const body = await response.json();
    expect(Array.isArray(body)).toBe(true);
    expect(body.length).toBeGreaterThan(0);

    const first = body[0];
    expect(first).toHaveProperty('ticker');
    expect(first).toHaveProperty('price');
  });

  test('GET /market/prices/{ticker} returns price for AAPL', async () => {
    const response = await apiRequest.get('/market/prices/AAPL');
    expect(response.status()).toBe(200);

    const body = await response.json();
    expect(body).toHaveProperty('ticker', 'AAPL');
    expect(body).toHaveProperty('price');
    expect(typeof body.price).toBe('number');
    expect(body.price).toBeGreaterThan(0);
  });

  test('GET /market/prices/{ticker} returns price for MSFT', async () => {
    const response = await apiRequest.get('/market/prices/MSFT');
    expect(response.status()).toBe(200);

    const body = await response.json();
    expect(body).toHaveProperty('ticker', 'MSFT');
    expect(body.price).toBeGreaterThan(0);
  });

  test('GET /market/status returns scenario engine status', async () => {
    const response = await apiRequest.get('/market/status');
    expect(response.status()).toBe(200);

    const body = await response.json();
    expect(body).toBeDefined();
  });

  test('POST /market/prices/{ticker}/tick simulates a price tick', async () => {
    const before = await apiRequest.get('/market/prices/AAPL');
    const beforeBody = await before.json();

    const tickResp = await apiRequest.post('/market/prices/AAPL/tick');
    expect(tickResp.status()).toBe(200);

    const tickBody = await tickResp.json();
    expect(tickBody).toHaveProperty('ticker', 'AAPL');
    expect(tickBody).toHaveProperty('price');
    expect(typeof tickBody.price).toBe('number');
  });

  test('GET /market/prices/{ticker} returns 404 or 500 for unknown ticker', async () => {
    const response = await apiRequest.get('/market/prices/FAKEXYZ');
    expect([404, 500]).toContain(response.status());
  });
});
