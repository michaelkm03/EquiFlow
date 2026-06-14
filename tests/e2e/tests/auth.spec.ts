/**
 * Auth Service — Smoke Tests (@smoke)
 *
 * Validates that the authentication service correctly issues and validates
 * JWT tokens. These are the most foundational tests in the suite — every
 * other service depends on auth working correctly. If any test here fails,
 * all downstream tests (integration, E2E) are meaningless.
 *
 * Runs on: every PR (required gate), every push to master
 * Services required: auth-service (localhost:8081)
 */
import { test, expect, APIRequestContext } from '@playwright/test';

const AUTH_URL = process.env.AUTH_URL || 'http://localhost:8081';

test.describe('Auth Service', { tag: '@smoke' }, () => {
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
    // Core happy path: a known trader credential must produce a valid JWT.
    // Validates token shape (non-empty string, >50 chars), role assignment,
    // and expiry field — all required by downstream services for authorization.
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
    // Security boundary: wrong password must never produce a token.
    // A 200 here would mean the auth service has a critical security defect.
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
    // Input validation: empty credentials must be rejected at the API boundary
    // before reaching any authentication logic.
    const response = await request.post('/auth/token', {
      data: {
        username: '',
        password: '',
      },
    });

    expect(response.status()).toBe(400);
  });

  test('unknown user returns 401', async () => {
    // Ensures the service does not leak user existence via a different status
    // code. Unknown user and wrong password should both return 401, not 404.
    const response = await request.post('/auth/token', {
      data: {
        username: 'nonexistent_user',
        password: 'password123',
      },
    });

    expect(response.status()).toBe(401);
  });

  test('validate endpoint accepts valid JWT', async () => {
    // Two-step flow: login then validate. Confirms the token issued in step 1
    // is immediately usable by other services that call /auth/validate
    // to verify incoming requests.
    const loginResp = await request.post('/auth/token', {
      data: { username: 'trader1', password: 'password123' },
    });
    const { token } = await loginResp.json();

    const validateResp = await request.get('/auth/validate', {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(validateResp.status()).toBe(200);
    const body = await validateResp.json();
    expect(body.valid).toBe(true);
  });

  test('validate endpoint rejects invalid JWT', async () => {
    // Ensures the validation endpoint does not blindly return true.
    // Any service using /auth/validate as a trust signal depends on this
    // correctly returning 401 for a tampered or fabricated token.
    const response = await request.get('/auth/validate', {
      headers: { Authorization: 'Bearer invalid.jwt.token' },
    });
    expect(response.status()).toBe(401);
  });
});
