# End-to-End Test Suite

Integration tests that exercise every layer of NEXO Fabric against a running stack,
using a fictional demo tenant (**Acme Swiss Consulting**) with realistic mock data.

No credentials are stored in this directory — `.token` is generated at runtime and gitignored.

## What gets tested

- **Auth** — JWT, API keys, rate limiting, unauthenticated rejection
- **Ontology** — ObjectTypes, PropertyTypes, dynamic property addition, LinkTypes
- **Schema Versioning** — automatic version history on update/add/remove
- **Object CRUD** — create, search, filter, count
- **Semantic Search** — ONNX embeddings (384d) with pgvector cosine similarity
- **Lifecycle State Machines** — transitions with validation and role checks
- **Functions / Compute** — Rhino JS engine, execution log, syntax validation
- **Workflow Engine** — trigger, run history, WAIT/CONDITION steps
- **AI Agent** — tool-calling (Anthropic, OpenAI, Ollama, or keyword fallback)
- **Action Engine** — audit log
- **Connectors** — JDBC/REST/CSV + marketplace catalog
- **Observability** — health, notifications, audit events

## Prerequisites

1. Backend stack running:
   ```bash
   cd ../../docker
   docker compose -f docker-compose.dev.yml --env-file ../.env up -d
   ```
2. Python 3 on PATH (used for UTF-8-safe HTTP and JSON parsing — avoids a Windows
   Git-Bash Latin-1 conversion bug with curl `-d`).

## Running the tests

```bash
# 1. Seed the demo tenant (idempotent — re-registers or logs in)
./seed.sh

# 2. Full comprehensive test suite
./test-all.sh

# 3. AI agent tests (works with or without a configured LLM; fallback to keywords)
./test-ai.sh

# 4. TypeScript SDK code generation from live schema
./test-codegen.sh

# 5. Remove lifecycle and function (keeps the tenant + objects)
./cleanup.sh
```

All scripts read `$NEXO_API_URL` (default `http://localhost:8082`) so you can point
them at any instance.

## Demo data

| File | Records | What it represents |
|------|:---:|---|
| `data/customers.json` | 5 | Fictional B2B customers across CH regions |
| `data/invoices.json` | 12 | Invoices (paid / open / overdue) linked by customer name |
| `data/tickets.json` | 8 | Support tickets with priorities |
| `functions/calculateRisk.js` | — | JS function computing a customer risk score |

Seed also defines:
- **Customer Lifecycle** — `prospect → qualified → active → at_risk → churned` (5 states, 6 transitions)
- **Workflow** — `Risk Alert` (NOTIFY → WAIT 3s → END)

## Test expectations

`test-all.sh` runs a set of assertions with a summary footer. A successful run
against a freshly seeded database looks like:

```
═══ Summary ═══
  Passed: 59 / 59
🎯 All layers operational!
```

`test-ai.sh` behaves differently depending on the configured LLM provider:

- `NEXO_LLM_PROVIDER=none` (default) → keyword-based routing, answers in German,
  covers the ~5 most common query shapes.
- `NEXO_LLM_PROVIDER=ollama|anthropic|openai` → full tool-calling loop; expect
  richer answers and slower responses (500 ms – 3 s with a local GPU model).

See [`docs/testing.md`](../../docs/testing.md) for sample outputs and benchmarks.

## How to add a new test

1. Add a scenario block to `test-all.sh` following the existing pattern:
   ```bash
   section "13. My New Feature"
   RESULT=$(curl -s ... )
   check "My feature returns expected data" "$RESULT" "expected-substring"
   ```
2. If the feature needs seed data, extend `seed.sh` idempotently (check if it
   already exists first).

## Known issues reproducible by these tests

See `docs/testing.md` for a list; two noteworthy items:

1. **Multi-tenancy RLS** doesn't filter when the DB user is a superuser — the
   default Postgres role `nexo` in `docker-compose.dev.yml` has `BYPASSRLS`.
   Production deployments should use a non-superuser application role.
2. **`tenant_id` column** is present on the schema but the JPA `ObjectTypeEntity`
   doesn't map it yet, so inserts store `NULL`. Tracked for a follow-up fix.
