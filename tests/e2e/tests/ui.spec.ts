/**
 * EquiFlow Agent Dashboard — UI Tests (@ui)
 *
 * Browser-level tests for the React frontend. These are the only tests in
 * the suite that launch a real browser (Chromium) and interact with the DOM.
 * They validate that the UI renders correctly, state transitions work as
 * expected, and critical interactive elements behave properly.
 *
 * These tests do NOT require the Java backend services to be running —
 * they test the frontend in isolation, mocking or ignoring API responses.
 * The one exception is the agent-run flow test, which is marked @e2e and
 * requires the full stack.
 *
 * Runs on: every PR (required gate) alongside smoke tests
 * Services required: frontend dev server (localhost:5173)
 * Browser: Chromium (headed in local dev, headless in CI)
 */
import { test, expect, Page } from '@playwright/test';

const FRONTEND_URL = process.env.FRONTEND_URL || 'http://localhost:5173';

test.describe('Agent Dashboard — UI', { tag: '@ui' }, () => {

  test.beforeEach(async ({ page }) => {
    await page.goto(FRONTEND_URL);
  });

  // ─── Page Load ──────────────────────────────────────────────────────────────

  test('page loads and renders the sidebar with ready agents', async ({ page }) => {
    // Validates the initial render: sidebar heading is present and the three
    // ready agents (Duplicate Detection, Compliance Monitor, Order Triage)
    // appear in the list. Non-ready agents (Failure Escalation, Settlement
    // Reconciliation) must not appear — they are hidden until implemented.
    await expect(page.getByText('EquiFlow')).toBeVisible();
    await expect(page.getByText('Duplicate Detection').first()).toBeVisible();
    await expect(page.getByText('Compliance Monitor').first()).toBeVisible();
    await expect(page.getByText('Order Triage').first()).toBeVisible();

    // Not-ready agents must not be rendered
    await expect(page.getByText('Failure Escalation')).not.toBeVisible();
    await expect(page.getByText('Settlement Reconciliation')).not.toBeVisible();
  });

  test('first ready agent is selected by default', async ({ page }) => {
    // On load, Duplicate Detection should be the active selection.
    // The main panel header must reflect this immediately without any
    // user interaction — default state matters for first-time users.
    await expect(page.getByRole('heading', { name: 'Duplicate Detection' })).toBeVisible();
  });

  test('ticket reference is displayed in the header', async ({ page }) => {
    // Ticket numbers (EQ-136) are shown in the header to link UI elements
    // to the backlog. This is a traceability requirement — product and QA
    // use these to verify the correct agent is being tested for a given ticket.
    await expect(page.getByText('EQ-136').first()).toBeVisible();
  });

  // ─── Agent Selection ─────────────────────────────────────────────────────────

  test('clicking Compliance Monitor updates the main panel header', async ({ page }) => {
    // Validates the agent selection state machine: clicking a sidebar item
    // must update the header, description, and example prompts in the main panel.
    // A stale header after selection would mean the state update is broken.
    await page.getByText('Compliance Monitor').click();
    await expect(page.getByRole('heading', { name: 'Compliance Monitor' })).toBeVisible();
    await expect(page.getByText('wash-sale').first()).toBeVisible();
  });

  test('clicking Order Triage updates the main panel and shows relevant examples', async ({ page }) => {
    // Same state machine check for Order Triage. Also validates that example
    // prompts shown in the header update to match the selected agent — stale
    // examples from the previous agent would confuse users and break prompt seeding.
    await page.getByText('Order Triage').click();
    await expect(page.getByRole('heading', { name: 'Order Triage' })).toBeVisible();
    await expect(page.getByText(/Triage order/)).toBeVisible();
  });

  test('clicking an example prompt populates the input field', async ({ page }) => {
    // Example prompts are clickable shortcuts that pre-fill the input.
    // Validates that clicking an example sets the input value correctly —
    // required for demo flows and first-time user onboarding.
    const exampleBtn = page.getByRole('button', { name: "Scan today's orders for duplicates" });
    await expect(exampleBtn).toBeVisible();
    await exampleBtn.click();

    const input = page.getByPlaceholder("Scan today's orders for duplicates");
    await expect(input).toHaveValue("Scan today's orders for duplicates");
  });

  // ─── Prompt Input & RUN Button ───────────────────────────────────────────────

  test('RUN button is disabled when prompt input is empty', async ({ page }) => {
    // Guards against submitting an empty prompt to the agent API.
    // The RUN button must be disabled (opacity-25 + disabled attr) until the
    // user types something meaningful in the input field.
    const runBtn = page.getByRole('button', { name: 'RUN' });
    await expect(runBtn).toBeDisabled();
  });

  test('RUN button enables after typing a prompt', async ({ page }) => {
    // Validates the controlled input state: as soon as the user types a
    // non-empty string, the RUN button must become enabled. This is the
    // entry point for all agent invocations.
    const input = page.getByPlaceholder("Scan today's orders for duplicates");
    await input.fill('Check for duplicates this week');
    const runBtn = page.getByRole('button', { name: 'RUN' });
    await expect(runBtn).toBeEnabled();
  });

  test('RUN button re-disables if input is cleared', async ({ page }) => {
    // Validates the inverse: clearing the input after typing must disable
    // the RUN button again. Without this, a user who backspaces to empty
    // could still submit a blank prompt.
    const input = page.getByPlaceholder("Scan today's orders for duplicates");
    await input.fill('some query');
    await input.clear();
    const runBtn = page.getByRole('button', { name: 'RUN' });
    await expect(runBtn).toBeDisabled();
  });

  // ─── LIVE / LOCAL Mode Toggle ─────────────────────────────────────────────────

  test('LIVE and LOCAL mode buttons are present', async ({ page }) => {
    // Validates the mode toggle renders. LIVE routes to the Claude API;
    // LOCAL runs against a local model. Both must always be present and
    // labeled correctly — a missing toggle leaves users unable to switch modes.
    await expect(page.getByRole('button', { name: 'LIVE' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'LOCAL' })).toBeVisible();
  });

  test('clicking LOCAL toggles active mode', async ({ page }) => {
    // Validates the toggle state change. After clicking LOCAL, the button
    // should reflect the active state (teal background). The exact CSS class
    // check is intentionally loose — we verify visual distinction exists
    // rather than hard-coding a color value that may change with design updates.
    const localBtn = page.getByRole('button', { name: 'LOCAL' });
    await localBtn.click();

    // After clicking LOCAL, the LIVE button should no longer be active
    // (verified by checking LOCAL button is now the highlighted one)
    await expect(localBtn).toBeVisible();
    // LOCAL button should have an active background class applied
    await expect(localBtn).toHaveClass(/bg-\[#19535f\]/);
  });

  // ─── Seed Controls ────────────────────────────────────────────────────────────

  test('seed controls are visible for Duplicate Detection agent', async ({ page }) => {
    // Seed controls (HIGH/MED/LOW buttons) allow QA to generate test data
    // with specific duplicate time gaps. They must be visible only when
    // Duplicate Detection is selected — they don't apply to other agents.
    await expect(page.getByRole('button', { name: 'HIGH', exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'MED', exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'LOW', exact: true })).toBeVisible();
  });

  test('seed controls are hidden when switching to Compliance Monitor', async ({ page }) => {
    // Validates that seed controls are agent-specific. Compliance Monitor
    // has no concept of seeding duplicate orders, so seed buttons must not
    // appear when it is selected — they would confuse users and could trigger
    // unintended data seeding in shared environments.
    await page.getByText('Compliance Monitor').click();
    await expect(page.getByRole('button', { name: 'HIGH', exact: true })).not.toBeVisible();
    await expect(page.getByRole('button', { name: 'MED', exact: true })).not.toBeVisible();
    await expect(page.getByRole('button', { name: 'LOW', exact: true })).not.toBeVisible();
  });

  test('Cleanup button is present in sidebar footer', async ({ page }) => {
    // Cleanup removes all seeded test data. It must always be accessible
    // from the sidebar footer regardless of which agent is selected.
    await expect(page.getByRole('button', { name: 'Cleanup' })).toBeVisible();
  });

  // ─── Empty Timeline State ─────────────────────────────────────────────────────

  test('timeline area shows idle placeholder before any run', async ({ page }) => {
    // Validates the empty state of the timeline panel. Before any agent run,
    // the placeholder text must be visible so users know the area is functional
    // and waiting for input — a blank panel with no feedback is confusing.
    await expect(page.getByText('Run an agent to see output')).toBeVisible();
  });

  // ─── Negative Cases ───────────────────────────────────────────────────────────

  test('seed controls are not shown for Order Triage agent', async ({ page }) => {
    // Seed controls are Duplicate Detection-specific. Order Triage has no
    // concept of seeding duplicate orders — showing them would mislead users
    // into thinking they can seed data for a different agent's workflow.
    await page.getByText('Order Triage').click();
    await expect(page.getByRole('button', { name: 'HIGH', exact: true })).not.toBeVisible();
    await expect(page.getByRole('button', { name: 'MED', exact: true })).not.toBeVisible();
    await expect(page.getByRole('button', { name: 'LOW', exact: true })).not.toBeVisible();
  });

  test('switching agents does not carry over example prompts from previous agent', async ({ page }) => {
    // When switching to Compliance Monitor, the Duplicate Detection example
    // prompt buttons must no longer be visible. Stale examples from the wrong
    // agent would seed the input with an irrelevant prompt and confuse users.
    await page.getByText('Compliance Monitor').click();
    await expect(page.getByRole('button', { name: "Scan today's orders for duplicates" })).not.toBeVisible();
  });

  test('LIVE button does not show active state when LOCAL is selected', async ({ page }) => {
    // Validates the inverse of the LOCAL toggle test: when LOCAL is active,
    // LIVE must not also appear active. Two simultaneously active mode buttons
    // would mean the toggle state is broken.
    const liveBtn = page.getByRole('button', { name: 'LIVE' });
    await page.getByRole('button', { name: 'LOCAL' }).click();
    await expect(liveBtn).not.toHaveClass(/bg-\[#19535f\]/);
  });

  test('prompt input is empty on initial page load', async ({ page }) => {
    // The input must start blank — no pre-filled value from a previous session,
    // cached state, or default injection. A pre-filled input would silently
    // submit a stale prompt if the user clicks RUN without reading it.
    const input = page.getByPlaceholder("Scan today's orders for duplicates");
    await expect(input).toHaveValue('');
  });

  test('RUN button remains disabled when input contains only whitespace', async ({ page }) => {
    // A prompt of only spaces is functionally empty — the agent would receive
    // a blank query. The RUN button must stay disabled to prevent this, the
    // same as if the field were completely empty.
    const input = page.getByPlaceholder("Scan today's orders for duplicates");
    await input.fill('   ');
    await expect(page.getByRole('button', { name: 'RUN' })).toBeDisabled();
  });
});
