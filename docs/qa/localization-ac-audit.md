# Localization & Acceptance-Criteria Audit

**Auditor:** qa-engineer agent
**Date:** 2026-06-30
**Branch:** worktree-poc-implementation
**Stack audited:** Frontend (Angular 20), Backend (Spring Boot 3.5.1), LLM stub (Node.js)
**Method:** Code inspection + live app interaction via Playwright MCP (all three servers running)

---

## 1. Summary

| Category | Total | PASS | FAIL | N-A (deferred) |
|---|---|---|---|---|
| Form (AC-01..08) | 8 | 8 | 0 | 0 |
| Image Analysis (AC-09..12) | 4 | 4 | 0 | 0 |
| AI Decision (AC-13..17) | 5 | 5 | 0 | 0 |
| Chat (AC-18..22) | 5 | 4 | 1 | 0 |
| General (AC-23..26) | 4 | 4 | 0 | 0 |
| **Total** | **26** | **25** | **1** | **0** |

One failure: **AC-21** — `updatedDecision` marker extraction from streaming replies is not surfaced in the UI when the UPDATED_DECISION_MARKER `[UPDATED_DECISION:]` is present in a chat reply (it is implemented in the backend but the frontend ChatComponent does not display a visual indicator that the decision changed inline in the conversation thread).

---

## 2. Full AC Table

### Form

| AC | Description | Status | Evidence |
|---|---|---|---|
| AC-01 | Case Type selector with exactly two options: Complaint and Return | PASS | `GET /api/metadata` returns `[{id:COMPLAINT, labelPl:Reklamacja},{id:RETURN, labelPl:Zwrot}]`; form mat-select populated from metadata |
| AC-02 | Equipment Category selector populated from predefined list | PASS | Metadata response includes 13 categories in Polish (Smartfon, Tablet, Laptop, etc.); form mat-select shows all |
| AC-03 | Model/Name input, required, non-empty after trimming | PASS | `intake-form.component.ts:131` — `Validators.required, Validators.minLength(1)`; backend: `@NotBlank` |
| AC-04 | Purchase Date picker; cannot be future | PASS | `noFutureDateValidator()` at `intake-form.component.ts:51`; template `[max]="today"` on datepicker; backend `@PastOrPresent` |
| AC-05 | Reason required for COMPLAINT, optional for RETURN | PASS | `updateReasonValidator()` at `intake-form.component.ts:184`; backend cross-field check at `CaseController.java:82` |
| AC-06 | Exactly one image upload; blocked without image | PASS | `isSubmitDisabled` getter at `intake-form.component.ts:142`: `selectedFile() === null` blocks submit; backend `ImageRequiredException` |
| AC-07 | Submission blocked with field-level messages while any validation fails | PASS | `mat-error` blocks visible in template for all fields; `[disabled]="isSubmitDisabled"` on submit button |
| AC-08 | File rejected if wrong type or over max size | PASS | Client-side: `onFileSelected()` at `intake-form.component.ts:211` checks type/size before upload; server-side: `UnsupportedImageTypeException`, `ImageTooLargeException` → Polish messages |

### Image Analysis

| AC | Description | Status | Evidence |
|---|---|---|---|
| AC-09 | Image compressed/resized server-side before sending to model | PASS | `CaseService.java:117` calls `imageCompressor.compress()`; `ThumbnailatorImageCompressor` re-encodes to JPEG |
| AC-10 | COMPLAINT → complaint-analysis prompt for vision model | PASS | `OpenRouterVisionAdapter` uses `promptProvider.analysisPrompt(CaseType.COMPLAINT)` → `analysis-complaint.md` |
| AC-11 | RETURN → return-analysis prompt for vision model | PASS | Same adapter uses `analysis-return.md` for RETURN |
| AC-12 | Image description passed to decision agent and retained in session | PASS | `CaseService.java:149` includes `imageAnalysis.description()` in `CreateCaseResult`; session stores `imageAnalysis` |

### AI Decision

| AC | Description | Status | Evidence |
|---|---|---|---|
| AC-13 | Agent returns exactly one decision: Approve/Reject/Escalate | PASS | `parseOutcome()` at `OpenRouterDecisionAdapter.java:208` maps to `DecisionOutcome` enum; unknown → ESCALATE fallback |
| AC-14 | Decision includes justification referencing procedure and case facts | PASS | `composeFirstMessage()` builds Markdown with `**Uzasadnienie:**`, `**Cytowane punkty procedury:**`, `**Kolejne kroki:**` |
| AC-15 | Decision prompt depends on Case Type | PASS | `promptProvider.decisionPrompt(form.caseType())` → `decision-complaint.md` or `decision-return.md` |
| AC-16 | Corresponding procedure injected into agent context | PASS | `policyProvider.procedureText(form.caseType())` → policy document substituted as `{{procedureText}}` in prompt template |
| AC-17 | Low confidence → ESCALATE; no fabricated verdict | PASS | `decision-complaint.md` and `decision-return.md` instruct "zawsze wybieraj ESKALUJ"; `parseDecision()` coerces unknown outcomes to ESCALATE |

### Chat

| AC | Description | Status | Evidence |
|---|---|---|---|
| AC-18 | First chat message: greeting + decision + justification + next-steps | PASS | `composeFirstMessage()` in `OpenRouterDecisionAdapter.java:257` builds: "Dzień dobry," → outcome label → `**Uzasadnienie:**` → `**Cytowane punkty procedury:**` → `**Kolejne kroki:**` → disclaimer. Confirmed live in Playwright test output. |
| AC-19 | Chat interface shows decision as first system message | PASS | `chat.component.html:61` — `@if (msg.isDecision)` renders `chat-first-decision` bubble; `CaseService.java:135` creates `ChatMessage(Role.SYSTEM_ASSISTANT, decision.firstMessageMarkdown(), now)` as message[0] |
| AC-20 | Each agent reply uses full context: form, image description, initial decision, prior turns | PASS | `buildContextMessages()` at `OpenRouterDecisionAdapter.java:295` iterates all session messages; system prompt includes `chatSystemPrompt` |
| AC-21 | Updated recommendation rendered inline; original history preserved | FAIL | See §3 below |
| AC-22 | Off-topic questions: agent declines and redirects | PASS | `system.md` prompt: "gdy użytkownik zadaje pytanie niezwiązane...uprzejmie odmawiasz"; `OFF_TOPIC_MARKER` detection in `streamReply()`; stub has `off-topic` scenario |

### General

| AC | Description | Status | Evidence |
|---|---|---|---|
| AC-23 | All user-facing text in Polish | PASS | See §4 Polish Localization Inventory below |
| AC-24 | On analysis/agent failure: non-technical error, retry without re-entering form | PASS | `GlobalExceptionHandler.java`: all LLM errors mapped to Polish messages (LLM_UNAVAILABLE, LLM_TIMEOUT); `httpErrorInterceptor.ts:27` surfaces Polish snackbar; `handleSubmitError()` in `intake-form.component.ts:297` keeps form filled on 5xx |
| AC-25 | Loading indicator shown; duplicate submission impossible | PASS | `intake-form.component.ts:259` sets `isSubmitting(true)` and calls `form.disable()` on submit; `isSubmitDisabled` getter prevents re-submit; typing indicator in `chat.component.html:103` uses `chat-typing-indicator`; composer disabled during STREAMING |
| AC-26 | Advisory disclaimer in every decision message | PASS | `DISCLAIMER_PL` constant in `OpenRouterDecisionAdapter.java:54-57` appended by `composeFirstMessage()`; additional `<p class="decision-disclaimer">` in `chat.component.html:67-73` hardcoded in template. Confirmed by E2E test assertion. |

---

## 3. FAIL: AC-21 — Updated Decision Not Visually Distinguished

**AC text:** "When new information materially changes the assessment, the agent states an updated recommendation and explains what changed; the original first message stays visible in history."

**What is implemented (backend):**
- `extractUpdatedDecision()` at `OpenRouterDecisionAdapter.java:320` parses `[UPDATED_DECISION:<OUTCOME>]` marker from the streaming reply.
- `streamReply()` returns `ChatReply(fullReply, updatedDecision, offTopic)`.
- `ChatController` publishes an SSE `done` event that includes the `updatedDecision` field when non-null.

**What is implemented (frontend):**
- `onDone()` in `chat.component.ts:189` calls `this.appState.setDecision(updatedDecision)` when `updatedDecision` is present — this updates the header badge and the expansion panel summary.
- Original first message remains in the message list (history preserved).

**Gap:**
The updated decision causes the decision badge in the header to change (e.g., APPROVE → ESCALATE), but there is **no inline message bubble** in the conversation thread that states "the recommendation has changed from X to Y and here is why." The history is preserved, but there is no explicit in-thread notification of the update. The PRD AC-21 states the agent must "state an updated recommendation and explain what changed" — the text does appear in the chat assistant bubble (it is the streaming reply), but the formatted Markdown from `composeFirstMessage()` for the updated decision is NOT appended as a new bubble. The `setDecision()` call silently replaces the badge without inserting a formatted decision bubble into the thread.

**Impacted files:**
- `app/frontend/src/app/features/chat/chat.component.ts:189` — `onDone()` updates state but does not append a new decision-formatted bubble for `updatedDecision`.
- `app/backend/src/main/java/pl/nbp/copilot/integration/OpenRouterDecisionAdapter.java:335` — `composeFirstMessage()` is called for the updated decision but the resulting `firstMessageMarkdown` is sent as part of the SSE done event; it is not rendered.

**Severity:** Medium — The updated decision outcome IS surfaced (in the header badge), so the employee is not blind to the change. The Markdown justification for the change appears in the assistant bubble text. The gap is that the newly composed `firstMessageMarkdown` for the updated decision is not rendered as a separate formatted bubble inline.

**Recommended fix (frontend, `chat.component.ts:onDone`):**
When `updatedDecision` is present and has a `firstMessageMarkdown`, append a new `isDecision=true` message bubble for it — similar to how the initial decision is rendered.

---

## 4. Polish Localization Inventory

All user-facing text verified as Polish. Key areas checked:

### Frontend labels (intake-form.component.html)
- Form title: "Nowe zgłoszenie sprzętowe" — Polish
- Field labels: "Typ zgłoszenia", "Kategoria sprzętu", "Model / nazwa urządzenia", "Data zakupu", "Przyczyna zgłoszenia", "Zdjęcie sprzętu" — Polish
- Placeholder texts: "np. iPhone 15, ThinkPad X1", "DD.MM.RRRR", "Opisz przyczynę zgłoszenia..." — Polish
- Buttons: "Wybierz zdjęcie", "Wyślij zgłoszenie" — Polish
- Validation errors: "Wybierz typ zgłoszenia.", "Wybierz kategorię sprzętu.", "Podaj model lub nazwę urządzenia.", "Podaj datę zakupu.", "Podaj przyczynę reklamacji." — Polish
- File upload hint: "Akceptowane formaty: JPEG, PNG, WebP. Maksymalny rozmiar: 10 MB." — Polish
- Error messages from `GlobalExceptionHandler.java`: all Polish (see §2 AC-08, AC-24)

### Frontend labels (chat.component.html)
- Chat header: "Asystent sprzętowy NBP" — Polish
- Decision summary panel: "Podsumowanie decyzji" — Polish
- Decision outcome labels (in `decisionOutcomeLabel()`): "Zatwierdzona", "Odrzucona", "Do eskalacji" — Polish
- Composer: placeholder "Ctrl+Enter aby wysłać...", label "Wpisz wiadomość" — Polish
- Send button: "Wyślij" — Polish
- Typing indicator aria-label: "Asystent pisze..." — Polish
- Session expired: "Sesja wygasła. Twoja rozmowa nie jest już dostępna." — Polish
- New case button: "Rozpocznij nową sprawę" — Polish
- Bubble role labels: "Ty", "Asystent" — Polish
- Disclaimer: "Powyższa decyzja jest wstępna i może zostać zweryfikowana przez uprawnionego pracownika. Prosimy o kontakt z działem wsparcia w przypadku wątpliwości." — Polish

### Frontend interceptor (http-error.interceptor.ts)
All error code mappings return Polish strings (e.g., "Sesja wygasła lub nie istnieje.", "Przesłane zdjęcie jest zbyt duże.") — Polish.

### Backend error messages (GlobalExceptionHandler.java + CaseController inner exceptions)
All Polish:
- "Plik jest zbyt duży. Maksymalny rozmiar zdjęcia to 10 MB."
- "Nieobsługiwany format pliku. Akceptowane formaty: JPEG, PNG, WebP."
- "Sesja nie została znaleziona lub wygasła. Utwórz nową sprawę."
- "Model AI jest chwilowo niedostępny. Spróbuj ponownie za chwilę."
- "Model AI nie odpowiedział w wymaganym czasie. Spróbuj ponownie."

### Backend prompts (prompts/*.md)
- `system.md`: Polish system prompt
- `decision-complaint.md`, `decision-return.md`: Polish instructions and format
- `analysis-complaint.md`, `analysis-return.md`: Polish analysis instructions
- `chat.md`: Polish chat continuation prompt

### Backend metadata API
Case type labels: "Reklamacja", "Zwrot" — Polish.
Equipment category labels: all Polish (verified from live API response).

---

## 5. Routing to Teams

| Item | Target | Details |
|---|---|---|
| AC-21 gap | fe-developer | `chat.component.ts:onDone()` — when `updatedDecision` is non-null, append a new `isDecision=true` bubble with `updatedDecision.firstMessageMarkdown` instead of only calling `setDecision()` |
