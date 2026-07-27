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

1. Copy `.env.example` to `.env` and fill in real secrets.
2. `docker compose -f infra/docker-compose.yml up -d`
3. Backend: `cd apps/api && ./mvnw spring-boot:run`
4. Frontend: `cd apps/web && npm install && npm run dev`

## Testing

- Backend unit tests: `cd apps/api && ./mvnw test`
- Frontend unit tests: `cd apps/web && npm test`
- End-to-end: bring up `infra/docker-compose.yml` and `apps/api`, then `cd apps/web && npm run test:e2e`