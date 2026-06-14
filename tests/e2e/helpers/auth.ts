import { Playwright } from '@playwright/test';

const AUTH_URL = process.env.AUTH_URL || 'http://localhost:8081';

export async function getAuthToken(
  playwright: Playwright,
  username = 'trader1',
  password = 'password123'
): Promise<string> {
  const ctx = await playwright.request.newContext({ baseURL: AUTH_URL });
  const resp = await ctx.post('/auth/token', { data: { username, password } });
  const body = await resp.json();
  await ctx.dispose();
  return body.token;
}

export function authHeaders(token: string): Record<string, string> {
  return {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
  };
}
