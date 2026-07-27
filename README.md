# DesignForge

Master Low-Level Design (LLD) & High-Level Design (HLD) Interviews.

## Monorepo layout

- `apps/api` — Spring Boot 3 backend (Java 21)
- `apps/web` — Next.js 14 frontend
- `packages/shared-types` — shared TypeScript DTOs
- `infra` — Docker Compose infra (Postgres, Redis, Kafka, Elasticsearch, MinIO)
- `docs/superpowers/specs` — design specs
- `docs/superpowers/plans` — implementation plans

## Local development

1. Copy `.env.example` to `.env` (repo root) and fill in real secrets. `JWT_SECRET` has no
   default — the backend fails to start without it. Generate one with `openssl rand -base64 48`.
2. `docker compose -f infra/docker-compose.yml up -d`
3. Backend: `cd apps/api && export $(grep -v '^#' ../../.env | xargs) && ./mvnw spring-boot:run`
4. Frontend: `cd apps/web && cp .env.example .env.local && npm install && npm run dev`

### Environment variables

Backend variables live in the root `.env` (see `.env.example`); frontend variables live in
`apps/web/.env.local` (see `apps/web/.env.example`), per Next.js convention.

| Variable | Where | Default | Purpose |
| --- | --- | --- | --- |
| `JWT_SECRET` | root `.env` | none (required) | HMAC signing key for access tokens; must be at least 32 bytes. |
| `CORS_ALLOWED_ORIGINS` | root `.env` | `http://localhost:3000` | Comma-separated browser origins allowed to call the API. |
| `NEXT_PUBLIC_API_BASE_URL` | `apps/web/.env.local` | `http://localhost:8080` | Base URL the frontend uses to reach the backend. |

## Testing

- Backend unit tests: `cd apps/api && ./mvnw test`
- Frontend unit tests: `cd apps/web && npm test`
- End-to-end: bring up `infra/docker-compose.yml` and `apps/api`, then `cd apps/web && npm run test:e2e`