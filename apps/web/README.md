# DesignForge Web

Next.js 14 frontend for DesignForge. See the [root README](../../README.md) for full setup instructions.

## Development

```bash
npm install
npm run dev
```

Copy `.env.example` to `.env.local` and adjust `NEXT_PUBLIC_API_BASE_URL` if the backend is not on `http://localhost:8080`.

## Testing

```bash
npm test          # Vitest unit tests
npm run test:e2e  # Playwright end-to-end tests (requires the backend running)
```
