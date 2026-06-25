# Implementation Plan — Hardware Service Decision Copilot (PoC / MVP)

> **Status:** Draft for approval · **Date:** 2026-06-25
> **Owner of execution:** Orchestrator (delegates only; writes no production code).
> **Sources:** [PRD](PRD-Product-Requirements-Document.md) · [ADR-000](ADR/000-main-architecture.md) · [ADR-001](ADR/001-backend-api.md) · [ADR-002](ADR/002-frontend-angular.md) · [ADR-003](ADR/003-ai-llm-integration.md) · [ADR-004](ADR/004-data-and-persistence.md) · [ADR-005](ADR/005-project-setup.md) · [design-guidelines](../example/design-guidelines.md)

This document is the single coordination artifact for building the PoC. It defines **what exists today**, the **working conventions** every step follows (TDD, verification, commits), the **three agents** and how they run **in parallel without colliding**, a **dependency matrix + gate diagram**, and a **phase-by-phase list of commit-sized steps**. Each step lists its owner, dependencies, the exact ADR/AC/TAC references, the tests to write **first**, the deliverable files, the verification command, the commit message, and the **scoped context** to hand the agent (only what that task needs — never the whole app).

---

## 0. Current state (assessed 2026-06-25)

Scaffolding (ADR-005) is **already in place** in the main checkout but **untracked in git**:

- **Backend** `app/backend/`: Spring Boot **3.5.1**, Java 21, Maven Wrapper. `pom.xml` already declares every dependency: `spring-boot-starter-web`, `-validation`, `-actuator`, `com.openai:openai-java:4.41.0`, `net.coobird:thumbnailator:0.4.20`, `me.paulschwarz:spring-dotenv:4.0.0`, `spring-boot-starter-test`, `com.squareup.okhttp3:mockwebserver:4.12.0`. Layered packages (`web`, `application`, `domain`, `integration`, `support`) exist as empty `package-info.java` stubs. `application.yaml` present. Policy docs copied to `src/main/resources/policies/`. **No** feature classes, **no** `prompts/` folder.
- **Frontend** `app/frontend/`: Angular **20.3**, Angular Material **20.2**, `ngx-markdown`, `marked`, `@microsoft/fetch-event-source` installed. Standalone + routing + SCSS. `core/case.service.ts`, `core/models.ts`, `features/form/intake-form.component.ts`, `features/chat/chat.component.ts` exist as **7–10 line placeholders**.

**Implication — read before starting:** A git worktree only checks out *committed* files; **untracked files do not transfer into an isolation worktree**. Therefore **Step P0.1 (commit the baseline) is a hard gate** before any agent works in isolation. Until the scaffolding is committed, agents must either run in the main checkout or the worktree will be missing `app/`.

**Conclusion:** Phase 0 of ADR-005 reduces to *verify the existing scaffold is green and commit it*. The real work is the feature build, tests, and E2E below.

---

## 1. Working conventions (apply to EVERY step)

**TDD loop (mandatory, per AGENTS.md):**
1. Start from the spec (PRD/ADR/AC), not existing code.
2. Write or extend the test(s) named in the step **first**.
3. Run them; confirm they **fail for the expected reason** (red).
4. Write the minimum production code to pass (green).
5. Run the step's verification command for the changed scope.
6. Refactor only while green.

**Verification before each commit (changed scope only):**
- Backend: `./mvnw -q verify` (from `app/backend`).
- Frontend: `npm test -- --watch=false --browsers=ChromeHeadless && npm run lint && npm run build` (from `app/frontend`).
- Runtime-affecting change: start the app and confirm it boots (`./mvnw spring-boot:run` / `npm start`). Tests passing ≠ app working.

**Commits:** one logical change per step; format `Area: summary` (`Backend:`, `Frontend:`, `QA:`, `Docs:`). Commit only when the step's scope is green. **Do not push** (per user + AGENTS.md). All work on branch `worktree-poc-implementation` (this worktree).

**Co-author trailer** on every commit:
```
Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

**Context7/docs:** agents fetch live docs via the `find-docs` skill / Context7 handles before using a library API (Spring Boot `/spring-projects/spring-boot`, OpenAI Java SDK `/openai/openai-java`, Angular `/angular/angular`, Material `/websites/material_angular_dev`, ngx-markdown, Playwright). Do not rely on training memory for API specifics.

**Language:** all user-facing strings, labels, messages, prompts, and agent output in **Polish** (AC-23). Code identifiers and comments in English.

**AI in tests:** the OpenRouter endpoint is the **only** thing mocked at the integration layer (MockWebServer/WireMock pointed at by `OPENROUTER_BASE_URL`). Unit tests mock all deps. E2E runs the real stack against a **local OpenAI-compatible stub** (deterministic). The real-model smoke is a **manual, opt-in** step the user runs — **deferred**, not part of automated verification.

---

## 2. Agents & the parallelization principle

| Agent | Owns | Never touches |
|---|---|---|
| **be-developer** | `app/backend/**` — domain, application services, ports, integration adapters, support, web controllers, prompts, BE unit + integration tests. | `app/frontend/**` |
| **fe-developer** | `app/frontend/**` — core service/models/interceptor, form view, chat view, FE unit tests. | `app/backend/**` |
| **qa-engineer** | `app/e2e/**` (new Playwright project), the local OpenAI-compatible **stub server** used by E2E, cross-cutting localization/AC audit. | production code of BE/FE (reads only) |

**The linchpin is contract-first.** The REST/SSE contract (DTO shapes, enums, error codes, SSE event shapes, metadata payload) from ADR-001/004 is frozen in **Gate B** as concrete artifacts: backend DTO/enum types **and** the mirrored frontend TS interfaces, produced from one shared field list the orchestrator supplies to both agents. After Gate B, BE and FE build **in parallel** against the frozen contract and only re-sync if the contract changes (which requires an explicit contract-change step touching both sides). File ownership never overlaps, so parallel agents cannot create merge conflicts.

---

## 3. Dependency matrix & gates

**Gates (hard barriers — everything in a later gate depends on all of the earlier gate):**

```
GATE A  P0  Baseline committed + green ........................ blocks everything
   |
GATE B  P1  Contract frozen: enums + DTOs + error model + ..... unblocks parallel BE/FE
            /api/metadata live (BE) + mirrored TS models (FE)
            + QA stub server scaffolded
   |
   +------------------ parallel lanes ------------------+
   |  BE lane (be-developer)   |  FE lane (fe-developer) |  QA lane (qa-engineer)
   |                           |                         |
GATE C  P2  domain+session+    |  metadata-driven intake |  stub canned responses
            image+providers    |  form (full validation) |  + Playwright project
   |                           |                         |
GATE D  P3  AI adapters        |  chat skeleton + SSE    |  (assists stub fixtures)
            (vision/decision)  |  client wiring          |
   |                           |                         |
GATE E  P4  orchestration +    |  form→chat flow + live  |
            controllers + SSE  |  streaming + states     |
   |                           |                         |
GATE F  P5  BE integration     |  FE unit suite final    |
            tests (all TACs)   |                         |
   |
GATE G  P6  E2E full stack (qa) + localization/AC audit + manual real-model smoke (user)
```

**Step dependency table** (step → must finish first → unblocks):

| Step | Owner | Depends on | Unblocks |
|---|---|---|---|
| P0.1 Baseline commit + verify | be+fe | — | everything |
| P1.1 Domain enums + labels | be | P0.1 | P1.2, P1.3, P2.* |
| P1.2 Error model + DTO types | be | P1.1 | P1.4, P4.* |
| P1.3 `/api/metadata` endpoint | be | P1.1, P1.2 | F-form, P5 |
| P1.4 FE TS models (mirror) | fe | P1.2 (field list) | all FE |
| P1.5 QA stub server skeleton | qa | P0.1 | P5, P6 |
| P2.B1 Session store | be | P1.1 | P4.B1/B2 |
| P2.B2 Image compressor | be | P0.1 | P4.B1 |
| P2.B3 Prompt + Policy providers | be | P1.1 | P3.B* |
| P2.F1 Intake form (validation) | fe | P1.3, P1.4 | P4.F1 |
| P3.B1 Vision adapter | be | P2.B3 | P4.B1 |
| P3.B2 Decision adapter (+stream) | be | P2.B3, P1.2 | P4.B1/B2 |
| P3.F1 Chat skeleton + SSE client | fe | P1.4 | P4.F2 |
| P4.B1 CaseService + POST /cases | be | P2.B1/B2, P3.B1/B2 | P4.B3, P5 |
| P4.B2 ChatService + SSE endpoint | be | P2.B1, P3.B2 | P5 |
| P4.B3 GET /cases/{id} + CORS + errors | be | P4.B1, P1.2 | P5 |
| P4.F1 Form→create→navigate | fe | P2.F1, P4.B1\* | P6 |
| P4.F2 Streaming chat turn + states | fe | P3.F1, P4.B2\* | P6 |
| P5.B* BE integration tests | be+qa | P4.B1/B2/B3, P1.5 | P6 |
| P5.F1 FE unit suite finalize | fe | P4.F1/F2 | P6 |
| P6.1 Playwright E2E | qa | P5.*, P1.5 | — |
| P6.2 Localization + AC audit | qa | P4.* | — |
| P6.3 Manual real-model smoke | **user** | P6.1 | — |

\* FE depends on BE only at *integration/E2E* time; for unit work FE uses `HttpTestingController` and mocked SSE, so FE and BE proceed concurrently within each gate.

---

## 4. Phases & steps

> Each step is one commit. "Tests first" lists the failing tests to author before code. "Context to hand the agent" is the **scoped** brief — give the agent that and nothing more.

### GATE A — Phase 0: Baseline

**P0.1 — Commit & verify the existing scaffold** · _owner: be-developer then fe-developer (or orchestrator)_
- **Refs:** ADR-005 §3–7; TAC-005-01/02/03/04/05/06.
- **Do:** From the main checkout, run `./mvnw -q verify` (backend) and `npm ci && npm test -- --watch=false --browsers=ChromeHeadless && npm run build` (frontend) to confirm green. Confirm backend boots on `:8080` (actuator health UP) and `npm start` proxies `/api`. Then commit the scaffolding so it exists on the branch (resolves the untracked-files / worktree gap from §0).
- **Verify:** both suites green; both apps boot.
- **Commit:** `Chore: commit verified backend + frontend scaffold baseline`
- **Context to agent:** ADR-005 §3 toolchain table + §7 commands; the §0 note that scaffolding is untracked and must be committed before isolated work.

---

### GATE B — Phase 1: Contract freeze (unblocks parallel work)

**P1.1 — Domain enums + Polish labels** · _be_
- **Refs:** ADR-004 §4 (CaseType, DecisionOutcome, EquipmentCategory full list incl. OTHER); AC-01/02; TAC-004-06.
- **Tests first:** enum completeness test (all 13 categories present incl. OTHER); every category maps to a non-empty Polish label.
- **Deliver:** `domain/CaseType`, `domain/DecisionOutcome`, `domain/EquipmentCategory` (with Polish label mapping).
- **Verify:** `./mvnw -q -Dtest=*Enum* test`.
- **Commit:** `Backend: domain enums (case type, outcome, equipment category) with PL labels`
- **Context:** ADR-004 §4 "Enumerations" block verbatim; AC-01/02.

**P1.2 — Error model + REST DTO types** · _be_
- **Refs:** ADR-001 §4 (all DTOs), §4 error codes; AC-07/08/24.
- **Tests first:** `ErrorResponse` serializes `{code,message,fieldErrors?}`; code enum contains `VALIDATION_ERROR, SESSION_NOT_FOUND, IMAGE_TOO_LARGE, UNSUPPORTED_MEDIA_TYPE, LLM_UNAVAILABLE, LLM_TIMEOUT`.
- **Deliver:** `web/dto/*` records (CreateCaseRequest binding, CreateCaseResponse, ChatRequest, SessionResponse, MetadataResponse, ErrorResponse) + error `code` enum. No logic yet.
- **Verify:** `./mvnw -q -Dtest=*Dto*,*Error* test`.
- **Commit:** `Backend: REST DTOs and error model`
- **Context:** ADR-001 §4 "Data Structures (DTOs)" verbatim. **Orchestrator also extracts the exact field/enum list and hands the identical list to P1.4 so FE/BE stay byte-compatible.**

**P1.3 — `GET /api/metadata` endpoint** · _be_
- **Refs:** ADR-001 §5; AC-02/08; ADR-002 §6 (backend-driven options); TAC-004-06.
- **Tests first (MockMvc):** `200` with `caseTypes[]`, `equipmentCategories[]` (Polish labels, OTHER present), `imageConstraints{acceptedTypes,maxBytes}` reflecting config.
- **Deliver:** `MetadataController` + assembling service reading enums + `app.image.*` config.
- **Verify:** `./mvnw -q -Dtest=MetadataControllerTest test`.
- **Commit:** `Backend: GET /api/metadata serving case types, categories, image constraints`
- **Context:** ADR-001 §4 MetadataResponse + §5; ADR-000 §7 image env vars.

**P1.4 — Frontend TS models (mirror of DTOs)** · _fe_
- **Refs:** ADR-002 §4; the field list from P1.2.
- **Tests first:** type-level compile + a small guard test that a sample metadata/decision payload parses into the interfaces.
- **Deliver:** `core/models.ts` — interfaces for metadata, CreateCaseResponse, Decision, SessionResponse, ChatRequest, SSE event union (`token|done|error`), `ErrorResponse`, plus client-only `PendingState`, `DisplayMessage` (ADR-002 §4).
- **Verify:** `npm run build` + the guard spec.
- **Commit:** `Frontend: typed models mirroring REST/SSE contract`
- **Context:** the exact field list from P1.2 + ADR-002 §4 client-only additions. **Must match P1.2 names/enums exactly.**

**P1.5 — QA: local OpenAI-compatible stub server skeleton** · _qa_
- **Refs:** ADR-000 §10 (E2E determinism); ADR-003 §8.
- **Tests first:** a smoke test that the stub returns a canned chat-completion and a chunked stream when `OPENROUTER_BASE_URL` points at it.
- **Deliver:** `app/e2e/stub/` — minimal OpenAI-compatible server (chat completions sync + streaming) returning canned responses keyed by scenario; start/stop scripts.
- **Verify:** stub starts; canned + streamed responses returned.
- **Commit:** `QA: local OpenAI-compatible stub server for deterministic E2E`
- **Context:** ADR-000 §10 note on substitution-logged-never-silent; ADR-003 §8 fixtures list (valid decision, ESCALATE, off-topic, updatedDecision, 5xx/timeout, chunked stream).

---

### GATE C — Phase 2 (parallel: BE foundations ‖ FE form ‖ QA fixtures)

**P2.B1 — In-memory session store** · _be_
- **Refs:** ADR-004 §3/§5/§8; TAC-004-01..05; AC-21.
- **Tests first:** create+find; TTL expiry (boundary at TTL); eviction sweep removes only expired; concurrent `appendMessages` lose none + ordered; `supersedeDecision` keeps history; no raw image bytes retained.
- **Deliver:** `application/port/SessionStore`, `support/InMemorySessionStore` (+scheduled sweep), `domain/CaseSession`, `ChatMessage`.
- **Verify:** `./mvnw -q -Dtest=*SessionStore* test`.
- **Commit:** `Backend: in-memory session store with TTL eviction`
- **Context:** ADR-004 §3/§4/§5 verbatim.

**P2.B2 — Image compressor** · _be_
- **Refs:** ADR-001 §6 (pipeline); AC-09; TAC-003 (image invariants), TAC-001-03.
- **Tests first:** JPEG/PNG/WebP input → long side ≤ `APP_IMAGE_MAX_DIMENSION_PX`, output bytes ≤ original, format = target; WebP re-encoded to JPEG.
- **Deliver:** `application/port/ImageCompressor`, `support/ThumbnailatorImageCompressor`.
- **Verify:** `./mvnw -q -Dtest=*Compressor* test`.
- **Commit:** `Backend: Thumbnailator image compressor with dimension/format invariants`
- **Context:** ADR-001 §6 "Image pipeline" decision verbatim; ADR-000 §7 `app.image.*`.

**P2.B3 — Prompt + Policy providers (authoring prompts)** · _be_
- **Refs:** ADR-003 §3 (providers), §6 (procedure injection), PRD §11 (guardrails); AC-10/11/16/26.
- **Tests first:** `PolicyProvider` returns complaint text for COMPLAINT, return text for RETURN (TAC-006); `PromptProvider` returns the 4 templates (system, analysis-by-caseType, decision-by-caseType, chat).
- **Deliver:** `application/port/PromptProvider`, `PolicyProvider`; `integration/PromptTemplateProvider`, `PolicyDocumentLoader`; **author** `src/main/resources/prompts/{system,analysis-complaint,analysis-return,decision-complaint,decision-return,chat}.md` in **Polish**, encoding PRD §11 guardrails (advisory-only, no binding commitment, ESCALATE when unsure, off-topic redirect, vision-describes-only).
- **Verify:** `./mvnw -q -Dtest=*Provider* test`.
- **Commit:** `Backend: prompt templates (PL) and policy/prompt providers`
- **Context:** PRD §11 verbatim; ADR-003 §3/§6; the two `docs/policies/*.md` files.

**P2.F1 — Intake form with full client validation** · _fe_
- **Refs:** ADR-002 §3 form; AC-01..08, AC-23/25; TAC-002-01/02/03/06/07.
- **Tests first:** reason required iff COMPLAINT (value kept on toggle); future date blocked, today allowed; file type/size guard (GIF rejected, exactly 10 MB accepted, 11 MB rejected); selectors populated from mocked `/api/metadata`; all labels Polish; submit disabled while invalid.
- **Deliver:** `features/form/intake-form.component.ts(.html/.scss)` using Material `mat-select`/`matInput`/`mat-datepicker`/textarea/file picker + thumbnail; reads metadata via `CaseService.getMetadata()` (mocked in unit). Apply NBP design tokens (granat `#152E52`, akcent `#4A74B0`, radius/spacing) per design-guidelines.
- **Verify:** `npm test` (form specs) + `npm run lint` + `npm run build`.
- **Commit:** `Frontend: intake form with metadata-driven options and client validation`
- **Context:** ADR-002 §3 form bullet + §8 table; PRD §9.1; AC-01..08; design-guidelines colors/typography/spacing.

---

### GATE D — Phase 3 (parallel: BE AI adapters ‖ FE chat skeleton)

**P3.B1 — Vision analysis adapter** · _be_
- **Refs:** ADR-003 §3/§5; AC-10/11/12; TAC-003-01/02/03.
- **Tests first (MockWebServer):** client built with `baseUrl=OPENROUTER_BASE_URL` + resolved key; uses `OPENROUTER_VISION_MODEL`; COMPLAINT→complaint-analysis prompt, RETURN→return-analysis; request carries text part + image part (base64); returns `ImageAnalysis` with **no** outcome; missing flags → unknown; timeout/5xx → typed exception after bounded retries.
- **Deliver:** `integration/OpenAiClientConfig`, `OpenRouterVisionAdapter`, `application/port/VisionAnalysisPort`, `domain/ImageAnalysis`.
- **Verify:** `./mvnw -q -Dtest=*Vision*,*ClientConfig* test`.
- **Commit:** `Backend: OpenRouter vision adapter (describe-only) via OpenAI Java SDK`
- **Context:** ADR-003 §3/§4/§5 + §6 "two-model" & "bounded retries"; ADR-000 §7 env vars.

**P3.B2 — Decision adapter (structured) + streaming** · _be_
- **Refs:** ADR-003 §3/§4/§5/§6; AC-13/14/15/16/17/26; TAC-003-04/05/06/07, TAC-04/05.
- **Tests first (MockWebServer):** valid structured JSON → `Decision` with enum outcome + citedRules; garbage/unknown/non-JSON outcome → **coerced to ESCALATE**; composed `firstMessageMarkdown` always includes the Polish advisory disclaimer; COMPLAINT injects complaint procedure / RETURN injects return (TAC-006); streaming pushes deltas in order + accumulates full reply; `updatedDecision` only when fixture signals material new info; off-topic → redirect flag, no decision change; timeout/5xx → typed exception.
- **Deliver:** `OpenRouterDecisionAdapter` (`decide` + `streamReply`), `application/port/DecisionPort`, `domain/Decision`, `ChatReply`, `TokenSink`; first-message composer.
- **Verify:** `./mvnw -q -Dtest=*Decision* test`.
- **Commit:** `Backend: OpenRouter decision adapter with structured output, ESCALATE fail-safe, streaming`
- **Context:** ADR-003 §3/§4/§5/§6 verbatim; PRD §11; AC-13..18/26.

**P3.F1 — Chat view skeleton + SSE client** · _fe_
- **Refs:** ADR-002 §3/§4/§6; AC-18/19/25; TAC-002-04/05/08.
- **Tests first:** `CaseService` REST methods via `HttpTestingController` (success + error branches); SSE consumption renders tokens incrementally and finalizes on `done`; sanitization on (raw HTML not executed); first bubble structure (greeting/decision/justification/next-steps/disclaimer) renders via ngx-markdown.
- **Deliver:** `core/case.service.ts` (HttpClient methods + `sendMessage` SSE via `@microsoft/fetch-event-source`), `core/http-error.interceptor.ts`, `core/app-state` signal store, `features/chat/chat.component.*` skeleton (bubbles, composer, expansion-panel summary, typing indicator) — wired against mocks.
- **Verify:** `npm test` (chat/service specs) + `npm run lint` + `npm run build`.
- **Commit:** `Frontend: chat view skeleton, CaseService REST+SSE client, error interceptor`
- **Context:** ADR-002 §3/§4/§5/§6 verbatim; AC-18/19/25; design tokens.

---

### GATE E — Phase 4 (parallel: BE orchestration+endpoints ‖ FE flow wiring)

**P4.B1 — CaseService + `POST /api/cases`** · _be_
- **Refs:** ADR-001 §3/§5; ADR-000 §6; AC-06/07/08/09/13/18; TAC-001-01/02.
- **Tests first (MockMvc + MockWebServer):** valid COMPLAINT/RETURN → `201` with decision + firstMessageMarkdown (stub hit vision then text); missing reason for COMPLAINT / future date / no image → `400` + fieldErrors with **zero** LLM calls; oversized → `413`, wrong type → `415` before any LLM call.
- **Deliver:** `application/CaseService` (validate→compress→analyze→decide→create session→compose), `web/CaseController` POST handler + multipart limits.
- **Verify:** `./mvnw -q -Dtest=CaseControllerTest,CaseServiceTest test`.
- **Commit:** `Backend: case creation orchestration and POST /api/cases`
- **Context:** ADR-001 §3 CaseService bullet + §5; ADR-000 §6 row 1.

**P4.B2 — ChatService + `POST /api/cases/{id}/messages` (SSE)** · _be_
- **Refs:** ADR-001 §3/§5; ADR-000 §6; AC-19/20/21/22/24; TAC-001-05.
- **Tests first (MockMvc):** content-type `text/event-stream`; ≥1 `token` then terminal `done`; history appended; `updatedDecision` when info changes; mid-stream stub error → `error` event then close, session not corrupted; unknown/expired id → `404` JSON **before** stream opens.
- **Deliver:** `application/ChatService.streamReply`, `web/ChatController` returning `SseEmitter` on a bounded async executor.
- **Verify:** `./mvnw -q -Dtest=ChatControllerTest,ChatServiceTest test`.
- **Commit:** `Backend: streaming chat orchestration and SSE messages endpoint`
- **Context:** ADR-001 §3 ChatService + §6 SSE decision; ADR-003 §5 streamReply contract.

**P4.B3 — `GET /api/cases/{id}` + GlobalExceptionHandler + CORS** · _be_
- **Refs:** ADR-001 §3/§6; ADR-000 §6/§8; AC-24; TAC-001-04/06, TAC-007, TAC-10, TAC-08.
- **Tests first:** GET returns full ordered session, unknown → `404`; handler maps every known failure to its `code`/status, **no 500/stack trace**; CORS allows configured origin, rejects others.
- **Deliver:** `CaseController` GET, `web/GlobalExceptionHandler`, CORS config from `APP_CORS_ALLOWED_ORIGIN`.
- **Verify:** `./mvnw -q -Dtest=*ExceptionHandler*,*Cors*,CaseControllerTest test`.
- **Commit:** `Backend: session rehydration endpoint, global error handler, CORS`
- **Context:** ADR-001 §6 error decision; ADR-000 §6 error shape + §8 CORS/no-auth.

**P4.F1 — Form → create → navigate to chat** · _fe_
- **Refs:** ADR-002 §3/§5; AC-07/24/25; TAC-002-04/06.
- **Tests first:** mocked `201` → router navigates `/chat/{id}` + store holds decision; mocked `400` fieldErrors → messages under correct controls, inputs preserved; mocked `502` → retryable message, form re-enabled; SUBMITTING disables form + spinner.
- **Deliver:** wire `IntakeFormComponent.submit()` → `CaseService.createCase` → navigate; loading/error states.
- **Verify:** `npm test` + `npm run lint` + `npm run build`.
- **Commit:** `Frontend: submit flow with navigation, loading and error states`
- **Context:** ADR-002 §3 form + §5 + sequence "Form submit to chat"; AC-24/25.

**P4.F2 — Streaming chat turn + states** · _fe_
- **Refs:** ADR-002 §3/§5; AC-19/20/21/22/24/25; TAC-002-05/06.
- **Tests first:** send → user bubble + empty assistant bubble + typing indicator; token deltas grow the bubble; `done` finalizes + renders `updatedDecision` inline (history preserved); `error`/`404` → inline error / "Sesja wygasła" + new-case; composer disabled while streaming.
- **Deliver:** `ChatComponent` send handler + SSE consumption + "start new case" reset.
- **Verify:** `npm test` + `npm run lint` + `npm run build`.
- **Commit:** `Frontend: streaming chat turn with live bubble, updated decision, error states`
- **Context:** ADR-002 §3 chat + sequence "Streaming chat turn"; AC-19..24.

---

### GATE F — Phase 5: Integration + suite finalization

**P5.B1 — Backend integration sweep (all controllers, only OpenRouter stubbed)** · _be (qa reviews)_
- **Refs:** ADR-001 §8 scenarios; ADR-000 §10 TAC-01..10; ADR-003 §8; ADR-004 §8.
- **Tests first:** the full ADR-001 §8 scenario table end-to-end through controllers→services with MockWebServer as the only mock: valid complaint/return, missing field, no/oversized/wrong image, LLM unavailable/timeout (vision-ok-text-fails), chat happy, chat mid-stream error, unknown/expired session, metadata, CORS. Assert TAC-01 (zero LLM calls on invalid), TAC-09 (baseUrl/key/model ids via stub request).
- **Deliver:** `src/test/java/.../web` integration tests; fix any gaps surfaced.
- **Verify:** `./mvnw -q verify`.
- **Commit:** `Backend: end-to-end integration tests across REST and SSE (OpenRouter stubbed)`
- **Context:** ADR-001 §8 table + ADR-000 §10 TAC list.

**P5.F1 — Frontend unit suite finalize + coverage of AC table** · _fe_
- **Refs:** ADR-002 §8 full table; TAC-002-01..07.
- **Tests first:** close any gaps in the ADR-002 §8 table not yet covered.
- **Deliver:** completed FE spec suite.
- **Verify:** `npm test` + `npm run lint` + `npm run build`.
- **Commit:** `Frontend: finalize unit suite covering ADR-002 acceptance table`
- **Context:** ADR-002 §8 table.

---

### GATE G — Phase 6: E2E + audit + manual smoke

**P6.1 — Playwright E2E against the running stack (local stub)** · _qa_
- **Refs:** ADR-000 §10 TAC-11; ADR-002 §8 "Full flow"; playwright-best-practices skill.
- **Tests first:** form → decision → one streamed chat turn; assert first-bubble structure (greeting/decision/justification/next-steps/disclaimer) and incremental token rendering. Backend + frontend started; `OPENROUTER_BASE_URL` → local stub (P1.5). Substitution logged, never silent.
- **Deliver:** `app/e2e/` Playwright project + spec + run script (start stub → BE → FE → run).
- **Verify:** `npx playwright test` green against the live stack.
- **Commit:** `QA: Playwright E2E (form to decision to streamed chat) against running stack`
- **Context:** ADR-000 §10 TAC-11; ADR-002 full-flow row; P1.5 stub interface.

**P6.2 — Localization + AC compliance audit** · _qa_
- **Refs:** AC-23 + the full PRD §6 AC list; PRD §11 disclaimer.
- **Do:** verify every user-facing string (form, errors, agent output, disclaimer) is Polish; walk the AC checklist; file any misses as fix steps routed to be/fe.
- **Verify:** documented checklist all green (or fixes scheduled).
- **Commit:** `QA: localization and acceptance-criteria audit report`
- **Context:** PRD §6 + §11.

**P6.3 — Manual real-model smoke (DEFERRED — user runs)** · _user_
- Populate root `.env` with a real `OPENROUTER_API_KEY`, point `OPENROUTER_BASE_URL` at real OpenRouter, run one complaint and one return end-to-end against real models. **Not** part of automated CI/verification. Out of scope for the agents per the agreed default.

---

## 5. Risks & notes

- **Untracked baseline (highest priority):** P0.1 must commit the scaffold before any isolated/worktree work, or `app/` is missing in the worktree (verified empty here).
- **Contract drift FE↔BE:** mitigated by Gate B single-field-list source; any contract change is an explicit dual-side step, not an ad-hoc edit.
- **WebP decode (Thumbnailator):** if absent, accept WebP upload then re-encode to JPEG (ADR-001 §6) — covered by P2.B2 tests.
- **Structured output support on the chosen model:** ESCALATE fail-safe (P3.B2) is the hard backstop; model id is env-configured.
- **SSE over POST:** native `EventSource` is GET-only — FE uses `@microsoft/fetch-event-source` (already installed).
- **Real-model behavior** is only ever checked in the manual smoke (P6.3); all automated tests use the stub.

---

## 6. Execution checklist (orchestrator)

1. Get plan approval (this document).
2. P0.1 baseline commit + green → **Gate A**.
3. P1.1→P1.5 → **Gate B** (contract frozen).
4. Launch parallel lanes per gate (C→D→E), one agent per lane, file ownership disjoint, commit per step.
5. P5 integration → **Gate F**.
6. P6.1/P6.2 → **Gate G**; hand P6.3 to the user.
7. Each delegated task gets only its step's scoped context block above — never the whole PRD/ADR set.
