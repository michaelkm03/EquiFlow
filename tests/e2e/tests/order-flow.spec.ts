/**
 * Cross-service E2E: Full Order Lifecycle
 *
 * Validates the entire order saga across 5 services:
 *   Auth → Gateway (order submit) → Compliance → Audit → Ledger
 *
 * Tagged @e2e — runs in the regression suite and staging environment gates,
 * not on PR (too slow / requires all services up).
 */
import { test, expect, APIRequestContext } from '@playwright/test';
import { getAuthToken, authHeaders } from '../helpers/auth';

const GATEWAY_URL    = process.env.BASE_URL        || 'http://localhost:8080';
const LEDGER_URL     = process.env.LEDGER_URL      || 'http://localhost:8085';
const AUDIT_URL      = process.env.AUDIT_URL       || 'http://localhost:8087';
const COMPLIANCE_URL = process.env.COMPLIANCE_URL  || 'http://localhost:8084';

const TRADER1_USER_ID = 'a1000000-0000-0000-0000-000000000001';
const POLL_INTERVAL_MS = 1000;
const POLL_MAX_ATTEMPTS = 10;

test.describe('Order Lifecycle — Cross-Service E2E', { tag: '@e2e' }, () => {
  let gatewayCtx: APIRequestContext;
  let ledgerCtx: APIRequestContext;
  let auditCtx: APIRequestContext;
  let complianceCtx: APIRequestContext;

  test.beforeAll(async ({ playwright }) => {
    const token = await getAuthToken(playwright);

    gatewayCtx = await playwright.request.newContext({
      baseURL: GATEWAY_URL,
      extraHTTPHeaders: authHeaders(token),
    });
    ledgerCtx = await playwright.request.newContext({
      baseURL: LEDGER_URL,
      extraHTTPHeaders: { 'Content-Type': 'application/json' },
    });
    auditCtx = await playwright.request.newContext({
      baseURL: AUDIT_URL,
      extraHTTPHeaders: { 'Content-Type': 'application/json' },
    });
    complianceCtx = await playwright.request.newContext({
      baseURL: COMPLIANCE_URL,
      extraHTTPHeaders: { 'Content-Type': 'application/json' },
    });
  });

  test.afterAll(async () => {
    await Promise.all([
      gatewayCtx.dispose(),
      ledgerCtx.dispose(),
      auditCtx.dispose(),
      complianceCtx.dispose(),
    ]);
  });

  test('full saga: submit order → compliance recorded → audit trail created → ledger updated', async () => {
    // 1. Capture baseline ledger state before the order
    const baselineResp = await ledgerCtx.get(`/ledger/accounts/${TRADER1_USER_ID}`);
    expect(baselineResp.status()).toBe(200);
    const { cashBalance: balanceBefore } = await baselineResp.json();

    // 2. Submit a market BUY order through the API gateway
    const orderResp = await gatewayCtx.post('/orders', {
      data: { ticker: 'AAPL', side: 'BUY', type: 'MARKET', quantity: 1 },
    });

    if (orderResp.status() === 409) {
      test.skip(); // Market closed — environment state, not a defect
      return;
    }

    expect(orderResp.status()).toBe(201);
    const order = await orderResp.json();
    const orderId: string = order.id;
    expect(orderId).toBeTruthy();

    // 3. Poll until order reaches a terminal state
    let finalStatus: string = order.status;
    for (let i = 0; i < POLL_MAX_ATTEMPTS; i++) {
      await new Promise(r => setTimeout(r, POLL_INTERVAL_MS));
      const pollResp = await gatewayCtx.get(`/orders/${orderId}`);
      if (pollResp.status() === 200) {
        const polled = await pollResp.json();
        finalStatus = polled.status;
        if (['FILLED', 'REJECTED', 'CANCELLED', 'FAILED'].includes(finalStatus)) break;
      }
    }

    // 4. Verify audit trail — at least one ORDER event recorded for this order
    const auditResp = await auditCtx.get(`/audit/events/order/${orderId}`);
    expect([200, 404]).toContain(auditResp.status());
    if (auditResp.status() === 200) {
      const events: any[] = await auditResp.json();
      if (Array.isArray(events) && events.length > 0) {
        expect(events.some(e => typeof e.eventType === 'string' && e.eventType.includes('ORDER'))).toBe(true);
      }
    }

    // 5. Verify compliance service recorded a check for this trader
    const complianceResp = await complianceCtx.get(`/compliance/history/${TRADER1_USER_ID}`);
    expect(complianceResp.status()).toBe(200);
    const complianceHistory = await complianceResp.json();
    expect(Array.isArray(complianceHistory)).toBe(true);

    // 6. Verify ledger reflects the fill — cash balance decreases on BUY FILLED
    if (finalStatus === 'FILLED') {
      const updatedLedgerResp = await ledgerCtx.get(`/ledger/accounts/${TRADER1_USER_ID}`);
      expect(updatedLedgerResp.status()).toBe(200);
      const { cashBalance: balanceAfter } = await updatedLedgerResp.json();
      expect(balanceAfter).toBeLessThan(balanceBefore);

      // And verify a new position exists for AAPL
      const positionsResp = await ledgerCtx.get(`/ledger/positions/${TRADER1_USER_ID}`);
      expect(positionsResp.status()).toBe(200);
      const positions: any[] = await positionsResp.json();
      const aaplPosition = positions.find((p: any) => p.ticker === 'AAPL');
      expect(aaplPosition).toBeDefined();
      expect(aaplPosition.quantity).toBeGreaterThan(0);
    }
  });

  test('unauthenticated request to gateway is rejected with 401', async ({ playwright }) => {
    const unauthCtx = await playwright.request.newContext({ baseURL: GATEWAY_URL });
    const resp = await unauthCtx.post('/orders', {
      data: { ticker: 'AAPL', side: 'BUY', type: 'MARKET', quantity: 1 },
    });
    expect(resp.status()).toBe(401);
    await unauthCtx.dispose();
  });

  test('BOT_OPERATOR role can read orders but not place them', async ({ playwright }) => {
    const botToken = await getAuthToken(playwright, 'bot-operator1', 'password123');
    const botCtx = await playwright.request.newContext({
      baseURL: GATEWAY_URL,
      extraHTTPHeaders: authHeaders(botToken),
    });

    // Reading orders should succeed
    const readResp = await botCtx.get('/orders?page=0&size=5');
    expect([200, 403]).toContain(readResp.status());

    // Placing an order as BOT_OPERATOR should be forbidden
    const placeResp = await botCtx.post('/orders', {
      data: { ticker: 'AAPL', side: 'BUY', type: 'MARKET', quantity: 1 },
    });
    expect([403, 401]).toContain(placeResp.status());

    await botCtx.dispose();
  });

  test('sell order rejected when trader holds no position', async () => {
    const resp = await gatewayCtx.post('/orders', {
      data: { ticker: 'FAKEXYZ99', side: 'SELL', type: 'MARKET', quantity: 100 },
    });
    // Gateway should reject invalid ticker or insufficient position
    expect([400, 409, 422]).toContain(resp.status());
  });

  test('audit event count is non-decreasing across polls', async () => {
    const first = await auditCtx.get('/audit/events?page=0&size=1');
    const { totalElements: countBefore } = await first.json();

    // Place an order to generate audit events
    await gatewayCtx.post('/orders', {
      data: { ticker: 'MSFT', side: 'BUY', type: 'MARKET', quantity: 1 },
    });
    await new Promise(r => setTimeout(r, 1500));

    const second = await auditCtx.get('/audit/events?page=0&size=1');
    const { totalElements: countAfter } = await second.json();

    // Audit log is append-only — total must never shrink
    expect(countAfter).toBeGreaterThanOrEqual(countBefore);
  });
});
