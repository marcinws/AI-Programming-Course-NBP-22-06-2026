import { test, expect } from '@playwright/test';

/**
 * Smoke spec — scaffold sanity only.
 *
 * PURPOSE: Verify that the Playwright project is wired correctly and that
 * `npx playwright test --list` enumerates this spec. No real app interaction
 * happens here; the full E2E flow is implemented in P6.1.
 *
 * The skipped test documents the intended flow so the P6.1 author has a
 * clear starting point.
 */

test.describe('Scaffold sanity @smoke', () => {
  /**
   * Config sanity: Playwright can launch a browser and navigate to about:blank.
   * This test DOES run (not skipped) and validates the Playwright installation.
   */
  test('Playwright jest poprawnie zainstalowany i uruchomiony', async ({ page }) => {
    await page.goto('about:blank');
    expect(page.url()).toBe('about:blank');
  });

  /**
   * Full E2E flow placeholder — skipped until P6.1.
   *
   * Flow to be implemented:
   *   1. Navigate to /  (formularz zgłoszenia)
   *   2. Fill mandatory fields (opis problemu, numer seryjny, opcjonalnie zdjęcie)
   *   3. Submit the form
   *   4. Wait for navigation to /chat/:sessionId
   *   5. Assert decision bubble visible with APPROVE / REJECT / ESCALATE
   *   6. Send one follow-up chat message
   *   7. Assert assistant reply appears
   *
   * Prerequisites for P6.1:
   *   - Angular template implemented with data-testid attributes
   *   - Backend Spring Boot running and proxied via Angular dev server
   *   - LLM stub running at http://127.0.0.1:8089 with OPENROUTER_BASE_URL pointing to it
   *   - webServer array in playwright.config.ts uncommented and wired
   */
  test.skip('Pelny przeplyw: formularz → decyzja → czat (TODO P6.1)', async ({ page }) => {
    // TODO (P6.1): Import and use IntakeFormPage + ChatPage POMs.
    // import { IntakeFormPage } from './pages/IntakeFormPage';
    // import { ChatPage } from './pages/ChatPage';

    // const intakePage = new IntakeFormPage(page);
    // await intakePage.goto();
    // await intakePage.fillAndSubmit('Laptop nie włącza się', 'SN-12345');

    // const chatPage = new ChatPage(page);
    // await chatPage.waitForLoad();
    // await chatPage.waitForDecision();

    // const outcome = await chatPage.decisionOutcome.textContent();
    // expect(['ZATWIERDZONE', 'ODRZUCONO', 'ESKALACJA']).toContain(outcome?.trim().toUpperCase());

    // await chatPage.sendMessage('Czy mogę przyspieszyć rozpatrzenie?');
    // await chatPage.waitForAssistantReply();
  });
});
