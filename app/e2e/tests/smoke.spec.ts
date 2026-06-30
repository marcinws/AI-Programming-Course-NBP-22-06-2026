import { test, expect } from '@playwright/test';
import path from 'path';
import { IntakeFormPage } from './pages/IntakeFormPage';
import { ChatPage } from './pages/ChatPage';

// Absolute path to the tiny JPEG fixture uploaded in the form
const FIXTURE_JPEG = path.resolve(import.meta.dirname ?? __dirname, 'fixtures', 'sample.jpg');

// ── Scaffold sanity ──────────────────────────────────────────────────────────

test.describe('Scaffold sanity @smoke', () => {
  /**
   * Config sanity: Playwright can launch a browser.
   * Does NOT require the running stack.
   */
  test('Playwright jest poprawnie zainstalowany i uruchomiony', async ({ page }) => {
    await page.goto('about:blank');
    expect(page.url()).toBe('about:blank');
  });
});

// ── Full E2E flow ────────────────────────────────────────────────────────────

/**
 * P6.1 — Full flow: intake form → AI decision (stub) → streamed chat turn.
 *
 * Prerequisites (all managed by playwright.config.ts webServer):
 *   1. LLM stub at http://127.0.0.1:8089   (default APPROVE scenario)
 *   2. Spring Boot at http://localhost:8080  (OPENROUTER_BASE_URL=stub)
 *   3. Angular SPA at http://localhost:4200  (proxies /api → :8080)
 *
 * Refs: ADR-000 §10 TAC-11, ADR-002 §8 "Full flow", PRD §6 AC-18/19/23/25/26
 */
test.describe('Pelny przeplyw E2E @full-flow', () => {

  test('formularz → decyzja → czat (jeden turn strumieniowy)', async ({ page }) => {
    const formPage = new IntakeFormPage(page);
    const chatPage = new ChatPage(page);

    // ── Step 1: Navigate to intake form ─────────────────────────────────────
    await test.step('Otwarcie formularza zgłoszenia', async () => {
      await formPage.goto();
      // Title visible in Polish
      await expect(page.getByText('Nowe zgłoszenie sprzętowe')).toBeVisible();
    });

    // ── Step 2: Fill the form ────────────────────────────────────────────────
    await test.step('Wypełnienie formularza', async () => {
      // Case type: default is COMPLAINT ("Reklamacja") — no change needed,
      // but explicitly select it to confirm the mat-select works.
      await formPage.selectMatOption(formPage.caseTypeSelect, 'Reklamacja');

      // Equipment category
      await formPage.selectMatOption(formPage.equipmentCategorySelect, 'Laptop');

      // Model name
      await formPage.modelNameInput.fill('ThinkPad X1 Carbon');

      // Purchase date — must be a past date
      // The input is readonly (Angular [readonly]) and uses a datepicker.
      // We inject the value via JavaScript because the field is readonly.
      await page.evaluate(() => {
        const el = document.querySelector('[data-testid="form-purchase-date-input"]') as HTMLInputElement;
        if (el) {
          const nativeInputValueSetter = Object.getOwnPropertyDescriptor(
            window.HTMLInputElement.prototype, 'value'
          )?.set;
          nativeInputValueSetter?.call(el, '01.01.2023');
          el.dispatchEvent(new Event('input', { bubbles: true }));
          el.dispatchEvent(new Event('change', { bubbles: true }));
        }
      });
      // Click the datepicker toggle to trigger Angular Material change detection,
      // then press Escape to close it
      await page.getByTestId('form-purchase-date-input').click();
      // Try directly clicking a past day in the calendar, or just close
      await page.keyboard.press('Escape');

      // Reason (required for COMPLAINT)
      await formPage.reasonTextarea.fill(
        'Ekran laptopa wykazuje usterki fabryczne — widoczne plamki na matrycy.',
      );

      // File upload via hidden input
      await formPage.fileInput.setInputFiles(FIXTURE_JPEG);

      // Preview should appear after file selection
      await expect(page.locator('.preview-img')).toBeVisible({ timeout: 5_000 });
    });

    // ── Step 3: Submit and assert navigation ─────────────────────────────────
    await test.step('Wysłanie formularza i nawigacja do czatu', async () => {
      // Submit button should be enabled now
      await expect(formPage.submitButton).toBeEnabled({ timeout: 10_000 });

      // Click submit
      await formPage.submit();

      // App should navigate to /chat/:sessionId
      await chatPage.waitForNavigation();
      expect(page.url()).toMatch(/\/chat\/[a-zA-Z0-9_-]+/);
    });

    // ── Step 4: Assert decision bubble ──────────────────────────────────────
    await test.step('Weryfikacja bąbelka decyzji AI', async () => {
      // Wait for the first decision bubble (streamed from stub)
      await chatPage.waitForDecision();

      // The decision bubble should contain the APPROVE decision content from the stub.
      // The stub returns a structured JSON; the backend formats it as Markdown.
      // We assert key Polish words from the decision content.
      const decisionBubble = chatPage.firstDecision;
      await expect(decisionBubble).toBeVisible();

      // The bubble must contain text (non-empty)
      const bubbleText = await decisionBubble.textContent();
      expect(bubbleText?.trim().length).toBeGreaterThan(10);

      // Disclaimer is hardcoded in the template (AC-26)
      await chatPage.assertDecisionHasDisclaimer();

      // Decision summary panel should be visible (mat-expansion-panel)
      await expect(chatPage.decisionSummary).toBeVisible({ timeout: 10_000 });
    });

    // ── Step 5: Send a follow-up chat message ────────────────────────────────
    await test.step('Wysłanie wiadomości uzupełniającej w czacie', async () => {
      // Count bubbles before sending (at least 1 — the decision bubble)
      const bubblesBefore = await chatPage.messageBubbles.count();

      // Composer should be enabled after decision is rendered
      await expect(chatPage.composerInput).toBeEnabled({ timeout: 10_000 });

      // Send a follow-up message
      await chatPage.sendMessage('Czy mogę przyspieszyć rozpatrzenie reklamacji?');

      // The app appends BOTH the user bubble and an empty streaming assistant bubble
      // immediately on send. Wait for the final state: bubblesBefore + 2 total.
      // (user bubble + finalized assistant bubble)
      await chatPage.waitForStreamingComplete(bubblesBefore + 2);

      // Last bubble should be the finalized assistant response (non-empty)
      const lastBubble = chatPage.messageBubbles.last();
      const lastText = await lastBubble.textContent();
      expect(lastText?.trim().length).toBeGreaterThan(5);

      // History preserved: user message visible at position bubblesBefore
      await expect(chatPage.messageBubbles.nth(bubblesBefore)).toContainText(
        'Czy mogę przyspieszyć',
      );
    });
  });
});
