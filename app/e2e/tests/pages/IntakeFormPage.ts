import { type Page, type Locator } from '@playwright/test';

/**
 * Page Object for the Intake Form (Formularz zgłoszenia).
 *
 * This is the landing page at route "/" where the user submits a hardware
 * service request before the AI decision flow starts.
 *
 * SELECTOR STATUS: All locators are placeholders — real selectors will be
 * confirmed against the live DOM in P6.1 once the Angular template is
 * fully implemented. Prefer data-testid attributes and ARIA roles over
 * Angular-generated `_ngcontent-*` attributes (which change on every build).
 *
 * Routing: On successful submit the app navigates to /chat/:sessionId.
 */
export class IntakeFormPage {
  readonly page: Page;

  // ── Locators ────────────────────────────────────────────────────────────────

  /**
   * TODO (P6.1): Confirm selector once Angular template exposes the field.
   * Expected: a labelled text area / input for the problem description.
   * Prefer: page.getByLabel('Opis problemu') or page.getByTestId('field-description')
   */
  readonly descriptionField: Locator;

  /**
   * TODO (P6.1): Confirm selector for hardware serial / asset identifier.
   * Prefer: page.getByLabel('Numer seryjny') or page.getByTestId('field-serial')
   */
  readonly serialNumberField: Locator;

  /**
   * TODO (P6.1): Confirm selector for the photo / attachment upload input.
   * Prefer: page.getByTestId('field-photo') or page.locator('input[type="file"]')
   */
  readonly photoUploadInput: Locator;

  /**
   * TODO (P6.1): Confirm the submit button role / label.
   * Prefer: page.getByRole('button', { name: /wyślij|zatwierdź/i })
   */
  readonly submitButton: Locator;

  /**
   * TODO (P6.1): Confirm error message container selector.
   * Prefer: page.getByRole('alert') or page.getByTestId('form-error')
   */
  readonly formError: Locator;

  // ── Constructor ─────────────────────────────────────────────────────────────

  constructor(page: Page) {
    this.page = page;

    // Placeholder locators — replace with confirmed selectors in P6.1.
    this.descriptionField = page.getByTestId('field-description');
    this.serialNumberField = page.getByTestId('field-serial');
    this.photoUploadInput  = page.locator('input[type="file"]');
    this.submitButton      = page.getByRole('button', { name: /wyślij|zatwierdź|submit/i });
    this.formError         = page.getByRole('alert');
  }

  // ── Navigation ──────────────────────────────────────────────────────────────

  /** Navigate to the intake form (root route). */
  async goto(): Promise<void> {
    await this.page.goto('/');
  }

  // ── Actions ─────────────────────────────────────────────────────────────────

  /**
   * Fill the problem description field.
   * TODO (P6.1): verify the actual label / testid.
   */
  async fillDescription(text: string): Promise<void> {
    await this.descriptionField.fill(text);
  }

  /**
   * Fill the serial number / asset identifier field.
   * TODO (P6.1): verify the actual label / testid.
   */
  async fillSerialNumber(value: string): Promise<void> {
    await this.serialNumberField.fill(value);
  }

  /**
   * Upload a photo attachment.
   * TODO (P6.1): verify the file input selector and upload mechanism.
   */
  async uploadPhoto(filePath: string): Promise<void> {
    await this.photoUploadInput.setInputFiles(filePath);
  }

  /**
   * Submit the intake form.
   * After a successful submit the app navigates to /chat/:sessionId.
   * TODO (P6.1): add waitForURL('/chat/') after click if navigation is synchronous.
   */
  async submit(): Promise<void> {
    await this.submitButton.click();
  }

  /**
   * Fill and submit the form in one step — convenience method for happy-path tests.
   * TODO (P6.1): extend with all mandatory fields once the final template is known.
   */
  async fillAndSubmit(description: string, serialNumber: string): Promise<void> {
    await this.fillDescription(description);
    await this.fillSerialNumber(serialNumber);
    await this.submit();
  }
}
