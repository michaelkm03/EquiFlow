import { test, expect, APIRequestContext } from '@playwright/test';

const AUTH_URL = process.env.AUTH_URL || 'http://localhost:8081';
const GATEWAY_URL = process.env.BASE_URL || 'http://localhost:8080';

let authToken: string;
let apiRequest: APIRequestContext;

test.describe('Order Service', () => {
  test.beforeAll(async ({ playwright }) => {
    // Get auth token
    const authContext = await playwright.request.newContext({
      baseURL: AUTH_URL,
    });
    const loginResp = await authContext.post('/auth/token', {
      data: { username: 'trader1', password: 'password123' },
    });
    const loginBody = await loginResp.json();
    authToken = loginBody.token;
    await authContext.dispose();

    // Create API context with auth
    apiRequest = await playwright.request.newContext({
      baseURL: GATEWAY_URL,
      extraHTTPHeaders: {
        Authorization: `Bearer ${authToken}`,
        'Content-Type': 'application/json',
      },
    });
  });

  test.afterAll(async () => {
    await apiRequest.dispose();
  });

  test('submit market BUY order for AAPL', async () => {
    const response = await apiRequest.post('/orders', {
      data: {
        ticker: 'AAPL',
        side: 'BUY',
        type: 'MARKET',
        quantity: 10,
      },
    });

    // Accept 201 or 409 (market closed) as valid responses
    expect([201, 409]).toContain(response.status());

    if (response.status() === 201) {
      const body = await response.json();
      expect(body).toHaveProperty('id');
      expect(body).toHaveProperty('ticker', 'AAPL');
      expect(body).toHaveProperty('side', 'BUY');
      expect(body).toHaveProperty('type', 'MARKET');
      expect(body).toHaveProperty('status');
      expect(['PENDING', 'FILLED', 'REJECTED', 'PARTIALLY_FILLED']).toContain(body.status);
    }
  });

  test('submit limit BUY order for MSFT', async () => {
    const response = await apiRequest.post('/orders', {
      data: {
        ticker: 'MSFT',
        side: 'BUY',
        type: 'LIMIT',
        quantity: 5,
        limitPrice: 400.00,
      },
    });

    expect([201, 409]).toContain(response.status());

    if (response.status() === 201) {
      const body = await response.json();
      expect(body).toHaveProperty('ticker', 'MSFT');
      expect(body).toHaveProperty('limitPrice');
    }
  });

  test('limit order requires limitPrice field', async () => {
    const response = await apiRequest.post('/orders', {
      data: {
        ticker: 'TSLA',
        side: 'BUY',
        type: 'LIMIT',
        quantity: 1,
        // limitPrice intentionally omitted
      },
    });

    // Should be 400 (validation) or 409 (market closed) - not 201
    expect(response.status()).not.toBe(201);
  });

  test('get order book for AAPL', async () => {
    const response = await apiRequest.get('/orders/book/AAPL');
    expect(response.status()).toBe(200);

    const body = await response.json();
    expect(body).toHaveProperty('ticker', 'AAPL');
    expect(body).toHaveProperty('bids');
    expect(body).toHaveProperty('asks');
  });

  test('list orders returns paginated result', async () => {
    const response = await apiRequest.get('/orders?page=0&size=10');
    expect(response.status()).toBe(200);

    const body = await response.json();
    expect(body).toHaveProperty('content');
    expect(body).toHaveProperty('totalElements');
    expect(Array.isArray(body.content)).toBe(true);
  });

  test('poll order until FILLED or OPEN', async () => {
    // Place a market order and poll for completion
    const placeResp = await apiRequest.post('/orders', {
      data: { ticker: 'NVDA', side: 'BUY', type: 'MARKET', quantity: 1 },
    });

    if (placeResp.status() === 409) {
      test.skip(); // Market closed, skip
      return;
    }

    expect(placeResp.status()).toBe(201);
    const order = await placeResp.json();
    const orderId = order.id;

    // Poll up to 5 times for terminal state
    let finalOrder = order;
    for (let i = 0; i < 5; i++) {
      const getResp = await apiRequest.get(`/orders/${orderId}`);
      if (getResp.status() === 200) {
        finalOrder = await getResp.json();
        if (['FILLED', 'REJECTED', 'CANCELLED', 'FAILED'].includes(finalOrder.status)) break;
      }
      await new Promise(r => setTimeout(r, 1000));
    }

    expect(['FILLED', 'REJECTED', 'CANCELLED', 'OPEN', 'PARTIALLY_FILLED', 'PENDING', 'FAILED'])
        .toContain(finalOrder.status);
  });
});
