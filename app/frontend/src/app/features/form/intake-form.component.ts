/**
 * IntakeFormComponent — P2.F1
 *
 * Angular Material reactive form: caseType selector, equipmentCategory selector,
 * modelName input, purchaseDate datepicker (max=today), reason textarea
 * (required only for COMPLAINT), and image upload with type/size guard.
 *
 * Options and image constraints are fetched from GET /api/metadata via CaseService.
 * Submit handler is a stub (createCase is implemented in P4.F1).
 *
 * AC-01..08, AC-23, AC-25 / TAC-002-01..03, 06, 07
 */

import { Component, OnInit, OnDestroy, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { Subscription } from 'rxjs';

import { CaseService } from '../../core/case.service';
import { MetadataResponse, Option, CaseType } from '../../core/models';

// ---------------------------------------------------------------------------
// Custom validator: blocks future dates.
// Produces the same error key as the Material datepicker [max] binding
// so template error checks stay consistent.
// ---------------------------------------------------------------------------
function noFutureDateValidator(): ValidatorFn {
  return (control: AbstractControl) => {
    const val: Date | null = control.value;
    if (!val) return null;
    const today = new Date();
    today.setHours(23, 59, 59, 999);
    return val > today ? { matDatepickerMax: true } : null;
  };
}

@Component({
  selector: 'app-intake-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatSelectModule,
    MatInputModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
  ],
  templateUrl: './intake-form.component.html',
  styleUrl: './intake-form.component.scss',
})
export class IntakeFormComponent implements OnInit, OnDestroy {
  private readonly caseService = inject(CaseService);
  private readonly fb = inject(FormBuilder);
  private readonly subs = new Subscription();

  // -------------------------------------------------------------------------
  // Signals — metadata-driven options and image state
  // -------------------------------------------------------------------------

  readonly caseTypeOptions = signal<Option[]>([]);
  readonly categoryOptions = signal<Option[]>([]);
  readonly imageConstraints = signal<MetadataResponse['imageConstraints'] | null>(null);
  readonly selectedFile = signal<File | null>(null);
  readonly fileError = signal<string | null>(null);
  readonly previewUrl = signal<string | null>(null);

  // -------------------------------------------------------------------------
  // Today's date for datepicker [max] binding
  // -------------------------------------------------------------------------
  readonly today = new Date();

  // -------------------------------------------------------------------------
  // Reactive form
  // -------------------------------------------------------------------------
  readonly form = this.fb.group({
    caseType: this.fb.control<CaseType>('COMPLAINT', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    equipmentCategory: this.fb.control('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    modelName: this.fb.control('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(1)],
    }),
    purchaseDate: this.fb.control<Date | null>(null, {
      validators: [Validators.required, noFutureDateValidator()],
    }),
    reason: this.fb.control('', { nonNullable: true }),
  });

  // -------------------------------------------------------------------------
  // Submit disabled: form invalid OR no (valid) file attached.
  // Plain getter — reads form.valid (reactive form) and signals directly.
  // -------------------------------------------------------------------------
  get isSubmitDisabled(): boolean {
    return this.form.invalid || this.selectedFile() === null || this.fileError() !== null;
  }

  // -------------------------------------------------------------------------
  // Lifecycle
  // -------------------------------------------------------------------------

  ngOnInit(): void {
    // Set initial reason validator based on default caseType (COMPLAINT)
    this.updateReasonValidator(this.form.get('caseType')!.value as CaseType);

    // Update reason validator when caseType changes — keep value, just toggle required
    this.subs.add(
      this.form.get('caseType')!.valueChanges.subscribe((ct) => {
        this.updateReasonValidator(ct as CaseType);
      }),
    );

    // Load metadata — drives selectors + image constraints
    this.subs.add(
      this.caseService.getMetadata().subscribe((meta) => {
        this.caseTypeOptions.set(meta.caseTypes);
        this.categoryOptions.set(meta.equipmentCategories);
        this.imageConstraints.set(meta.imageConstraints);
      }),
    );
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
  }

  // -------------------------------------------------------------------------
  // Internal helpers
  // -------------------------------------------------------------------------

  private updateReasonValidator(caseType: CaseType): void {
    const reasonCtrl = this.form.get('reason')!;
    if (caseType === 'COMPLAINT') {
      reasonCtrl.addValidators(Validators.required);
    } else {
      reasonCtrl.removeValidators(Validators.required);
    }
    reasonCtrl.updateValueAndValidity({ emitEvent: false });
  }

  // -------------------------------------------------------------------------
  // File handling — called from template and directly from tests
  // -------------------------------------------------------------------------

  onFileSelected(file: File): void {
    // Revoke previous preview URL to avoid memory leaks
    const prev = this.previewUrl();
    if (prev) URL.revokeObjectURL(prev);

    this.selectedFile.set(null);
    this.fileError.set(null);
    this.previewUrl.set(null);

    const constraints = this.imageConstraints();
    const acceptedTypes = constraints?.acceptedTypes ?? ['image/jpeg', 'image/png', 'image/webp'];
    const maxBytes = constraints?.maxBytes ?? 10_485_760;

    if (!acceptedTypes.includes(file.type)) {
      this.fileError.set(
        `Niedozwolony format pliku. Akceptowane formaty: JPEG, PNG, WebP.`,
      );
      return;
    }

    if (file.size > maxBytes) {
      this.fileError.set(
        `Przekroczony maksymalny rozmiar pliku (${Math.round(maxBytes / 1024 / 1024)} MB).`,
      );
      return;
    }

    this.selectedFile.set(file);
    this.previewUrl.set(URL.createObjectURL(file));
  }

  onFileInputChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) {
      this.onFileSelected(file);
    }
  }

  removeFile(): void {
    const prev = this.previewUrl();
    if (prev) URL.revokeObjectURL(prev);
    this.selectedFile.set(null);
    this.fileError.set(null);
    this.previewUrl.set(null);
  }

  // -------------------------------------------------------------------------
  // Submit — stub (createCase implemented in P4.F1)
  // -------------------------------------------------------------------------

  onSubmit(): void {
    if (this.isSubmitDisabled) return;
    // P4.F1 will wire this to CaseService.createCase()
    console.info('[IntakeFormComponent] onSubmit — stub, P4.F1 pending');
  }
}
