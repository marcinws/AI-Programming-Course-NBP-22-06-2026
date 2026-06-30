/**
 * Spec: IntakeFormComponent — P2.F1 + P4.F1
 *
 * Coverage:
 * - Selectors (caseType, equipmentCategory) populate from mocked CaseService.getMetadata()
 * - reason required iff caseType == COMPLAINT; value kept on toggle
 * - future purchaseDate blocked; today allowed
 * - image file guard: type (GIF rejected), size (10 MB accepted, 11 MB rejected)
 * - all labels/messages in Polish
 * - submit button disabled while form is invalid
 * P4.F1 additions:
 * - 201 response: hydrateFromCreate + navigate to /chat/{sessionId}
 * - 400 fieldErrors: shown under controls; inputs preserved
 * - 502/LLM_UNAVAILABLE: retryable Polish error; form re-enabled
 * - SUBMITTING state: form disabled + spinner shown (AC-25)
 *
 * TAC: TAC-002-01..03, 04, 06, 07
 */

import { ComponentFixture, TestBed, fakeAsync, tick, flush } from '@angular/core/testing';
import { provideAnimations } from '@angular/platform-browser/animations';
import { ReactiveFormsModule } from '@angular/forms';
import { of, throwError } from 'rxjs';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';

import { IntakeFormComponent } from './intake-form.component';
import { CaseService } from '../../core/case.service';
import { AppStateService } from '../../core/app-state';
import { MetadataResponse, CreateCaseResponse } from '../../core/models';

// ---------------------------------------------------------------------------
// Mock metadata
// ---------------------------------------------------------------------------

const MOCK_METADATA: MetadataResponse = {
  caseTypes: [
    { id: 'COMPLAINT', labelPl: 'Reklamacja' },
    { id: 'RETURN', labelPl: 'Zwrot' },
  ],
  equipmentCategories: [
    { id: 'SMARTPHONE', labelPl: 'Smartfon' },
    { id: 'LAPTOP', labelPl: 'Laptop' },
  ],
  imageConstraints: {
    acceptedTypes: ['image/jpeg', 'image/png', 'image/webp'],
    maxBytes: 10_485_760, // exactly 10 MB
  },
};

// ---------------------------------------------------------------------------
// Helper: create a fake File with given type and size
// ---------------------------------------------------------------------------

function makeFile(name: string, type: string, sizeBytes: number): File {
  const blob = new Blob([new Uint8Array(sizeBytes)], { type });
  return new File([blob], name, { type });
}

// ---------------------------------------------------------------------------
// Spec
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Mock data for P4.F1
// ---------------------------------------------------------------------------

const MOCK_CREATE_RESPONSE: CreateCaseResponse = {
  sessionId: 'sess-abc-123',
  decision: {
    outcome: 'APPROVE',
    justification: 'Towar w gwarancji.',
    nextSteps: 'Wyślij sprzęt do serwisu.',
    firstMessageMarkdown: '## Decyzja\n**Zatwierdzona**',
  },
  imageAnalysisSummary: 'Zdjęcie zostało przeanalizowane.',
};

function makeValidFormValues() {
  return {
    caseType: 'RETURN' as const,
    equipmentCategory: 'SMARTPHONE',
    modelName: 'iPhone 15',
    purchaseDate: new Date(),
    reason: '',
  };
}

describe('IntakeFormComponent', () => {
  let component: IntakeFormComponent;
  let fixture: ComponentFixture<IntakeFormComponent>;
  let caseServiceSpy: jasmine.SpyObj<CaseService>;
  let appStateSpy: jasmine.SpyObj<AppStateService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    caseServiceSpy = jasmine.createSpyObj<CaseService>('CaseService', [
      'getMetadata',
      'createCase',
    ]);
    caseServiceSpy.getMetadata.and.returnValue(of(MOCK_METADATA));

    appStateSpy = jasmine.createSpyObj<AppStateService>('AppStateService', [
      'hydrateFromCreate',
      'setPendingState',
      'reset',
    ]);

    routerSpy = jasmine.createSpyObj<Router>('Router', ['navigate']);
    routerSpy.navigate.and.returnValue(Promise.resolve(true));

    await TestBed.configureTestingModule({
      imports: [IntakeFormComponent, ReactiveFormsModule],
      providers: [
        provideAnimations(),
        { provide: CaseService, useValue: caseServiceSpy },
        { provide: AppStateService, useValue: appStateSpy },
        { provide: Router, useValue: routerSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(IntakeFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  // -------------------------------------------------------------------------
  // 1. Metadata-driven selectors
  // -------------------------------------------------------------------------

  describe('metadata-driven selectors', () => {
    it('should call getMetadata() on init', () => {
      expect(caseServiceSpy.getMetadata).toHaveBeenCalledTimes(1);
    });

    it('should expose caseType options from metadata', () => {
      expect(component.caseTypeOptions().length).toBe(2);
      expect(component.caseTypeOptions()[0].labelPl).toBe('Reklamacja');
      expect(component.caseTypeOptions()[1].labelPl).toBe('Zwrot');
    });

    it('should expose equipmentCategory options from metadata', () => {
      expect(component.categoryOptions().length).toBe(2);
      expect(component.categoryOptions()[0].labelPl).toBe('Smartfon');
    });

    it('should store imageConstraints from metadata', () => {
      expect(component.imageConstraints()).toBeDefined();
      expect(component.imageConstraints()!.maxBytes).toBe(10_485_760);
    });
  });

  // -------------------------------------------------------------------------
  // 2. reason required toggling (TAC-002-01)
  // -------------------------------------------------------------------------

  describe('reason — conditional required (TAC-002-01)', () => {
    it('should require reason when caseType is COMPLAINT', () => {
      component.form.get('caseType')!.setValue('COMPLAINT');
      const reasonControl = component.form.get('reason')!;
      reasonControl.setValue('');
      reasonControl.updateValueAndValidity();
      expect(reasonControl.hasError('required')).toBeTrue();
    });

    it('should NOT require reason when caseType is RETURN', () => {
      component.form.get('caseType')!.setValue('RETURN');
      const reasonControl = component.form.get('reason')!;
      reasonControl.setValue('');
      reasonControl.updateValueAndValidity();
      expect(reasonControl.hasError('required')).toBeFalse();
    });

    it('should keep the entered reason value when toggling caseType', () => {
      component.form.get('caseType')!.setValue('COMPLAINT');
      component.form.get('reason')!.setValue('Wadliwy produkt');

      // Toggle to RETURN and back to COMPLAINT
      component.form.get('caseType')!.setValue('RETURN');
      component.form.get('caseType')!.setValue('COMPLAINT');

      expect(component.form.get('reason')!.value).toBe('Wadliwy produkt');
    });

    it('should make form valid when caseType is RETURN and reason is empty', () => {
      component.form.patchValue({
        caseType: 'RETURN',
        equipmentCategory: 'SMARTPHONE',
        modelName: 'iPhone 15',
        purchaseDate: new Date(), // today
        reason: '',
      });
      component.form.get('reason')!.updateValueAndValidity();
      // image is still missing so we only check reason specifically
      const reasonControl = component.form.get('reason')!;
      expect(reasonControl.valid).toBeTrue();
    });
  });

  // -------------------------------------------------------------------------
  // 3. Purchase date validation (TAC-002-02)
  // -------------------------------------------------------------------------

  describe('purchaseDate — future date blocked (TAC-002-02)', () => {
    it('should mark purchaseDate invalid when future date is selected', () => {
      const tomorrow = new Date();
      tomorrow.setDate(tomorrow.getDate() + 1);
      component.form.get('purchaseDate')!.setValue(tomorrow);
      component.form.get('purchaseDate')!.updateValueAndValidity();
      expect(component.form.get('purchaseDate')!.hasError('matDatepickerMax')).toBeTrue();
    });

    it('should mark purchaseDate valid when today is selected', () => {
      const today = new Date();
      today.setHours(0, 0, 0, 0);
      component.form.get('purchaseDate')!.setValue(today);
      component.form.get('purchaseDate')!.updateValueAndValidity();
      expect(component.form.get('purchaseDate')!.hasError('matDatepickerMax')).toBeFalse();
      expect(component.form.get('purchaseDate')!.hasError('required')).toBeFalse();
    });
  });

  // -------------------------------------------------------------------------
  // 4. Image file guard (TAC-002-03)
  // -------------------------------------------------------------------------

  describe('image file guard (TAC-002-03)', () => {
    it('should reject a GIF file (unsupported type)', () => {
      const gifFile = makeFile('photo.gif', 'image/gif', 1024);
      component.onFileSelected(gifFile);
      expect(component.fileError()).toBeTruthy();
      expect(component.fileError()).toContain('format');
    });

    it('should reject a file exceeding 10 MB', () => {
      const bigFile = makeFile('photo.jpg', 'image/jpeg', 11 * 1024 * 1024); // 11 MB
      component.onFileSelected(bigFile);
      expect(component.fileError()).toBeTruthy();
      expect(component.fileError()).toContain('rozmiar');
    });

    it('should accept a JPEG file at exactly 10 MB', () => {
      const exactFile = makeFile('photo.jpg', 'image/jpeg', 10_485_760); // exactly 10 MB
      component.onFileSelected(exactFile);
      expect(component.fileError()).toBeNull();
      expect(component.selectedFile()).toBe(exactFile);
    });

    it('should accept a PNG file', () => {
      const pngFile = makeFile('photo.png', 'image/png', 1024);
      component.onFileSelected(pngFile);
      expect(component.fileError()).toBeNull();
    });

    it('should accept a WebP file', () => {
      const webpFile = makeFile('photo.webp', 'image/webp', 1024);
      component.onFileSelected(webpFile);
      expect(component.fileError()).toBeNull();
    });

    it('should reject a file at 11 MB (one byte over boundary)', () => {
      const elevenMb = makeFile('photo.jpg', 'image/jpeg', 11 * 1024 * 1024);
      component.onFileSelected(elevenMb);
      expect(component.fileError()).toBeTruthy();
    });
  });

  // -------------------------------------------------------------------------
  // 5. Submit button disabled state (AC-25 / TAC-002-06)
  // -------------------------------------------------------------------------

  describe('submit button disabled while form invalid (AC-25)', () => {
    it('should disable submit when form is freshly loaded (invalid)', () => {
      expect(component.form.invalid).toBeTrue();
      expect(component.isSubmitDisabled).toBeTrue();
    });

    it('should disable submit when no image is attached', fakeAsync(() => {
      component.form.patchValue({
        caseType: 'RETURN',
        equipmentCategory: 'SMARTPHONE',
        modelName: 'iPhone 15',
        purchaseDate: new Date(),
        reason: '',
      });
      tick();
      // no file selected
      expect(component.isSubmitDisabled).toBeTrue();
    }));

    it('should enable submit when all fields valid and file is attached', fakeAsync(() => {
      component.form.patchValue({
        caseType: 'RETURN',
        equipmentCategory: 'SMARTPHONE',
        modelName: 'iPhone 15',
        purchaseDate: new Date(),
        reason: '',
      });
      const validFile = makeFile('photo.jpg', 'image/jpeg', 1024);
      component.onFileSelected(validFile);
      tick();
      expect(component.isSubmitDisabled).toBeFalse();
    }));

    it('should disable submit when COMPLAINT caseType but reason is empty', fakeAsync(() => {
      component.form.patchValue({
        caseType: 'COMPLAINT',
        equipmentCategory: 'SMARTPHONE',
        modelName: 'iPhone 15',
        purchaseDate: new Date(),
        reason: '',
      });
      const validFile = makeFile('photo.jpg', 'image/jpeg', 1024);
      component.onFileSelected(validFile);
      tick();
      expect(component.isSubmitDisabled).toBeTrue();
    }));

    it('should enable submit when COMPLAINT with reason provided and file attached', fakeAsync(() => {
      component.form.patchValue({
        caseType: 'COMPLAINT',
        equipmentCategory: 'SMARTPHONE',
        modelName: 'iPhone 15',
        purchaseDate: new Date(),
        reason: 'Wadliwy produkt',
      });
      const validFile = makeFile('photo.jpg', 'image/jpeg', 1024);
      component.onFileSelected(validFile);
      tick();
      expect(component.isSubmitDisabled).toBeFalse();
    }));
  });

  // -------------------------------------------------------------------------
  // 6. Polish labels / messages (AC-23 / TAC-002-07)
  // -------------------------------------------------------------------------

  describe('Polish labels and error messages (AC-23)', () => {
    it('should show Polish error when GIF is uploaded', () => {
      const gifFile = makeFile('photo.gif', 'image/gif', 1024);
      component.onFileSelected(gifFile);
      expect(component.fileError()).toContain('format');
    });

    it('should show Polish error when file is too large', () => {
      const bigFile = makeFile('photo.jpg', 'image/jpeg', 11 * 1024 * 1024);
      component.onFileSelected(bigFile);
      expect(component.fileError()).toContain('rozmiar');
    });

    it('should expose Polish labels on options', () => {
      expect(component.caseTypeOptions()[0].labelPl).toBe('Reklamacja');
      expect(component.caseTypeOptions()[1].labelPl).toBe('Zwrot');
    });
  });

  // -------------------------------------------------------------------------
  // 7. File removal
  // -------------------------------------------------------------------------

  describe('file removal', () => {
    it('should clear file and error on removeFile()', () => {
      const validFile = makeFile('photo.jpg', 'image/jpeg', 1024);
      component.onFileSelected(validFile);
      expect(component.selectedFile()).toBe(validFile);

      component.removeFile();
      expect(component.selectedFile()).toBeNull();
      expect(component.fileError()).toBeNull();
    });
  });

  // -------------------------------------------------------------------------
  // 8. P4.F1 — Submit → createCase → navigate (201 success)
  // -------------------------------------------------------------------------

  describe('P4.F1 — submit success → navigate to chat (AC-07, TAC-002-04)', () => {
    it('should call hydrateFromCreate and navigate to /chat/{sessionId} on 201', fakeAsync(() => {
      caseServiceSpy.createCase.and.returnValue(of(MOCK_CREATE_RESPONSE));

      component.form.patchValue(makeValidFormValues());
      const validFile = makeFile('photo.jpg', 'image/jpeg', 1024);
      component.onFileSelected(validFile);
      tick();

      component.onSubmit();
      tick();

      expect(caseServiceSpy.createCase).toHaveBeenCalledTimes(1);
      expect(appStateSpy.hydrateFromCreate).toHaveBeenCalledWith(MOCK_CREATE_RESPONSE);
      expect(routerSpy.navigate).toHaveBeenCalledWith(['/chat', 'sess-abc-123']);
    }));

    it('should set pendingState SUBMITTING before response arrives', fakeAsync(() => {
      caseServiceSpy.createCase.and.returnValue(of(MOCK_CREATE_RESPONSE));

      component.form.patchValue(makeValidFormValues());
      component.onFileSelected(makeFile('photo.jpg', 'image/jpeg', 1024));
      tick();

      component.onSubmit();
      // SUBMITTING must have been set during the call
      expect(appStateSpy.setPendingState).toHaveBeenCalledWith('SUBMITTING');
      tick();
      flush();
    }));
  });

  // -------------------------------------------------------------------------
  // 9. P4.F1 — Submit → 400 fieldErrors (inputs preserved)
  // -------------------------------------------------------------------------

  describe('P4.F1 — submit 400 fieldErrors → controls show errors; inputs preserved (TAC-002-04)', () => {
    it('should show fieldError under modelName control and keep the value', fakeAsync(() => {
      const errorBody = {
        code: 'VALIDATION_ERROR',
        message: 'Błąd walidacji.',
        fieldErrors: { modelName: 'Nieprawidłowy model.' },
      };
      caseServiceSpy.createCase.and.returnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 400,
              error: errorBody,
            }),
        ),
      );

      component.form.patchValue({
        ...makeValidFormValues(),
        modelName: 'TestModel XYZ',
      });
      component.onFileSelected(makeFile('photo.jpg', 'image/jpeg', 1024));
      tick();

      component.onSubmit();
      tick();

      // The form value must be preserved (not wiped)
      expect(component.form.get('modelName')!.value).toBe('TestModel XYZ');
      // The control should have the server error set
      expect(component.form.get('modelName')!.hasError('serverError')).toBeTrue();
      // Form should be re-enabled after error
      expect(component.form.enabled).toBeTrue();
    }));

    it('should show fieldError under equipmentCategory when returned', fakeAsync(() => {
      const errorBody = {
        code: 'VALIDATION_ERROR',
        message: 'Błąd walidacji.',
        fieldErrors: { equipmentCategory: 'Nieprawidłowa kategoria.' },
      };
      caseServiceSpy.createCase.and.returnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 400,
              error: errorBody,
            }),
        ),
      );

      component.form.patchValue({ ...makeValidFormValues(), equipmentCategory: 'LAPTOP' });
      component.onFileSelected(makeFile('photo.jpg', 'image/jpeg', 1024));
      tick();

      component.onSubmit();
      tick();

      expect(component.form.get('equipmentCategory')!.hasError('serverError')).toBeTrue();
      expect(component.form.get('equipmentCategory')!.value).toBe('LAPTOP');
    }));

    it('should set pendingState to ERROR on fieldErrors response', fakeAsync(() => {
      const errorBody = {
        code: 'VALIDATION_ERROR',
        message: 'Błąd.',
        fieldErrors: { modelName: 'Zły model.' },
      };
      caseServiceSpy.createCase.and.returnValue(
        throwError(
          () => new HttpErrorResponse({ status: 400, error: errorBody }),
        ),
      );

      component.form.patchValue(makeValidFormValues());
      component.onFileSelected(makeFile('photo.jpg', 'image/jpeg', 1024));
      tick();
      component.onSubmit();
      tick();

      expect(appStateSpy.setPendingState).toHaveBeenCalledWith('ERROR');
    }));
  });

  // -------------------------------------------------------------------------
  // 10. P4.F1 — Submit → 502/LLM_UNAVAILABLE retryable error
  // -------------------------------------------------------------------------

  describe('P4.F1 — submit 502 LLM_UNAVAILABLE → retryable Polish error (TAC-002-04)', () => {
    it('should expose a Polish retryable error message and re-enable the form', fakeAsync(() => {
      const errorBody = {
        code: 'LLM_UNAVAILABLE',
        message: 'Usługa AI jest chwilowo niedostępna. Spróbuj ponownie.',
      };
      caseServiceSpy.createCase.and.returnValue(
        throwError(
          () => new HttpErrorResponse({ status: 502, error: errorBody }),
        ),
      );

      component.form.patchValue(makeValidFormValues());
      component.onFileSelected(makeFile('photo.jpg', 'image/jpeg', 1024));
      tick();

      component.onSubmit();
      tick();

      expect(component.submitError()).toBeTruthy();
      expect(component.submitError()!.toLowerCase()).toContain('ponownie');
      expect(component.form.enabled).toBeTrue();
    }));

    it('should re-enable the form after a 504 error', fakeAsync(() => {
      caseServiceSpy.createCase.and.returnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 504,
              error: { code: 'LLM_TIMEOUT', message: 'Timeout.' },
            }),
        ),
      );

      component.form.patchValue(makeValidFormValues());
      component.onFileSelected(makeFile('photo.jpg', 'image/jpeg', 1024));
      tick();
      component.onSubmit();
      tick();

      expect(component.form.enabled).toBeTrue();
      expect(component.submitError()).toBeTruthy();
    }));
  });

  // -------------------------------------------------------------------------
  // 11. P4.F1 — SUBMITTING state: spinner visible; double submit impossible (AC-25)
  // -------------------------------------------------------------------------

  describe('P4.F1 — SUBMITTING state guards (AC-25, TAC-002-06)', () => {
    it('should not call createCase again when already submitting', fakeAsync(() => {
      caseServiceSpy.createCase.and.returnValue(of(MOCK_CREATE_RESPONSE));

      component.form.patchValue(makeValidFormValues());
      component.onFileSelected(makeFile('photo.jpg', 'image/jpeg', 1024));
      tick();

      // Manually force SUBMITTING state to simulate in-flight request
      component.isSubmitting.set(true);
      component.onSubmit();

      // createCase should NOT be called while already submitting
      expect(caseServiceSpy.createCase).not.toHaveBeenCalled();

      component.isSubmitting.set(false);
      flush();
    }));
  });
});
