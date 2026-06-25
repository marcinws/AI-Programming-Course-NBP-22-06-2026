package pl.nbp.copilot.web;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import pl.nbp.copilot.application.exception.SessionNotFoundException;
import pl.nbp.copilot.integration.exception.LlmTimeoutException;
import pl.nbp.copilot.integration.exception.LlmUnavailableException;
import pl.nbp.copilot.web.dto.ErrorCode;
import pl.nbp.copilot.web.dto.ErrorResponse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps every known application failure to a consistent {@link ErrorResponse}.
 * Never returns HTTP 500 or stack traces for known conditions.
 * ADR-001 §3/§6; AC-24; TAC-001-04.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── Bean Validation (body) → 400 VALIDATION_ERROR ────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        return badRequest("Żądanie zawiera błędy walidacji.", fieldErrors);
    }

    // ── Bean Validation (query/path/multipart params) → 400 VALIDATION_ERROR ─

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (ConstraintViolation<?> cv : ex.getConstraintViolations()) {
            String field = cv.getPropertyPath().toString();
            // Strip method prefix if present (e.g. "createCase.modelName" → "modelName")
            int dot = field.lastIndexOf('.');
            if (dot >= 0) field = field.substring(dot + 1);
            fieldErrors.put(field, cv.getMessage());
        }
        return badRequest("Żądanie zawiera błędy walidacji.", fieldErrors);
    }

    // ── COMPLAINT requires reason → 400 ──────────────────────────────────────

    @ExceptionHandler(CaseController.ComplaintReasonRequiredException.class)
    public ResponseEntity<ErrorResponse> handleComplaintReasonRequired(
            CaseController.ComplaintReasonRequiredException ex) {
        Map<String, String> fieldErrors = Map.of("reason", ex.getMessage());
        return badRequest("Żądanie zawiera błędy walidacji.", fieldErrors);
    }

    // ── Reason too long → 400 ────────────────────────────────────────────────

    @ExceptionHandler(CaseController.ReasonTooLongException.class)
    public ResponseEntity<ErrorResponse> handleReasonTooLong(
            CaseController.ReasonTooLongException ex) {
        Map<String, String> fieldErrors = Map.of("reason", ex.getMessage());
        return badRequest("Żądanie zawiera błędy walidacji.", fieldErrors);
    }

    // ── Image required → 400 ─────────────────────────────────────────────────

    @ExceptionHandler(CaseController.ImageRequiredException.class)
    public ResponseEntity<ErrorResponse> handleImageRequired(
            CaseController.ImageRequiredException ex) {
        Map<String, String> fieldErrors = Map.of("image", ex.getMessage());
        return badRequest("Żądanie zawiera błędy walidacji.", fieldErrors);
    }

    // ── Oversized image (controller-level check) → 413 IMAGE_TOO_LARGE ──────

    @ExceptionHandler(CaseController.ImageTooLargeException.class)
    public ResponseEntity<ErrorResponse> handleImageTooLarge(CaseController.ImageTooLargeException ex) {
        log.debug("Image too large: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ErrorResponse(
                        ErrorCode.IMAGE_TOO_LARGE.name(),
                        "Plik jest zbyt duży. Maksymalny rozmiar zdjęcia to 10 MB.",
                        null));
    }

    // ── Oversized image (Spring multipart resolver) → 413 IMAGE_TOO_LARGE ────

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        log.debug("Upload exceeded maximum size: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ErrorResponse(
                        ErrorCode.IMAGE_TOO_LARGE.name(),
                        "Plik jest zbyt duży. Maksymalny rozmiar zdjęcia to 10 MB.",
                        null));
    }

    // ── Unsupported image type → 415 UNSUPPORTED_MEDIA_TYPE ──────────────────

    @ExceptionHandler(CaseController.UnsupportedImageTypeException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedImageType(
            CaseController.UnsupportedImageTypeException ex) {
        log.debug("Unsupported image type: {}", ex.getContentType());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(new ErrorResponse(
                        ErrorCode.UNSUPPORTED_MEDIA_TYPE.name(),
                        "Nieobsługiwany format pliku. Akceptowane formaty: JPEG, PNG, WebP.",
                        null));
    }

    // ── Session not found → 404 SESSION_NOT_FOUND ────────────────────────────

    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSessionNotFound(SessionNotFoundException ex) {
        log.debug("Session not found: {}", ex.getSessionId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(
                        ErrorCode.SESSION_NOT_FOUND.name(),
                        "Sesja nie została znaleziona lub wygasła. Utwórz nową sprawę.",
                        null));
    }

    // ── LLM unavailable → 502 LLM_UNAVAILABLE ────────────────────────────────

    @ExceptionHandler(LlmUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleLlmUnavailable(LlmUnavailableException ex) {
        log.warn("LLM unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse(
                        ErrorCode.LLM_UNAVAILABLE.name(),
                        "Model AI jest chwilowo niedostępny. Spróbuj ponownie za chwilę.",
                        null));
    }

    // ── LLM timeout → 504 LLM_TIMEOUT ────────────────────────────────────────

    @ExceptionHandler(LlmTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleLlmTimeout(LlmTimeoutException ex) {
        log.warn("LLM timeout: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(new ErrorResponse(
                        ErrorCode.LLM_TIMEOUT.name(),
                        "Model AI nie odpowiedział w wymaganym czasie. Spróbuj ponownie.",
                        null));
    }

    // ── Catch-all safety net (should not be reached for known conditions) ─────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        "INTERNAL_ERROR",
                        "Wystąpił nieoczekiwany błąd. Skontaktuj się z administratorem.",
                        null));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ResponseEntity<ErrorResponse> badRequest(String message, Map<String, String> fieldErrors) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(ErrorCode.VALIDATION_ERROR.name(), message, fieldErrors));
    }
}
