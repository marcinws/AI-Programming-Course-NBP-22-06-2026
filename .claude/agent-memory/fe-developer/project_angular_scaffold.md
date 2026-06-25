---
name: project_angular_scaffold
description: Angular 20 scaffold for Hardware Service Copilot at app/frontend — key versions, deviations, and setup notes
metadata:
  type: project
---

Angular 20 scaffold created at `app/frontend` for the Hardware Service Copilot MVP (ADR-005, ADR-002).

**Key installed versions:**
- @angular/core: 20.3.25
- @angular/material: 20.2.14 (azure-blue theme, typography, animations)
- ngx-markdown: 20.1.0 (NOT latest — pinned to ^20 for Angular 20 compatibility)
- marked: 16.4.2
- @microsoft/fetch-event-source: 2.0.1

**Why:** ngx-markdown@22 requires Angular 22. The latest compatible version for Angular 20 is ngx-markdown@20.1.0 which supports marked ^15||^16.

**How to apply:** When updating ngx-markdown or adding it to new projects, always match the major version to the Angular major version (ngx-markdown@20.x for Angular 20).

**Folder structure created:**
- `src/app/core/` — CaseService stub, models (PendingState, DisplayMessage)
- `src/app/features/form/` — IntakeFormComponent (placeholder)
- `src/app/features/chat/` — ChatComponent (placeholder)

**Dev proxy:** `proxy.conf.json` maps `/api` → `http://localhost:8080`. Start script: `ng serve --proxy-config proxy.conf.json`.

**Angular CLI:** NOT globally installed. Use `npx -p @angular/cli@20 ng` for initial scaffold, then `npx ng` inside project directory (uses local node_modules/.bin/ng).
