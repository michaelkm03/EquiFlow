import { test, expect, APIRequestContext } from '@playwright/test';

const AUTH_URL = process.env.AUTH_URL || 'http://localhost:8081';

test.describe('Auth Service', () => {
  let request: APIRequestContext;

  test.beforeAll(async ({ playwright }) => {
    request = await playwright.request.newContext({
      baseURL: AUTH_URL,
      extraHTTPHeaders: { 'Content-Type': 'application/json' },
    });
  });

  test.afterAll(async () => {
    await request.dispose();
  });

  test('valid login returns a JWT token', async () => {
    const response = await request.post('/auth/token', {
      data: {
        username: 'trader1',
        password: 'password123',
      },
    });

    expect(response.status()).toBe(200);

    const body = await response.json();
    expect(body).toHaveProperty('token');
    expect(typeof body.token).toBe('string');
    expect(body.token.length).toBeGreaterThan(50);
    expect(body).toHaveProperty('role', 'TRADER');
    expect(body).toHaveProperty('username', 'trader1');
    expect(body).toHaveProperty('expiresAt');
  });

  test('invalid password returns 401', async () => {
    const response = await request.post('/auth/token', {
      data: {
        username: 'trader1',
        password: 'wrongpassword',
      },
    });

    expect(response.status()).toBe(401);
    const body = await response.json();
    expect(body).toHaveProperty('error');
  });

  test('missing fields returns 400', async () => {
    const response = await request.post('/auth/token', {
      data: {
        username: '',
        password: '',
      },
    });

    expect(response.status()).toBe(400);
  });

  test('unknown user returns 401', async () => {
    const response = await request.post('/auth/token', {
      data: {
        username: 'nonexistent_user',
        password: 'password123',
      },
    });

    expect(response.status()).toBe(401);
  });

  test('validate endpoint accepts valid JWT', async () => {
    // First, get a token
    const loginResp = await request.post('/auth/token', {
      data: { username: 'trader1', password: 'password123' },
    });
    const { token } = await loginResp.json();

    // Then validate it
    const validateResp = await request.get('/auth/validate', {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(validateResp.status()).toBe(200);
    const body = await validateResp.json();
    expect(body.valid).toBe(true);
  });

  test('validate endpoint rejects invalid JWT', async () => {
    const response = await request.get('/auth/validate', {
      headers: { Authorization: 'Bearer invalid.jwt.token' },
    });
    expect(response.status()).toBe(401);
  });
});
