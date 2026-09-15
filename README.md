# StockPulse - AI Inventory & Dynamic Pricing Engine

A reactive commerce advisor: when inventory drops below a reorder threshold or demand
velocity spikes, StockPulse automatically generates AI (or rule-based) pricing and
replenishment recommendations and queues them for a merchandiser to approve - without
anyone having to ask for it.

Backend: Java 21 · Spring Boot 3.3 · Spring Data JPA · H2 (in-memory)
Frontend: React 18 · Vite

## Quick start (< 5 minutes)

**Prerequisites:** JDK 17+ (JDK 21 recommended), Maven, Node 18+.

```bash
# 1. Backend - runs on http://localhost:8080, seeds 8 demo products automatically
cd backend
mvn spring-boot:run

# 2. Frontend - runs on http://localhost:5173, in a second terminal
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. No API keys or external services are required to run the
full system end to end: the default active strategy for both pricing and reorder is
**RULE_BASED**, which is deterministic and has zero external dependencies. Switch either
engine to **AI** from the header once you've configured an LLM provider (see below) - the
switch takes effect immediately, no restart needed.

## Demo paths (from the seeded catalog)

- **Inventory low:** `PRD-003` (Organic Cotton T-Shirt) seeds already below its reorder
  threshold (stock 8, threshold 15) with a pending pricing + reorder suggestion. Click
  "Simulate sale" on any product to push it below threshold and watch a new pair of
  suggestions appear within a few seconds (polling interval: 4s).
- **Demand spike:** `PRD-008` (Hoodie) starts at velocity 15 against APPAREL peers
  averaging ~7/24h. A handful of simulated sales (or one `POST /api/products/PRD-008/orders`
  with `{"quantity": 10}`) pushes velocity past 3x the peer average and fires a
  `DEMAND_SPIKE`-tagged suggestion pair alongside the `INVENTORY_LOW` one.

## Configuring an AI provider

Set these in `backend/src/main/resources/application.properties` (or override via env
vars / `-D` flags - never commit a real key):

| Property | Gemini | Groq | Ollama (local, default) |
|---|---|---|---|
| `llm.provider` | `gemini` | `groq` | `ollama` |
| `llm.base-url` | `https://generativelanguage.googleapis.com` | `https://api.groq.com` | `http://localhost:11434` |
| `llm.model` | `gemini-1.5-flash` | `llama-3.1-8b-instant` | `llama3.1` |
| `llm.api-key` | required | required | not required |

The key is read via `${LLM_API_KEY:}` from the environment - set `LLM_API_KEY` before
starting the backend rather than editing the properties file. If the provider is
unreachable, times out, or returns something unparseable/out-of-bounds, every AI
strategy transparently falls back to its rule-based counterpart (see `AiPricingStrategy`
/ `AiReorderStrategy`) - suggestions are never silently dropped.

## Runtime strategy switching

`GET /api/config/strategy` / `PATCH /api/config/strategy` (body:
`{"suggestionType": "PRICING" | "REORDER", "mode": "RULE_BASED" | "AI"}`) flips the
active strategy with no restart and no code change. The React header exposes this as a
two-way toggle per engine.

## API surface

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/products` | Create a product |
| GET | `/api/products?status=&category=` | Filterable catalog |
| GET | `/api/products/{id}` | Product detail |
| PATCH | `/api/products/{id}/stock` | Absolute stock correction; fires the agentic loop |
| POST | `/api/products/{id}/orders` | Simulate a sale; fires the agentic loop |
| POST | `/api/products/{id}/suggest-pricing` | On-demand (MANUAL) pricing suggestion |
| POST | `/api/products/{id}/suggest-reorder` | On-demand (MANUAL) reorder suggestion |
| POST | `/api/products/{id}/suggest-pricing/stream` | Bonus: SSE token stream of AI reasoning |
| GET | `/api/pricing-suggestions?status=` | List pricing suggestions |
| PATCH | `/api/pricing-suggestions/{id}` | Accept/reject; accept updates `currentPrice` |
| GET | `/api/reorder-suggestions?status=` | List reorder suggestions |
| PATCH | `/api/reorder-suggestions/{id}` | Accept/reject; accept increments `stockLevel` |
| GET/PATCH | `/api/config/strategy` | Read/switch the active strategy per suggestion type |

All error responses are a clean JSON `ErrorResponse` (no stack traces); validation
failures return `400` with field-level details, unknown resources `404`, invalid state
transitions (e.g. deciding an already-decided suggestion, or a duplicate SKU) `409`.

## Testing

```bash
cd backend
mvn test
```

Covers: rule-based pricing/reorder strategy math (unit), and a full end-to-end agentic
loop test (`AgenticLoopIntegrationTest`) that boots the real application, drops a
product's stock below its reorder threshold over HTTP, waits for the async suggestion to
appear, accepts it, and asserts the product's live price updated.

## Project structure

```
backend/   Spring Boot app (domain, repository, strategy, ai, event, service, controller)
frontend/  React (Vite) merchandising console
ADR.md     Architecture decision record (start here for the "why")
```

## Security notes

- CORS is locked to an explicit origin allowlist (`http://localhost:5173`,
  `http://localhost:4200`) on `/api/**` only - never a wildcard.
- Every write endpoint binds to an explicit request DTO (never the JPA entity), so a
  client can never set server-managed fields like `id`, `status`, or timestamps.
- The LLM API key is read from an environment variable and never logged, echoed in a
  response, or committed to the repo (`.gitignore` excludes `.env*`).
- All outbound LLM HTTP calls have explicit connect/read timeouts so a hung provider
  can't exhaust the agentic loop's bounded thread pool.
- `npm audit` / dependency review: 0 known vulnerabilities in the frontend toolchain.
- **No authentication/authorization is implemented.** The brief scopes this sprint to the
  inventory-signal → recommendation → approval loop for a single merchandising team, with
  no mention of accounts or roles - adding auth now would be scope creep beyond what was
  asked. Noted here explicitly rather than left as a silent gap: this API should not be
  exposed outside a trusted network as-is.
