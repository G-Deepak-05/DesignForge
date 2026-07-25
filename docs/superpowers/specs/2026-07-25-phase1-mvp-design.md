# DesignForge — Phase 1 MVP Design

## Status
Approved by user on 2026-07-25. Ready for implementation planning.

## Scope

Full product vision is in the root PRD (14 modules, multilingual, full stack). This spec covers only **Phase 1**, as scoped in PRD section 15:

**Built (Phase 1):**
- Module 1 — Learning Hub (structured LLD/HLD roadmap navigation)
- Module 2 — Interactive Learning (lesson engine: explanation → visualization → interview notes → quiz → follow-up)
- Module 3 — LLD Interview Simulator
- Module 4 — HLD Interview Simulator
- Module 6 — Design Pattern Explorer
- Module 14 — AI Interview Feedback
- Multilingual: English + Hindi, Kannada, Tamil, Telugu
- Minimal diagram rendering (Mermaid-based, read-only) — enough for the simulators to display UML during an interview

**Explicitly deferred (stubbed as "coming soon" routes only, no logic):**
- Module 5 — Diagram Builder (full drag-and-drop editor, prompt-to-UML generation)
- Module 7 — Company Interview Prep
- Module 8 — Code to UML
- Module 9 — UML Validator
- Module 10 — Architecture Reviewer
- Module 11 — Personalized Roadmap
- Module 12 — Flashcards
- Module 13 — Cheat Sheets
- Gamification (XP, streaks, badges) and Analytics dashboards

Rationale: PRD's own Phase 1 definition. Building all 14 modules simultaneously produces an unreviewable spec and an unshippable implementation. Deferred modules get route/schema stubs so the full navigation map exists, but no business logic.

## Architecture

Monorepo, full PRD tech stack (section 14) provisioned from day one via Docker Compose, since infra availability was confirmed (Docker, Java 21, Maven, Node all present locally).

```
DesignForge/
├── apps/
│   ├── web/            Next.js 14 (App Router), TypeScript, Tailwind CSS, shadcn/ui,
│   │                    React Flow (diagram rendering), Mermaid integration
│   └── api/             Spring Boot 3 (Java 21), modular monolith
│       modules: auth, learning, patterns, simulator, ai-gateway, diagrams, common
├── packages/
│   └── shared-types/    TypeScript types mirroring API DTOs (hand-maintained in Phase 1;
│                        revisit codegen if drift becomes a problem)
├── infra/
│   └── docker-compose.yml   Postgres, Redis, Kafka + Zookeeper, Elasticsearch, MinIO
├── .env.example         OLLAMA_API_KEY and other secrets, placeholder values only
└── docs/superpowers/specs/
```

**Why modular monolith, not microservices:** one deployable Spring Boot app with clean package-per-module boundaries. Avoids premature distributed-systems overhead for a pre-launch product; module boundaries make a future service split mechanical if traffic ever demands it.

## AI Pipeline (PRD section 7)

Implemented entirely inside the `ai-gateway` module. No other module calls Ollama directly.

```
User request
  → Prompt Engine       (per-module prompt template, fills in user context + question)
  → Knowledge Engine     (Postgres-seeded curated content: design patterns, SOLID rules,
                          common mistakes, interview question bank — the LLM personalizes,
                          it does not invent this content)
  → Ollama Cloud API call
  → Response Formatter   (parses raw model output into a strict JSON schema per module)
  → Design Validator     (validates JSON schema + domain rules; on failure, retries the
                          LLM call once with a corrective prompt; on second failure, falls
                          back to a curated canned response)
  → UI Renderer (frontend)
```

Raw LLM text is never sent to the frontend — only validated structured JSON. `OLLAMA_API_KEY` is read from environment; `.env` is gitignored, `.env.example` is committed with a placeholder. The real key is added locally by the user when ready to test live calls (config wired now, key supplied later, per user decision).

## Interview Simulator Flow (Modules 3 & 4)

- Live session state held via Spring WebSocket (`spring-boot-starter-websocket`).
- Each user action (class/entity definition, relationship, requirement answer) is sent over the socket and evaluated against the Knowledge Engine's rubric:
  - LLD: abstraction, encapsulation, patterns, naming, scalability, extensibility, thread safety, testability
  - HLD: requirements, traffic estimation, database choice, caching, scaling, failure recovery, observability, tradeoffs
- Feedback is incremental (evaluated as the user progresses, matching PRD's "AI evaluates ... Missing abstraction ... Use Strategy Pattern" example), not just a single end-of-session verdict.
- On WebSocket disconnect, session state is persisted (Postgres) and resumed from the last checkpoint on reconnect — no lost progress.
- Diagrams the user builds during the session render via Mermaid (read-only view in Phase 1; the full editable Diagram Builder is Phase 2).

## Data Model (high level)

- `users`, `auth_sessions` — Spring Security + JWT
- `lessons`, `lesson_progress` — Learning Hub / Interactive Learning content and per-user progress
- `patterns` — Design Pattern Explorer (intent, problem, solution, per-language examples, pros/cons, interview questions, mistakes)
- `interview_sessions`, `interview_events`, `interview_scores` — simulator state and rubric scores
- `content_i18n` — translations keyed by content id + locale (en, hi, kn, ta, te)

Curated content (patterns, questions, SOLID rules, mistakes) ships as seed data (SQL/JSON fixtures) at launch, editable later without a CMS.

## Multilingual Support

- Locale stored per-user, switchable in-session.
- UI strings via a standard i18n library (`next-intl` or equivalent) — English source of truth.
- Domain/technical terms (class names, pattern names, code) stay in English inside translated content per PRD section 10 — translation applies to explanatory prose only, not identifiers or code.
- AI-generated responses request output in the user's selected locale as part of the Prompt Engine template, with technical terms pinned in English via the prompt.

## Kafka / Elasticsearch / MinIO in Phase 1

- **Kafka:** one topic, `interview.completed`, published when a session ends. Consumer is a stub logger — real gamification/analytics processing is Phase 2. Skeleton exists now to match the "full stack from day one" decision.
- **Elasticsearch:** indexes `patterns` and the interview question bank for Learning Hub / Pattern Explorer search.
- **MinIO:** provisioned and configured but has no active feature in Phase 1 (first real consumer is the Phase 2 UML Validator / Architecture Reviewer file uploads). Bucket created, unused.

## Testing

- **Backend:** JUnit 5 per module; Spring `@WebMvcTest`/`@DataJpaTest` slices for controllers/repositories; a focused test on the Design Validator's retry/fallback logic since that's the hallucination-safety mechanism.
- **Frontend:** Vitest + React Testing Library for components; particular coverage on the interview simulator's incremental feedback rendering.
- **E2E:** one Playwright smoke test — start an LLD interview, submit a design, receive a scored rubric response.

## Error Handling

- AI provider timeout/error: never surfaces raw provider errors to the user; Design Validator returns a curated fallback response and logs the failure.
- Malformed AI JSON: one corrective retry, then fallback (see AI Pipeline above).
- WebSocket disconnect mid-interview: session resumes from last persisted checkpoint; no silent data loss.
- Auth/session expiry: standard redirect-to-login, no partial-state UI.

## Out of Scope for Phase 1 (explicit, to prevent scope creep during implementation)

- Company-specific interview flows (Module 7)
- Code-to-UML, UML Validator, Architecture Reviewer (Modules 8–10)
- Personalized Roadmap, Flashcards, Cheat Sheets (Modules 11–13)
- Gamification and analytics dashboards
- Any language beyond en/hi/kn/ta/te
- Voice-based interviews, resume-aware customization (Phase 2/3 per PRD section 15)
