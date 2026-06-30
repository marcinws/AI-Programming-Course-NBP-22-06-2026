import { type Page, type Locator } from '@playwright/test';

/**
 * Page Object for the Intake Form — route "/".
 *
 * Selectors are derived from the confirmed data-testid attributes in
 * app/frontend/src/app/features/form/intake-form.component.html (P6.1).
 *
 * mat-select interaction: Angular Material select does NOT use a native <select>
 * element. To choose an option:
 *   1. Click the mat-select trigger to open the overlay panel.
 *   2. Click the desired mat-option inside the panel.
 */
export class IntakeFormPage {
  readonly page: Page;

  // ── Form field locators (matching confirmed data-testid attributes) ──────────

  /** mat-select for Typ zgłoszenia (COMPLAINT / RETURN). */
  readonly caseTypeSelect: Locator;

  /** mat-select for Kategoria sprzętu. */
  readonly equipmentCategorySelect: Locator;

  /** Text input for Model / nazwa urządzenia. */
  readonly modelNameInput: Locator;

  /** Date input for Data zakupu (readonly — interacted via datepicker or direct value). */
  readonly purchaseDateInput: Locator;

  /** Textarea for Przyczyna zgłoszenia. */
  readonly reasonTextarea: Locator;

  /**
   * Hidden file input (class="file-input-hidden").
   * Use setInputFiles() directly — do NOT call click() (it is hidden).
   */
  readonly fileInput: Locator;

  /** Primary submit button. */
  readonly submitButton: Locator;

  /** Global submit error paragraph (role=alert, 5xx/retryable). */
  readonly submitError: Locator;

  // ── Constructor ──────────────────────────────────────────────────────────────

  constructor(page: Page) {
    this.page = page;

    this.caseTypeSelect          = page.getByTestId('form-case-type-select');
    this.equipmentCategorySelect = page.getByTestId('form-equipment-category-select');
    this.modelNameInput          = page.getByTestId('form-model-name-input');
    this.purchaseDateInput       = page.getByTestId('form-purchase-date-input');
    this.reasonTextarea          = page.getByTestId('form-reason-textarea');
    this.fileInput               = page.getByTestId('form-file-input');
    this.submitButton            = page.getByTestId('form-submit-button');
    this.submitError             = page.getByTestId('form-submit-error');
  }

  // ── Navigation ───────────────────────────────────────────────────────────────

  /** Navigate to the intake form (root route). */
  async goto(): Promise<void> {
    await this.page.goto('/');
    // Wait for the mat-select to be visible — confirms metadata loaded
    await this.caseTypeSelect.waitFor({ state: 'visible' });
  }

  // ── mat-select helpers ───────────────────────────────────────────────────────

  /**
   * Select a value from a Material mat-select.
   *
   * Angular Material renders the dropdown in a CDK overlay portal (outside the
   * mat-select DOM). The overlay panel is identified by class ".mat-mdc-select-panel"
   * or ".cdk-overlay-pane". We search for mat-option elements globally.
   *
   * @param selectLocator  The mat-select element (e.g. this.caseTypeSelect)
   * @param optionText     Visible text of the option to select (case-insensitive substring match)
   */
  async selectMatOption(selectLocator: Locator, optionText: string): Promise<void> {
    await selectLocator.click();
    // Options land in a CDK overlay portal — search the full page
    const option = this.page
      .locator('mat-option')
      .filter({ hasText: optionText });
    await option.first().waitFor({ state: 'visible' });
    await option.first().click();
  }

  // ── Date picker helper ───────────────────────────────────────────────────────

  /**
   * Set the purchase date by typing directly into the datepicker input.
   * The field uses [matDatepicker] with placeholder "DD.MM.RRRR".
   * We type the value and press Tab to commit.
   *
   * @param dateStr  Date string in DD.MM.YYYY format (e.g. "01.01.2023")
   */
  async setPurchaseDate(dateStr: string): Promise<void> {
    // The datepicker input is readonly in the template — click to focus, then clear & type
    const input = this.purchaseDateInput;
    await input.click();
    await input.fill(dateStr);
    await this.page.keyboard.press('Tab');
  }

  // ── File upload ──────────────────────────────────────────────────────────────

  /**
   * Upload a file via the hidden file input.
   * Use the absolute path to the file.
   */
  async uploadFile(filePath: string): Promise<void> {
    await this.fileInput.setInputFiles(filePath);
  }

  // ── Submit ───────────────────────────────────────────────────────────────────

  async submit(): Promise<void> {
    await this.submitButton.click();
  }

  // ── Combined fill-and-submit ─────────────────────────────────────────────────

  /**
   * Fill all mandatory form fields and submit.
   *
   * @param opts.caseType          Label text for case type (e.g. "Reklamacja")
   * @param opts.equipmentCategory Label text for equipment category (e.g. "Laptop")
   * @param opts.modelName         Device model string
   * @param opts.purchaseDate      Date in DD.MM.YYYY format (must be in the past)
   * @param opts.reason            Reason text (required for COMPLAINT)
   * @param opts.filePath          Absolute path to the image fixture
   */
  async fillAndSubmit(opts: {
    caseType?: string;
    equipmentCategory: string;
    modelName: string;
    purchaseDate: string;
    reason?: string;
    filePath: string;
  }): Promise<void> {
    if (opts.caseType) {
      await this.selectMatOption(this.caseTypeSelect, opts.caseType);
    }
    await this.selectMatOption(this.equipmentCategorySelect, opts.equipmentCategory);
    await this.modelNameInput.fill(opts.modelName);
    await this.setPurchaseDate(opts.purchaseDate);
    if (opts.reason) {
      await this.reasonTextarea.fill(opts.reason);
    }
    await this.uploadFile(opts.filePath);
    await this.submit();
  }
}
