import { type Page, type Locator } from '@playwright/test';

/**
 * Page Object for the Chat / Decision view (Widok czatu).
 *
 * This page is reached at route "/chat/:sessionId" after the intake form is
 * submitted. It displays:
 *   - An AI-generated decision bubble (APPROVE / REJECT / ESCALATE) with
 *     justification streamed from the backend.
 *   - A chat input for follow-up questions to the AI.
 *
 * SELECTOR STATUS: All locators are placeholders — real selectors will be
 * confirmed against the live DOM in P6.1 once the Angular template is
 * fully implemented. Prefer data-testid attributes and ARIA roles.
 */
export class ChatPage {
  readonly page: Page;

  // ── Locators ────────────────────────────────────────────────────────────────

  /**
   * The decision outcome badge / heading (e.g. "ZATWIERDZONE", "ODRZUCONO",
   * "ESKALACJA").
   * TODO (P6.1): confirm selector — likely getByTestId('decision-outcome')
   * or getByRole('heading', { name: /zatwierdz|odrzuc|eskalac/i })
   */
  readonly decisionOutcome: Locator;

  /**
   * Container that holds the full decision justification text streamed from
   * the LLM.
   * TODO (P6.1): confirm selector — likely getByTestId('decision-justification')
   */
  readonly decisionJustification: Locator;

  /**
   * The chat message list / transcript area.
   * TODO (P6.1): confirm selector — likely getByRole('log') or
   * getByTestId('chat-messages')
   */
  readonly chatMessages: Locator;

  /**
   * The text input for follow-up questions.
   * TODO (P6.1): confirm selector — likely getByRole('textbox', { name: /pytanie|wiadomość/i })
   * or getByTestId('chat-input')
   */
  readonly chatInput: Locator;

  /**
   * The send / submit button for chat messages.
   * TODO (P6.1): confirm selector — likely getByRole('button', { name: /wyślij|send/i })
   * or getByTestId('chat-send')
   */
  readonly sendButton: Locator;

  /**
   * Streaming indicator shown while the AI response is being received.
   * TODO (P6.1): confirm selector — likely getByTestId('streaming-indicator')
   * or getByRole('status')
   */
  readonly streamingIndicator: Locator;

  // ── Constructor ─────────────────────────────────────────────────────────────

  constructor(page: Page) {
    this.page = page;

    // Placeholder locators — replace with confirmed selectors in P6.1.
    this.decisionOutcome       = page.getByTestId('decision-outcome');
    this.decisionJustification = page.getByTestId('decision-justification');
    this.chatMessages          = page.getByTestId('chat-messages');
    this.chatInput             = page.getByRole('textbox', { name: /pytanie|wiadomość|message/i });
    this.sendButton            = page.getByRole('button', { name: /wyślij|send/i });
    this.streamingIndicator    = page.getByTestId('streaming-indicator');
  }

  // ── Navigation ──────────────────────────────────────────────────────────────

  /**
   * Navigate directly to a chat session by ID.
   * Normally the app navigates here after form submission — use this only in
   * tests that need to deep-link into an existing session.
   */
  async goto(sessionId: string): Promise<void> {
    await this.page.goto(`/chat/${sessionId}`);
  }

  /**
   * Wait until the chat page is fully loaded (URL matches /chat/).
   * TODO (P6.1): also wait for the decision bubble to be visible.
   */
  async waitForLoad(): Promise<void> {
    await this.page.waitForURL(/\/chat\//);
  }

  // ── Actions ─────────────────────────────────────────────────────────────────

  /**
   * Wait for the streamed AI decision to appear (streaming indicator gone,
   * decision outcome visible).
   * TODO (P6.1): tune timeouts based on observed LLM stub latency.
   */
  async waitForDecision(): Promise<void> {
    // Wait for the streaming indicator to disappear first (if present).
    // TODO (P6.1): uncomment once the indicator is implemented.
    // await this.streamingIndicator.waitFor({ state: 'hidden' });
    await this.decisionOutcome.waitFor({ state: 'visible' });
  }

  /**
   * Type a follow-up question and submit it.
   * TODO (P6.1): verify behaviour — does pressing Enter submit, or is the
   * button required?
   */
  async sendMessage(text: string): Promise<void> {
    await this.chatInput.fill(text);
    await this.sendButton.click();
  }

  /**
   * Wait for the assistant's reply to the last user message to appear.
   * TODO (P6.1): refine — count messages before/after to detect a new reply.
   */
  async waitForAssistantReply(): Promise<void> {
    // Placeholder: wait for at least one message in the transcript.
    await this.chatMessages.waitFor({ state: 'visible' });
  }
}
