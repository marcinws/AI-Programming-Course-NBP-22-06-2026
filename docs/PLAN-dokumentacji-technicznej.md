# PLAN: Dokumentacja Techniczna — Hardware Service Decision Copilot

> **Przeznaczenie planu:** Szczegółowy spis treści i opis zawartości każdego rozdziału dokumentu technicznego, który zostanie wygenerowany na podstawie tego planu.  
> **Docelowy dokument:** `docs/dokumentacja-techniczna.md`  
> **Język:** Polski  
> **Odbiorcy:** Programiści utrzymujący i rozwijający aplikację  
> **Szacowana długość docelowego dokumentu:** ~15 000–20 000 słów + diagramy

---

## Metadane docelowego dokumentu

| Pole | Wartość |
|------|---------|
| Tytuł | Dokumentacja Techniczna — Hardware Service Decision Copilot |
| Wersja | 1.0 |
| Data | 2026-06-30 |
| Autorzy | Zespół kursowy NBP / JSystems |
| Status | Dokument żywy (aktualizowany wraz z rozwojem) |

---

## Spis treści docelowego dokumentu

### 1. Wprowadzenie i kontekst biznesowy
**Opis:** Skrótowy opis co to za aplikacja, dla kogo, jakie problemy rozwiązuje, jaki jest jej cel biznesowy — podano konkretny kontekst: obsługa reklamacji i zwrotów sprzętu elektronicznego w NBP.

**Zawartość:**
- Cel aplikacji (wspomaganie decyzji pracowników działu obsługi klienta)
- Domena (sprzęt elektroniczny, 13 kategorii)
- Typy spraw (COMPLAINT = reklamacja, RETURN = zwrot)
- Wyniki decyzji (APPROVE / REJECT / ESCALATE)
- Kontekst kursu AI dla programistów — rola MVPa
- Diagram: `[Diagram: Kontekst systemu — C4 Level 1]`

---

### 2. Architektura systemu — widok ogólny

**Opis:** Panoramiczny widok całego systemu: frontend SPA, backend REST API, modele LLM przez OpenRouter, sesje w pamięci, przepływ danych end-to-end.

**Zawartość:**
- Diagram: `[Diagram: Architektura C4 — Level 2 — kontenery]`
  - Angular SPA → Spring Boot REST API → OpenRouter (text + vision models)
  - Session Store (in-memory) jako tymczasowe rozwiązanie MVP
- Diagram: `[Diagram: Sekwencja tworzenia sprawy — od formularza do decyzji AI]`
- Diagram: `[Diagram: Sekwencja czatu — streaming SSE token po tokenie]`
- Monorepo layout: `app/backend`, `app/frontend`, `app/e2e`
- Protokoły komunikacji: HTTP REST + SSE (Server-Sent Events)
- Języki i środowisko wykonawcze: Java 21, Node 18+, TypeScript 5.9
- Zasada: brak RAG (polityki procedurowe osadzone bezpośrednio w promptach)

---

### 3. Stos technologiczny

**Opis:** Pełna tabela używanych technologii z uzasadnieniem wyboru każdej z nich (odniesienie do ADR-000, ADR-003, ADR-005).

**Zawartość:**

#### 3.1 Backend

| Technologia | Wersja | Rola |
|-------------|--------|------|
| Java | 21 LTS | Język backendu |
| Spring Boot | 3.5.1 | Framework webowy i DI |
| Spring Web MVC | — | REST controllers, SseEmitter |
| Spring Validation | — | Bean validation (JSR-380) |
| OpenAI Java SDK | 4.41.0 | Klient LLM (OpenRouter) |
| Thumbnailator | 0.4.20 | Kompresja obrazów |
| TwelveMonkeys WebP | 3.13.1 | Obsługa formatu WebP |
| spring-dotenv | 4.0.0 | Ładowanie .env do env vars |
| Maven | 3.x | Build tool |

**Uzasadnienie wyborów (z ADR):** dlaczego Java 21 (wirtualne wątki), dlaczego Spring Boot (dojrzałość, ecosystem), dlaczego OpenAI SDK zamiast REST ręcznego (streaming support).

#### 3.2 Frontend

| Technologia | Wersja | Rola |
|-------------|--------|------|
| Angular | 20.3.x | SPA framework |
| Angular Material | 20.2.x | Komponenty UI |
| Angular Signals | — | Zarządzanie stanem |
| RxJS | 7.8.x | Reaktywność HTTP |
| @microsoft/fetch-event-source | 2.0.1 | POST-based SSE client |
| marked | 16.4.2 | Parser Markdown |
| ngx-markdown | 20.1.0 | Bezpieczne renderowanie Markdown |
| TypeScript | 5.9.2 | Typowany JS |
| Karma + Jasmine | — | Testy jednostkowe |

**Uzasadnienie wyborów (z ADR-002):** standalone components zamiast NgModules, Signals zamiast NgRx (rozmiar aplikacji), fetch-event-source zamiast EventSource (POST SSE).

#### 3.3 Testy E2E

| Technologia | Wersja | Rola |
|-------------|--------|------|
| Playwright | — | E2E automation |
| Node.js stub | — | Mock serwer OpenAI-compatible |

---

### 4. Kluczowe biblioteki — omówienie szczegółowe

**Opis:** Dla każdej niestandardowej biblioteki: co robi, jak jest używana w kodzie, jak skonfigurowana, przykłady użycia.

**Zawartość:**

#### 4.1 OpenAI Java SDK v4.41.0
- Jak jest skonfigurowany do pracy z OpenRouter (base URL override, custom headers)
- `OpenAIClient` — synchroniczne i strumieniowe wywołania
- Przykład kodu: konfiguracja klienta w `OpenAiClientConfig.java`
- Przykład kodu: strumieniowanie w `OpenRouterDecisionAdapter.java`
- Różnica między `decide()` (synchroniczny) a `streamReply()` (streaming)

#### 4.2 @microsoft/fetch-event-source
- Dlaczego natywny `EventSource` nie działa (tylko GET)
- Jak wrapper umożliwia POST SSE
- Pattern użycia w `CaseService.sendMessage()`
- Test seam: `_fetchEventSourceImpl`

#### 4.3 Thumbnailator + TwelveMonkeys
- Cel: kompresja obrazów przed wysłaniem do vision LLM
- `ThumbnailatorImageCompressor` — resize do max 2048px, konwersja do JPEG
- TwelveMonkeys: rozszerzenie ImageIO o WebP (Spring Boot nie ma wbudowanego wsparcia)

#### 4.4 ngx-markdown / marked
- Renderowanie odpowiedzi AI (Markdown → bezpieczny HTML)
- Ochrona XSS — Angular auto-escape + sanitized rendering

#### 4.5 spring-dotenv
- Ładowanie `.env` z katalogu głównego projektu do zmiennych środowiskowych JVM
- Priorytet: zmienne systemowe > .env

---

### 5. Jak działa aplikacja — przepływ użytkownika

**Opis:** Krok po kroku co dzieje się w systemie od momentu wejścia na stronę do zakończenia sesji.

**Zawartość:**

#### 5.1 Inicjalizacja — formularz przyjęcia sprawy
- Diagram: `[Diagram: Formularz — przepływ danych i walidacja]`
- Krok 1: `GET /api/metadata` → opcje formularza (typy spraw, kategorie)
- Krok 2: Użytkownik wypełnia formularz (ReactiveForm)
- Walidacja client-side: `noFutureDateValidator`, required pola, warunek reason dla COMPLAINT
- Krok 3: `POST /api/cases` (multipart/form-data — pola + plik obrazu)

#### 5.2 Przetwarzanie sprawy na backendzie
- Diagram: `[Diagram: Sekwencja backend — CaseService pipeline]`
- Krok 4: Walidacja server-side (Bean Validation + cross-field)
- Krok 5: Kompresja obrazu (`ThumbnailatorImageCompressor`)
- Krok 6: Analiza obrazu (`OpenRouterVisionAdapter` → vision model)
- Krok 7: Generowanie decyzji (`OpenRouterDecisionAdapter` → text model + JSON)
- Krok 8: Zapis sesji (`InMemorySessionStore`)
- Krok 9: Odpowiedź 201 z `sessionId`, `decision`, `imageAnalysisSummary`

#### 5.3 Chat — kontynuacja rozmowy
- Diagram: `[Diagram: Streaming SSE — backend do frontend]`
- Krok 10: Nawigacja do `/chat/{sessionId}`
- Krok 11: Rehydracja sesji (lub `GET /api/cases/{id}`)
- Krok 12: Użytkownik wpisuje pytanie → `POST /api/cases/{id}/messages`
- Krok 13: Backend otwiera `SseEmitter`, emituje tokeny (`token` events)
- Krok 14: Przy zakończeniu: `done` event z pełną wiadomością + opcjonalna zmiana decyzji
- Krok 15: Detekcja markerów `[OFFTOPIC]` i `[UPDATED_DECISION:X]`

#### 5.4 Wygaśnięcie sesji
- TTL: 60 minut od utworzenia
- `@Scheduled` cleanup co minutę
- 404 → frontend pokazuje "sesja wygasła", link do nowej sprawy

---

### 6. Separacja warstw — architektura hexagonalna

**Opis:** Szczegółowy opis struktury warstwowej backendu i frontendu z zasadami zależności.

**Zawartość:**

#### 6.1 Backend — porty i adaptery

- Diagram: `[Diagram: Hexagonal Architecture — backend]`

```
┌─────────────────────────────────────────┐
│  Web Layer (REST + SSE)                 │
│  CaseController, ChatController,        │
│  MetadataController, GlobalExceptionH.  │
└──────────┬──────────────────────────────┘
           │ DTO (CaseFormView, ChatRequest…)
┌──────────▼──────────────────────────────┐
│  Application Layer (Use Cases)          │
│  CaseService, ChatService,              │
│  MetadataService                        │
│  ← driven by ports:                     │
│     DecisionPort, VisionAnalysisPort,   │
│     ImageCompressor, SessionStore,      │
│     PromptProvider, PolicyProvider,     │
│     TokenSink                           │
└──────────┬──────────────────────────────┘
           │
┌──────────▼──────────────────────────────┐
│  Domain Layer (Immutable Models)        │
│  CaseSession, CaseForm, CaseType,       │
│  Decision, ChatMessage, ImageAnalysis…  │
└─────────────────────────────────────────┘
           │ implements ports
┌──────────▼──────────────────────────────┐
│  Integration Adapters                   │
│  OpenRouterDecisionAdapter,             │
│  OpenRouterVisionAdapter,               │
│  PromptTemplateProvider,                │
│  PolicyDocumentLoader                   │
└─────────────────────────────────────────┘
           │
┌──────────▼──────────────────────────────┐
│  Support / Infrastructure               │
│  InMemorySessionStore,                  │
│  ThumbnailatorImageCompressor,          │
│  Configuration Properties               │
└─────────────────────────────────────────┘
```

- Zasada: Application Layer NIE zna klas z Integration/Support — zna tylko porty
- Diagram: `[Diagram: Graf zależności klas — bez cykli]`

#### 6.2 Frontend — architektura Angular Signals

- Diagram: `[Diagram: Frontend — komponenty, serwisy, state flow]`

```
Routes → IntakeFormComponent ──┐
      → ChatComponent          │
                               ▼
                        AppStateService (signals)
                               │
                        CaseService (HTTP + SSE)
                               │
                        HttpErrorInterceptor
```

- Wzorzec: "Smart Component + Dumb Services" (serwisy bez UI-state)
- Signals jako alternatywa dla NgRx: kiedy to wystarczy?

---

### 7. Kluczowe klasy — Backend

**Opis:** Szczegółowy opis każdej kluczowej klasy z sygnaturami metod, odpowiedzialnościami i przykładami kodu.

**Zawartość (osobna sekcja dla każdej klasy):**

#### 7.1 Warstwa Web
- **CaseController** — endpointy, walidacja multipart, mapowanie do DTO
- **ChatController** — SSE emitter, async executor, zarządzanie błędami mid-stream
- **MetadataController** — prosty endpoint metadanych
- **GlobalExceptionHandler** — mapa wyjątków → kody HTTP, format ErrorResponse

#### 7.2 Warstwa Application (Use Cases)
- **CaseService** — `createCase()`: pipeline kompresja → wizja → decyzja → zapis
- **ChatService** — `streamReply()`: load session → append user → stream → detect markers → append assistant → optional decision supersede

#### 7.3 Porty (Abstrakcje)
- **DecisionPort** — `decide()` (sync) + `streamReply()` (streaming)
- **VisionAnalysisPort** — `analyze()`
- **ImageCompressor** — `compress()`
- **SessionStore** — CRUD + TTL
- **PromptProvider** — getter-y dla każdego szablonu
- **PolicyProvider** — `getPolicy(CaseType)`
- **TokenSink** — callback interface dla delta tokenów

#### 7.4 Warstwa Domeny
- **CaseSession** — immutable, pola, TTL tracking, `isExpired()`
- **CaseForm** — value object formularza
- **Decision** — outcome + justification + citedRules + confidence + firstMessageMarkdown
- **ChatMessage** — role/content/createdAt, enum Role (USER/ASSISTANT/SYSTEM_ASSISTANT)
- **ImageAnalysis** — opis + opcjonalne flagi diagnostyczne
- Enumy: CaseType, EquipmentCategory (13 wartości), DecisionOutcome, DecisionConfidence

#### 7.5 Adaptery Integracyjne
- **OpenRouterDecisionAdapter** — synchroniczne `decide()` + streaming `streamReply()`, detekcja markerów, fallback ESCALATE, `composeFirstMessage()` z Markdown + disclaimer
- **OpenRouterVisionAdapter** — base64 encode obrazu, wywołanie vision model, parsowanie JSON z fallback do raw text
- **PromptTemplateProvider** — ładowanie szablonów z classpath, cache w ConcurrentHashMap
- **PolicyDocumentLoader** — ładowanie procedur z classpath
- **OpenAiClientConfig** — budowanie OpenAIClient z custom base URL i headerami

#### 7.6 Infrastruktura
- **InMemorySessionStore** — ConcurrentHashMap, `@Scheduled` TTL cleanup
- **ThumbnailatorImageCompressor** — resize + konwersja JPEG

**Format dla każdej klasy:**
```
Pełna nazwa: pl.nbp.copilot.xxx.ClassName
Odpowiedzialność: [jedno zdanie]
Lokalizacja: app/backend/src/main/java/pl/nbp/copilot/xxx/ClassName.java
Kluczowe metody:
  - methodName(params): returnType — opis
Zależności (przez DI): [lista portów/serwisów]
Przykład kodu: [snippet z kluczowym fragmentem]
```

---

### 8. Kluczowe klasy — Frontend

**Opis:** Szczegółowy opis każdej kluczowej klasy/komponentu Angular.

**Zawartość:**

#### 8.1 AppStateService (`core/app-state.ts`)
- Signals: `_sessionId`, `_decision`, `_messages`, `_pendingState`
- Enum PendingState: IDLE / SUBMITTING / STREAMING / ERROR
- Computed signals: publiczne accessory
- Metody: `hydrateFromCreate()`, `hydrateFromSession()`, `reset()`, `appendMessage()`, `updateLastMessage()`, `setDecision()`
- Dlaczego signals zamiast Subject/BehaviorSubject

#### 8.2 CaseService (`core/case.service.ts`)
- `getMetadata()` → `Observable<MetadataResponse>`
- `createCase(formData: FormData)` → `Observable<CreateCaseResponse>`
- `getCase(id: string)` → `Observable<SessionResponse>`
- `sendMessage(id: string, content: string, handlers)` → void (SSE)
- Test seam: `_fetchEventSourceImpl` (dependency injection dla testów jednostkowych)
- Diagram: `[Diagram: CaseService — metody i ich wywołania HTTP/SSE]`

#### 8.3 IntakeFormComponent (`features/form/`)
- ReactiveForm: `caseType`, `equipmentCategory`, `modelName`, `purchaseDate`, `reason`, `image`
- Walidator `noFutureDateValidator()` — jak działa custom Angular validator
- Signals komponentu: `selectedFile`, `fileError`, `previewUrl`, `isSubmitting`, `submitError`
- `onSubmit()`: buildFormData → createCase → hydrateState → navigate
- Mapowanie błędów backend → form controls

#### 8.4 ChatComponent (`features/chat/`)
- `effectiveMessages` — computed signal (testMessages || stateMessages)
- `isStreaming` — computed z pendingState
- Lifecycle: `ngOnInit` → rehydrate if empty → subscribe SSE
- `onSend()`: append USER bubble → append empty ASSISTANT bubble → SSE stream
- Obsługa wygasłej sesji: 404/SESSION_NOT_FOUND → session-expired UI
- Renderowanie Markdown przez `ngx-markdown`
- Test seam: `testMessages` signal override dla unit testów

#### 8.5 Modele TypeScript (`core/models.ts`)
- Enumeracje: `CaseType`, `EquipmentCategory`, `DecisionOutcome`, `DecisionConfidence`
- Interfejsy: `Decision`, `ChatMessage`, `DisplayMessage`, `SseEvent` (union type), `ErrorResponse`, `FieldError`, `ImageConstraints`
- Diagram: `[Diagram: Model typów TypeScript — relacje]`

#### 8.6 HttpErrorInterceptor (`core/http-error.interceptor.ts`)
- Parsowanie `ErrorResponse` z body HTTP
- Mapowanie `fieldErrors[]` na błędy form controls (Angular `setErrors()`)
- Propagacja jako `HttpErrorResponse` z ustrukturyzowanymi danymi

---

### 9. Kluczowe klasy — Testy

**Opis:** Jak zorganizowane są testy, co testują, jak uruchamiać.

**Zawartość:**

#### 9.1 Testy backend (JUnit 5 + Mockito + Spring Boot Test)

| Plik | Typ | Co testuje |
|------|-----|-----------|
| CaseServiceTest | Unit | Pipeline CreateCase, mocki portów |
| ChatServiceTest | Unit | Streaming, detekcja markerów |
| OpenRouterDecisionAdapterTest | Integration | Parsowanie JSON, fallback ESCALATE |
| OpenRouterVisionAdapterTest | Integration | Parsowanie obrazu, fallback raw text |
| InMemorySessionStoreTest | Integration | CRUD sesji, wygasanie TTL |
| CaseControllerTest | Integration | Kontrakt HTTP, walidacja multipart |
| ChatControllerTest | Integration | SSE emitter, sekwencja eventów |
| BackendIntegrationTest | End-to-end | Pełny przepływ z mock LLM |
| EnumCompletenessTest | Contract | Zgodność enumów FE-BE |

- Przykładowe testy z komentarzem
- Jak mockować OpenAI client (`MockWebServer`)
- Pattern testowania SSE (capture emitted events)

#### 9.2 Testy frontend (Karma + Jasmine)

| Plik | Co testuje |
|------|-----------|
| app-state.spec.ts | Signal store, przejścia stanów |
| case.service.spec.ts | HTTP calls, mock SSE seam |
| intake-form.component.spec.ts | Walidacja, submit, mapowanie błędów |
| chat.component.spec.ts | Bubbles, SSE events, session expiry |
| http-error.interceptor.spec.ts | Parsowanie błędów, field errors |
| models.spec.ts | Kompletność typów |

- Jak mockować SSE przez `_fetchEventSourceImpl`
- Test seam `testMessages` w ChatComponent
- Testy XSS przez ngx-markdown

#### 9.3 Testy E2E (Playwright)

- Konfiguracja `playwright.config.ts` — 3 serwery (stub + backend + frontend)
- Page Object Model: `IntakeFormPage`, `ChatPage`
- Stub serwer: jak działa, jakie zwraca odpowiedzi
- Smoke test: formularz → decyzja → chat → odpowiedź asystenta
- Diagram: `[Diagram: Orchestracja serwerów w testach E2E]`
- Jak uruchomić: headed, CI (headless), debug mode

---

### 10. Integracja z modelami AI

**Opis:** Dokładnie jak aplikacja używa modeli AI — każdy call, każdy prompt, każda odpowiedź.

**Zawartość:**

#### 10.1 Przegląd integracji

- Diagram: `[Diagram: Wywołania LLM — typy, sekwencja, modele]`
- Provider: OpenRouter (OpenAI-compatible API)
- SDK: OpenAI Java SDK v4.41.0 z override base URL
- Dwa modele: `OPENROUTER_VISION_MODEL` (analiza obrazu) + `OPENROUTER_TEXT_MODEL` (decyzja + chat)
- Zero RAG: polityki w promptach bezpośrednio
- Zero tool use (function calling): markery tekstowe zamiast

#### 10.2 Wywołanie 1: Analiza obrazu (Vision)

```
Dane wejściowe: bytes obrazu (JPEG po kompresji) + form (typ sprawy, kategoria)
Prompt:         analysis-complaint.md lub analysis-return.md
Model:          OPENROUTER_VISION_MODEL
Odpowiedź:      JSON → ImageAnalysis { description, damageObserved, signsOfUse, usableForResale, confidence }
Fallback:       raw text jeśli JSON nieparseable
```

- Snippet kodu z `OpenRouterVisionAdapter.java`
- Jak obraz jest osadzany (base64 data URL)
- Jak parsowany JSON z fallback

#### 10.3 Wywołanie 2: Generowanie decyzji (Structured JSON)

```
Dane wejściowe: form + imageAnalysis + policy text (complaint-procedure.md lub return-procedure.md)
Prompt:         decision-complaint.md lub decision-return.md + system.md
Model:          OPENROUTER_TEXT_MODEL
Odpowiedź:      JSON → Decision { outcome, justification, citedRules, nextSteps, confidence }
Fallback:       ESCALATE + LOW confidence jeśli parse error
```

- Dlaczego JSON mode (Chat Completions, nie Responses API)
- Jak działa `composeFirstMessage()` — Markdown z decyzją + disclaimer (AC-26)
- Snippet kodu z `OpenRouterDecisionAdapter.decide()`

#### 10.4 Wywołanie 3: Chat streaming

```
Dane wejściowe: cała historia konwersacji (form + image desc + messages[]) + nowa wiadomość
Prompt:         system.md + chat.md
Model:          OPENROUTER_TEXT_MODEL
Odpowiedź:      Strumieniowane tokeny + opcjonalne markery
Markery:        [OFFTOPIC] → redirect, [UPDATED_DECISION:OUTCOME] → zmiana decyzji
```

- Diagram: `[Diagram: Streaming SSE — token delta flow]`
- Jak działa `TokenSink` callback
- Detekcja markerów w strumieniu
- Jak zaktualizowana decyzja jest zapisywana i odsyłana w `done` event

#### 10.5 Szablony promptów

| Plik | Kiedy | Format odpowiedzi |
|------|-------|------------------|
| `system.md` | Zawsze (system message) | N/A |
| `analysis-complaint.md` | Przy analizie reklamacji | JSON / raw text |
| `analysis-return.md` | Przy analizie zwrotu | JSON / raw text |
| `decision-complaint.md` | Przy decyzji reklamacji | JSON (wymagany) |
| `decision-return.md` | Przy decyzji zwrotu | JSON (wymagany) |
| `chat.md` | W każdej turze czatu | Tekst + opcjonalne markery |

- Przykład każdego szablonu (skrót)
- Jak szablony są ładowane (`PromptTemplateProvider` + cache)
- Jak polityki procedurowe są wstrzykiwane w prompt

#### 10.6 Obsługa błędów LLM

- `LlmTimeoutException` → HTTP 504 (dla pre-stream) lub SSE `error` event (mid-stream)
- `LlmUnavailableException` → HTTP 502/503
- Fallback decyzji: ESCALATE zawsze gdy JSON nieparseable

---

### 11. API — dokumentacja endpointów

**Opis:** Pełna dokumentacja REST API z przykładami request/response.

**Zawartość:**

#### 11.1 POST /api/cases — Tworzenie sprawy

```
Content-Type: multipart/form-data

Pola:
  caseType: COMPLAINT | RETURN (wymagane)
  equipmentCategory: SMARTPHONE | ... (wymagane)
  modelName: string (wymagane)
  purchaseDate: ISO date, nie przyszła (wymagane)
  reason: string (wymagane gdy COMPLAINT)
  image: plik JPEG/PNG/WebP max 10MB (wymagane)

Odpowiedź 201:
{
  "sessionId": "uuid",
  "decision": {
    "outcome": "APPROVE|REJECT|ESCALATE",
    "justification": "...",
    "citedRules": ["..."],
    "nextSteps": "...",
    "confidence": "LOW|MEDIUM|HIGH",
    "firstMessageMarkdown": "..."
  },
  "imageAnalysisSummary": "..."
}
```

#### 11.2 GET /api/cases/{id} — Rehydracja sesji

```
Odpowiedź 200:
{
  "sessionId": "uuid",
  "form": { ... },
  "imageAnalysisSummary": "...",
  "decision": { ... },
  "messages": [
    { "role": "USER|ASSISTANT", "content": "...", "createdAt": "ISO datetime" }
  ]
}

Odpowiedź 404: { "code": "SESSION_NOT_FOUND", "message": "..." }
```

#### 11.3 POST /api/cases/{id}/messages — Wiadomość chat (SSE)

```
Content-Type: application/json
{ "content": "..." }

Response: text/event-stream

event: token    → { "type": "token", "delta": "..." }
event: done     → { "type": "done", "message": {...}, "updatedDecision": {...}? }
event: error    → { "type": "error", "code": "...", "message": "..." }
```

#### 11.4 GET /api/metadata — Metadane formularza

```
Odpowiedź 200:
{
  "caseTypes": [{ "value": "COMPLAINT", "label": "Reklamacja" }, ...],
  "equipmentCategories": [{ "value": "SMARTPHONE", "label": "Smartfon" }, ...],
  "imageConstraints": {
    "maxUploadBytes": 10485760,
    "maxDimensionPx": 2048,
    "acceptedTypes": ["image/jpeg", "image/png", "image/webp"]
  }
}
```

#### 11.5 Tabela kodów błędów

| Status | Kod | Scenariusz |
|--------|-----|-----------|
| 400 | VALIDATION_ERROR | Brakujące/niepoprawne pole |
| 404 | SESSION_NOT_FOUND | Sesja wygasła lub nie istnieje |
| 413 | IMAGE_TOO_LARGE | Plik > 10MB |
| 415 | UNSUPPORTED_MEDIA_TYPE | Niedozwolony typ MIME |
| 502/503 | LLM_UNAVAILABLE | Błąd serwisu LLM |
| 504 | LLM_TIMEOUT | Timeout LLM (> 60s) |

- Diagram: `[Diagram: Drzewo decyzyjne obsługi błędów]`

---

### 12. Konfiguracja aplikacji

**Opis:** Wszystkie zmienne środowiskowe, pliki konfiguracyjne, jak skonfigurować dla różnych środowisk.

**Zawartość:**

#### 12.1 Plik .env (wymagany)

```bash
# LLM Provider (wymagane)
OPENROUTER_API_KEY=sk-or-v1-...
OPENROUTER_BASE_URL=https://openrouter.ai/api/v1
OPENROUTER_TEXT_MODEL=openai/gpt-4o-mini
OPENROUTER_VISION_MODEL=openai/gpt-4o

# Opcjonalne (wartości domyślne w nawiasach)
APP_SESSION_TTL_MINUTES=60
APP_IMAGE_MAX_UPLOAD_BYTES=10485760
APP_IMAGE_MAX_DIMENSION_PX=2048
APP_CORS_ALLOWED_ORIGIN=http://localhost:4200
OPENAI_REQUEST_TIMEOUT_MS=60000
```

#### 12.2 application.yaml (backend)

- Pełna struktura YAML z opisem każdego klucza
- Jak `@ConfigurationProperties` binduje do rekordów Java (`OpenRouterProperties`, `ImageProperties`, `SessionProperties`, itp.)
- Jak spring-dotenv ładuje .env

#### 12.3 proxy.conf.json (frontend dev)

- Jak proxy `/api` → `http://localhost:8080` działa w Angular dev server
- Dlaczego konieczne (CORS w dev, brak w prod)

#### 12.4 playwright.config.ts (E2E)

- webServer orchestration (3 serwery)
- Jak zmienić stub na prawdziwy LLM (env var `OPENROUTER_BASE_URL`)

#### 12.5 Konfiguracja CORS

- `CorsProperties.java` — `allowedOrigin`
- `WebConfig.java` — gdzie konfigurowane w Spring MVC
- Prod vs dev: co zmienić

---

### 13. Jak uruchomić aplikację

**Opis:** Instrukcje krok po kroku dla różnych scenariuszy (dev, testy, E2E).

**Zawartość:**

#### 13.1 Wymagania wstępne
- Java 21 (`java -version`)
- Maven 3.9+ (`mvn -version`)
- Node 18+ (`node -v`), npm 9+
- Klucz API OpenRouter (lub własny model OpenAI-compatible)

#### 13.2 Uruchomienie — tryb developerski

```bash
# 1. Sklonuj i wejdź do katalogu
cd app/backend && cp ../../.env.example ../../.env
# Uzupełnij .env (OPENROUTER_API_KEY etc.)

# 2. Backend (terminal 1)
cd app/backend
mvn spring-boot:run
# Nasłuchuje na http://localhost:8080

# 3. Frontend (terminal 2)
cd app/frontend
npm install
npm start
# Nasłuchuje na http://localhost:4200, proxy /api → :8080

# 4. Otwórz przeglądarkę: http://localhost:4200
```

#### 13.3 Build produkcyjny

```bash
# Backend — fat JAR
cd app/backend && mvn clean package -DskipTests
java -jar target/hardware-service-copilot-*.jar

# Frontend — statyczne pliki
cd app/frontend && npm run build
# dist/ → serwuj z nginx/CDK/Vercel
```

#### 13.4 Zmiana modeli LLM

- Jak wybrać inny model na OpenRouter
- Jak podłączyć własny endpoint OpenAI-compatible (Ollama, LM Studio, Azure OpenAI)

---

### 14. Jak uruchamiać testy

**Opis:** Komendy, konfiguracja, interpretacja wyników.

**Zawartość:**

#### 14.1 Testy jednostkowe i integracyjne backendu

```bash
cd app/backend
mvn test                     # wszystkie testy
mvn test -Dtest=CaseServiceTest  # wybrany test
mvn verify                   # + integracyjne
```

- Co zwraca każdy test, co mockuje
- Jak czytać raport Maven Surefire

#### 14.2 Testy jednostkowe frontendu

```bash
cd app/frontend
npm test                     # watch mode (Karma)
npm test -- --watch=false    # jednorazowe (CI)
```

#### 14.3 Testy E2E (Playwright)

```bash
cd app/e2e
npm install

npm run e2e              # headless (CI)
npm run e2e:headed       # z przeglądarką (wizualne)
npm run e2e:debug        # krok po kroku
npm run e2e:report       # otwórz raport HTML
```

#### 14.4 Jak działają testy E2E

- Diagram: `[Diagram: Orchestracja E2E — 3 serwery + Playwright]`
- Jak stub serwer symuluje OpenRouter
- Jak stub zwraca deterministyczne odpowiedzi
- Page Object Model — `IntakeFormPage`, `ChatPage`
- Typowy test smoke: assertions krok po kroku
- Jak dodać nowy test E2E
- Dlaczego single worker: stabilność stack developerskiego

---

### 15. Wzorce architektoniczne i projektowe

**Opis:** Jakie wzorce są użyte, gdzie, dlaczego.

**Zawartość:**

#### 15.1 Hexagonal Architecture (Ports & Adapters)

- Diagram: `[Diagram: Ports & Adapters — cały backend]`
- Aplikacja nie zna detali LLM ani Spring — tylko porty
- Jak to ułatwia testowanie (mock adaptery)
- Jak dodać nowy adapter (np. inny LLM provider)

#### 15.2 Immutable Domain Models

- `CaseSession`, `Decision`, `CaseForm`, `ChatMessage` — Java records (immutable)
- Dlaczego niezmienność jest ważna (thread safety, predictability)

#### 15.3 Repository Pattern (SessionStore)

- Port `SessionStore` + adapter `InMemorySessionStore`
- Jak wymienić na bazę danych bez zmiany application layer

#### 15.4 Template Method / Provider Pattern (PromptProvider)

- Szablony jako zasoby classpath, cache w runtime
- Jak dynamicznie ładować nowe szablony

#### 15.5 Strategy Pattern (PolicyProvider)

- `getPolicy(CaseType)` — strategy based on case type
- COMPLAINT → complaint-procedure.md, RETURN → return-procedure.md

#### 15.6 Observer / Reactive Streams (TokenSink + SSE)

- `TokenSink` jako callback interface
- Jak SseEmitter emituje eventy asynchronicznie
- Frontend: jak `fetch-event-source` subskrybuje eventy

#### 15.7 Signal-based State Management (Frontend)

- Jak Signals różnią się od Observable/Subject
- `computed()` signals jako derived state
- Diagram: `[Diagram: Signal graph — AppStateService]`

#### 15.8 Page Object Model (E2E Tests)

- `IntakeFormPage` i `ChatPage` — enkapsulacja selektorów
- Dlaczego POM ułatwia utrzymanie testów

---

### 16. RAG — czy aplikacja go używa?

**Opis:** Wyjaśnienie podejścia do wiedzy domenowej i ocena zasadności RAG.

**Zawartość:**
- **Odpowiedź:** NIE — aplikacja NIE używa RAG (Retrieval-Augmented Generation)
- Dlaczego nie (ADR-003): dwie proste procedury mieszczą się w oknie kontekstowym
- Jak polityki są osadzone: `PolicyDocumentLoader` ładuje Markdown → string → wstrzykiwany do prompta
- Kiedy RAG byłby potrzebny (backlog): wiele dokumentów, duże bazy wiedzy, dynamiczna aktualizacja
- Diagram: `[Diagram: Porównanie Prompt-Stuffing vs RAG — kiedy co wybrać]`
- Jak zaimplementować RAG jako rozszerzenie (wskazówki architektoniczne)

---

### 17. Pliki konfiguracyjne — kompletny opis

**Opis:** Każdy plik konfiguracyjny z opisem każdego klucza.

**Zawartość:**

#### Backend
- `pom.xml` — dependencies z wersjami i uzasadnieniem
- `application.yaml` — pełny opis wszystkich kluczy
- `src/main/resources/prompts/*.md` — format i instrukcje promptów
- `src/main/resources/policies/*.md` — procedury domenowe

#### Frontend
- `package.json` — scripts: start, build, test, lint
- `angular.json` — konfiguracja build, test, serve
- `tsconfig.json` / `tsconfig.app.json` / `tsconfig.spec.json`
- `proxy.conf.json` — dev proxy
- `eslint.config.js` — reguły lintowania

#### E2E
- `playwright.config.ts` — pełny opis opcji
- `package.json` (e2e) — scripts E2E

#### Infrastruktura
- `.env.example` — template ze wszystkimi zmiennymi
- `.mcp.json` — konfiguracja MCP serverów (Context7, Playwright)

---

### 18. Jak budować podobną aplikację AI — przewodnik

**Opis:** Ogólny przewodnik architektoniczny jak od zera zbudować aplikację AI podobną do tej, oparty na wnioskach z tego projektu.

**Zawartość:**

#### 18.1 Faza 1: Definiowanie kontraktu AI

- Zacznij od wyjścia modelu, nie wejścia
- Definiuj format odpowiedzi (JSON schema, markery tekstowe)
- Ustal fallback na przypadek błędów parsowania
- Strategia: ESCALATE jako bezpieczny default

#### 18.2 Faza 2: Wybór modelu i providera

- Kryteria: kontekst, multimodalność, koszt, latency, streaming support
- OpenRouter vs bezpośredni dostawca (pros/cons)
- Jak testować modele bez kodowania (Playground, curl)
- Diagram: `[Diagram: Drzewo decyzyjne wyboru modelu LLM]`

#### 18.3 Faza 3: Projektowanie promptów

- Cykl: drafting → testowanie ręczne → ewaluacja automatyczna
- System message: rola, ograniczenia, format wyjścia
- Jak osadzić wiedzę domenową (inject policy text)
- Kiedy RAG zamiast prompt stuffing
- Versioning promptów (w plikach, nie w kodzie)

#### 18.4 Faza 4: Integracja LLM w kodzie

- Hexagonal architecture: port abstracts the LLM
- Synchroniczne vs strumieniowane wywołania
- Error handling: timeout, unavailable, parse error
- Mockowanie LLM w testach (MockWebServer, stub serwer)

#### 18.5 Faza 5: Streaming do frontendu

- SSE vs WebSocket vs Long Polling (porównanie)
- Dlaczego POST SSE (fetch-event-source) zamiast GET EventSource
- Implementacja token-by-token UX
- Progressive UI: append delta → finalize on done

#### 18.6 Faza 6: Zarządzanie sesją i stanem

- In-memory vs database (trade-offs, TTL, skalowalność)
- Rehydracja sesji (reload-safe UX)
- Signal-based state vs Redux (kiedy co)

#### 18.7 Faza 7: Testy AI-first aplikacji

- Stub LLM zamiast prawdziwego API (determinizm + koszt)
- Test piramida: unit → integration → E2E
- Co testować bez mocków: parsowanie, fallback, edge cases
- Playwright + POM dla E2E z prawdziwym UI

#### 18.8 Checklista MVP aplikacji AI

```
[ ] Zdefiniowany kontrakt wyjścia AI (JSON schema / markery)
[ ] Prompt templates w plikach (nie hardcoded)
[ ] Fallback na błąd parsowania
[ ] Stub LLM do testów
[ ] Streaming z token-by-token UX
[ ] Error handling: timeout, unavailable
[ ] Session TTL + rehydracja
[ ] Walidacja po stronie serwera (niezaufany klient)
[ ] Monitoring: co mierzyć (latency, tokens, fallback rate)
```

---

### 19. Pytania i odpowiedzi (FAQ)

**Opis:** Najczęstsze pytania programistów trafiających do kodu po raz pierwszy.

**Zawartość (pytania z odpowiedziami):**

**Ogólnoarchitektoniczne:**
- Q: Dlaczego Spring Boot zamiast czegoś lżejszego (Quarkus, Micronaut)?
- Q: Dlaczego Angular zamiast React/Vue?
- Q: Dlaczego nie ma bazy danych?
- Q: Co to jest OpenRouter i dlaczego nie bezpośrednio OpenAI?
- Q: Dlaczego nie ma tool use / function calling?
- Q: Dlaczego nie ma RAG?

**O integracji LLM:**
- Q: Jak zmienić model LLM na inny?
- Q: Co się stanie gdy model zwróci niepoprawny JSON?
- Q: Jak model wie, kiedy użyć markera `[OFFTOPIC]`?
- Q: Dlaczego decyzja jest synchroniczna a chat asynchroniczny?
- Q: Co oznacza `composeFirstMessage()` i dlaczego jest po stronie backendu?
- Q: Jak dodać nową kategorię sprzętu?

**O streamingu:**
- Q: Dlaczego `@microsoft/fetch-event-source` zamiast natywnego EventSource?
- Q: Co się dzieje gdy stream zostanie przerwany w połowie?
- Q: Jak SseEmitter zarządza threadami?
- Q: Jak testować SSE bez prawdziwego backendu?

**O sesjach:**
- Q: Po jakim czasie sesja wygasa?
- Q: Co się stanie gdy użytkownik odświeży stronę?
- Q: Gdzie są przechowywane sesje — czy przeżywają restart serwera?

**O testach:**
- Q: Jak uruchomić tylko testy E2E?
- Q: Dlaczego stub a nie mockito dla E2E?
- Q: Jak dodać nowy scenariusz E2E?
- Q: Co testuje `EnumCompletenessTest`?

**O konfiguracji:**
- Q: Jak zmienić port backendu?
- Q: Jak skonfigurować CORS dla domeny produkcyjnej?
- Q: Jak podłączyć własny endpoint LLM (np. Ollama)?

---

### 20. Słownik pojęć

**Opis:** Definicje pojęć domenowych i technicznych używanych w dokumentacji.

**Zawartość:**

| Termin | Definicja |
|--------|-----------|
| COMPLAINT | Reklamacja sprzętu elektronicznego (typ sprawy) |
| RETURN | Zwrot sprzętu elektronicznego (typ sprawy) |
| APPROVE | Decyzja AI: sprawa zaakceptowana |
| REJECT | Decyzja AI: sprawa odrzucona |
| ESCALATE | Decyzja AI: wymaga decyzji człowieka |
| SSE | Server-Sent Events — protokół strumieniowania |
| RAG | Retrieval-Augmented Generation — wyszukiwanie + LLM |
| Vision Model | Model AI analizujący obrazy |
| Text Model | Model AI generujący tekst |
| Hexagonal Architecture | Porty i adaptery — separacja logiki od infrastruktury |
| Signals | Angular reactive primitives — alternatywa dla RxJS |
| Token | Podstawowa jednostka tekstu przetwarzana przez LLM |
| Token Delta | Przyrostowy kawałek tokenu w strumieniu |
| POM | Page Object Model — wzorzec enkapsulacji selektorów w testach |
| Prompt Stuffing | Osadzanie całej wiedzy bezpośrednio w prompcie |
| TTL | Time-To-Live — czas życia sesji |
| Stub | Testowy serwer udający prawdziwe API (deterministyczny) |

---

## Uwagi do wygenerowania dokumentu

- **Diagramy:** użyć Mermaid (sequenceDiagram, flowchart, classDiagram, graph TD) — renderowane w GitLab/GitHub i VS Code
- **Przykłady kodu:** pobrane z rzeczywistych plików projektu — nie wymyślone
- **Język:** polski, techniczny, jak senior developer tłumaczy juniorowi
- **Linki wewnętrzne:** sekcje powiązane przez anchory Markdown
- **Wersjonowanie:** wersja + data u góry; sekcja "Changelog" na dole

---

*Plan wygenerowany: 2026-06-30*  
*Na podstawie: eksploracji kodu `app/backend` (58 plików Java), `app/frontend` (Angular 20 standalone + signals), `app/e2e` (Playwright + stub), `docs/` (PRD + 5 ADR-ów).*
