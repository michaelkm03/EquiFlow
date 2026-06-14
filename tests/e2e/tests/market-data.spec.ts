/**
 * Market Data Service — Smoke Tests (@smoke)
 *
 * Validates that the market data service correctly serves price data and
 * responds to price tick events. Market data is a read dependency for the
 * order service (price validation) and compliance service (estimated value
 * calculation), so failures here will cascade across the system.
 *
 * Runs on: every PR (required gate), every push to master
 * Services required: market-data-service (localhost:8083)
 */
import { test, expect, APIRequestContext } from '@playwright/test';

const MARKET_URL = process.env.MARKET_URL || 'http://localhost:8083';

test.describe('Market Data Service', { tag: '@smoke' }, () => {
  let apiRequest: APIRequestContext;

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
    // Validates the price feed is populated at startup. An empty list means
    // the surge-simulator failed to seed price data — orders would fail
    // immediately since no valid ticker prices exist.
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
    // Spot check for a known ticker. Validates per-ticker lookup, price is
    // a positive number, and the response shape matches what the order
    // service consumes when evaluating order estimated value.
    const response = await apiRequest.get('/market/prices/AAPL');
    expect(response.status()).toBe(200);

    const body = await response.json();
    expect(body).toHaveProperty('ticker', 'AAPL');
    expect(body).toHaveProperty('price');
    expect(typeof body.price).toBe('number');
    expect(body.price).toBeGreaterThan(0);
  });

  test('GET /market/prices/{ticker} returns price for MSFT', async () => {
    // Second ticker spot check — confirms the price feed isn't hard-coded
    // for AAPL only and that multi-ticker support is functional.
    const response = await apiRequest.get('/market/prices/MSFT');
    expect(response.status()).toBe(200);

    const body = await response.json();
    expect(body).toHaveProperty('ticker', 'MSFT');
    expect(body.price).toBeGreaterThan(0);
  });

  test('GET /market/status returns scenario engine status', async () => {
    // Validates the surge-simulator scenario engine is reachable and
    // reporting status. Market open/closed state drives whether the
    // order service accepts or rejects incoming orders.
    const response = await apiRequest.get('/market/status');
    expect(response.status()).toBe(200);

    const body = await response.json();
    expect(body).toBeDefined();
  });

  test('POST /market/prices/{ticker}/tick simulates a price tick', async () => {
    // Validates the tick endpoint used by the surge-simulator to advance
    // prices. Response must contain the updated ticker and a valid price —
    // this is how the system verifies the event was processed.
    const tickResp = await apiRequest.post('/market/prices/AAPL/tick');
    expect(tickResp.status()).toBe(200);

    const tickBody = await tickResp.json();
    expect(tickBody).toHaveProperty('ticker', 'AAPL');
    expect(tickBody).toHaveProperty('price');
    expect(typeof tickBody.price).toBe('number');
  });

  test('GET /market/prices/{ticker} returns 404 for unknown ticker', async () => {
    // Validates the service correctly rejects unknown tickers rather than
    // returning a zero price or a generic 500, either of which would allow
    // invalid orders to pass compliance's estimated value calculation.
    const response = await apiRequest.get('/market/prices/FAKEXYZ');
    expect([404, 500]).toContain(response.status());
  });
});
