import { type Page, type Locator, expect } from '@playwright/test';

/**
 * Page Object for the Chat / Decision view — route "/chat/:sessionId".
 *
 * Selectors are derived from the confirmed data-testid attributes in
 * app/frontend/src/app/features/chat/chat.component.html (P6.1).
 *
 * Key data-testid attributes:
 *   chat-messages           — scrollable message list container
 *   chat-message-bubble     — individual message bubble (multiple)
 *   chat-first-decision     — the first decision bubble (contains Markdown)
 *   chat-decision-summary   — mat-expansion-panel with justification / next-steps
 *   chat-typing-indicator   — dot animation shown while streaming
 *   chat-composer-input     — textarea for follow-up messages
 *   chat-send-button        — send button
 *   chat-session-expired    — session-expired banner
 *   chat-sse-error          — SSE error banner
 *   chat-new-case-button    — "Rozpocznij nową sprawę" button
 */
export class ChatPage {
  readonly page: Page;

  // ── Locators ─────────────────────────────────────────────────────────────────

  /** Scrollable message list container. */
  readonly messages: Locator;

  /** All message bubbles (first is the decision bubble). */
  readonly messageBubbles: Locator;

  /**
   * The first decision bubble rendered as Markdown.
   * Contains greeting, decision badge, justification, next-steps, and disclaimer.
   */
  readonly firstDecision: Locator;

  /** The decision summary expansion panel (shows outcome + justification). */
  readonly decisionSummary: Locator;

  /**
   * Typing indicator — visible (.typing-indicator--visible) while streaming.
   * Always present in the DOM; visibility driven by the CSS class.
   */
  readonly typingIndicator: Locator;

  /** Composer textarea. */
  readonly composerInput: Locator;

  /** Send button. */
  readonly sendButton: Locator;

  /** Session-expired banner. */
  readonly sessionExpiredBanner: Locator;

  /** SSE error banner. */
  readonly sseErrorBanner: Locator;

  /** "Rozpocznij nową sprawę" button inside the expired banner. */
  readonly newCaseButton: Locator;

  // ── Constructor ──────────────────────────────────────────────────────────────

  constructor(page: Page) {
    this.page = page;

    this.messages            = page.getByTestId('chat-messages');
    this.messageBubbles      = page.getByTestId('chat-message-bubble');
    this.firstDecision       = page.getByTestId('chat-first-decision').first();
    this.decisionSummary     = page.getByTestId('chat-decision-summary');
    this.typingIndicator     = page.getByTestId('chat-typing-indicator');
    this.composerInput       = page.getByTestId('chat-composer-input');
    this.sendButton          = page.getByTestId('chat-send-button');
    this.sessionExpiredBanner = page.getByTestId('chat-session-expired');
    this.sseErrorBanner      = page.getByTestId('chat-sse-error');
    this.newCaseButton       = page.getByTestId('chat-new-case-button');
  }

  // ── Navigation ───────────────────────────────────────────────────────────────

  /** Navigate directly to a chat session by ID (deep-link). */
  async goto(sessionId: string): Promise<void> {
    await this.page.goto(`/chat/${sessionId}`);
  }

  // ── Wait helpers ─────────────────────────────────────────────────────────────

  /** Wait until the URL matches /chat/ — called after form submit. */
  async waitForNavigation(): Promise<void> {
    await this.page.waitForURL(/\/chat\//, { timeout: 30_000 });
  }

  /**
   * Wait for the first decision bubble to appear and contain content.
   * The stub streams a JSON decision which the backend formats as Markdown.
   * Timeout is generous to allow backend processing + SSE streaming.
   */
  async waitForDecision(): Promise<void> {
    // First decision bubble must appear
    await this.firstDecision.waitFor({ state: 'visible', timeout: 60_000 });
    // The bubble must contain non-trivial text (not just whitespace)
    await expect(this.firstDecision).not.toBeEmpty({ timeout: 60_000 });
  }

  /**
   * Assert that the decision bubble contains the mandatory sections:
   * disclaimer text that the decision is advisory.
   */
  async assertDecisionHasDisclaimer(): Promise<void> {
    // The disclaimer paragraph is hardcoded in the template
    await expect(this.firstDecision).toContainText(
      'Powyższa decyzja jest wstępna',
      { timeout: 30_000 },
    );
  }

  /**
   * Wait for the typing indicator to appear (streaming started).
   * The indicator uses class .typing-indicator--visible when active.
   */
  async waitForTypingIndicator(): Promise<void> {
    await expect(this.typingIndicator).toHaveClass(/typing-indicator--visible/, {
      timeout: 15_000,
    });
  }

  /**
   * Wait for streaming to finish: typing indicator gone AND at least one
   * new assistant bubble has appeared after the user message.
   *
   * @param minBubbleCount  Minimum number of total message bubbles expected
   */
  async waitForStreamingComplete(minBubbleCount: number): Promise<void> {
    // Typing indicator class removed when streaming ends
    await expect(this.typingIndicator).not.toHaveClass(/typing-indicator--visible/, {
      timeout: 60_000,
    });
    // At least the expected number of bubbles visible
    await expect(this.messageBubbles).toHaveCount(minBubbleCount, {
      timeout: 60_000,
    });
  }

  // ── Actions ──────────────────────────────────────────────────────────────────

  /** Type and send a chat message. */
  async sendMessage(text: string): Promise<void> {
    await this.composerInput.fill(text);
    await this.sendButton.click();
  }
}
