# Testing

NEXO Fabric is verified at three levels: unit tests (Maven), end-to-end tests
(black-box HTTP/GraphQL), and manual AI-agent scenarios. All of them are
reproducible against a fresh `docker compose up` without external dependencies.

## Unit tests (Maven)

```bash
cd fabric-core
mvn test
```

Current status: **19 tests · 0 failures · 0 errors · BUILD SUCCESS**.

These cover the domain core: ObjectType registry, property management, link types,
and the ObjectService CRUD path with mocks for repositories and tenant limits.
Spring context is intentionally **not** loaded — the core is hexagonal and its
domain classes have no framework dependencies.

## End-to-End tests

Full black-box suite against the running stack. See
[`tests/e2e/README.md`](../tests/e2e/README.md) for setup and scripts. The
suite uses a fictional tenant "Acme Swiss Consulting" with 5 customers,
12 invoices, and 8 tickets.

### Coverage matrix

| # | Section | Scenarios | Last run |
|:---:|---------|-------|:---:|
| 1 | Auth & Security | Unauthenticated 403, JWT 200, Login 400/401, Health 200, Invalid JWT 403, API-Key auth | ✅ 7 / 7 |
| 2 | Ontology Schema | Customer/Invoice/Ticket types, property types with data types, dynamic property add | ✅ 7 / 7 |
| 3 | Schema Versioning | Auto version history, multi-version tracking | ✅ 3 / 3 |
| 4 | Object CRUD & Search | totalCount, properties, search by name, create via GraphQL | ✅ 7 / 7 |
| 5 | Semantic Search (ONNX) | Natural-language queries on Customer + Invoice (384-d embeddings) | ✅ 3 / 3 |
| 6 | Lifecycle State Machines | Definition, available transitions, valid/invalid transitions, rehabilitation | ✅ 7 / 7 |
| 7 | Functions / Compute | Create, execute with input, on-object, test without save, syntax validation, execution log | ✅ 8 / 8 |
| 8 | Workflow Engine | List, trigger, run history | ✅ 3 / 3 |
| 9 | AI Agent Tool-Calling | Schema query, multi-turn, search-tool trigger, audit log | ✅ 5 / 5 |
| 10 | Action Engine | Audit log queryable | ✅ 2 / 2 |
| 11 | Connectors | List + marketplace catalog | ✅ 2 / 2 |
| 12 | Observability | Health, info, notifications, audit events, API keys | ✅ 5 / 5 |

**Total: 59 / 59 passing.**

Run with:
```bash
cd tests/e2e
./seed.sh      # idempotent; seeds or re-uses the demo tenant
./test-all.sh  # runs all 59 assertions
```

### Sample output

```
▶ 1. Auth & Security
  ✓ Unauthenticated request returns 403           403
  ✓ JWT authenticated request returns 200         200
  ✓ Login endpoint is public (400/401 not 403)    400
  ✓ Health endpoint is public (200)               200
  ✓ Invalid JWT returns 403                       403
  ✓ API key created (nxo_ prefix)                 nxo_…
  ✓ API key authenticates (200)                   200

▶ 5. Semantic Search (ONNX 384d)
  ✓ Semantic search: IT consulting                 {"similarity":0.26…,"name":"Schmidt IT Services"…}
    → Top match for 'IT consulting': Schmidt IT Services
  ✓ Semantic search: overdue payments              {"similarity":0.33…}
  ✓ Semantic search on Invoices                    {"similarity":0.39…}

▶ 7. Functions / Compute Layer
  ✓ calculateRisk exists                           {"apiName":"calculateRisk"…}
  ✓ Function: high-risk input → 'high'           {"score":"high","reason":"3+ overdue …"}
  ✓ Function: medium-risk input → 'medium'       {"score":"medium","reason":"Some risk …"}
  ✓ Function: low-risk input → 'low'             {"score":"low","reason":"…good standing"}
  ✓ Function on real object returns result         {"success":true,"output":{"score":"low",…}}
  ✓ Function test endpoint: 6*7=42                 {"success":true,"output":42.0,…}
  ✓ Syntax error rejected                          {"error":"JavaScript syntax error: missing ; …"}
  ✓ Execution log has entries                      10 entries

═══ Summary ═══
  Passed: 59 / 59
🎯 All layers operational!
```

## AI Agent tests

The agent has four tools registered (`getOntologySchema`, `searchObjects`,
`traverseLinks`, `aggregateObjects`, `callFunction`) and supports multi-turn
sessions. Tests run against whichever provider `NEXO_LLM_PROVIDER` selects.

### Provider comparison

Tested on an RTX 3080 Ti (12 GB VRAM) with the demo data seeded.

| Provider | Cold start | Warm (single-tool query) | Multi-turn reasoning | Notes |
|----------|:---:|:---:|:---:|---|
| `none` (keyword fallback) | — | ~70 ms | ❌ | Only covers the common patterns (schema, count, search) |
| Ollama · `llama3.2:3b` (GPU) | ~48 s | 0.5–1 s | ⚠️ Sometimes hallucinates tool arguments | Runs at 100 % GPU; fast but limited reasoning |
| Ollama · `qwen2.5:7b` / `llama3.1:8b` (GPU) | ~60 s | 1.5–3 s | ✅ | Recommended for serious use |
| Anthropic Claude | — | 1–2 s (network-bound) | ✅ | Highest quality, paid |
| OpenAI GPT-4o | — | 1–2 s | ✅ | Similar to Claude |

### Sample agent session (Ollama llama3.2:3b on GPU)

```
[Test 1] Tool: getOntologySchema
  Frage: Welche Object Types gibt es im System?
  Antwort (2326ms): Es gibt vier verschiedene Object-Types im System:
                    1. **TestX**: Ein Objekt mit einer Eigenschaft `n`…
                    2. **Rechnung (Invoice)**: Ein Objekt mit fünf Eigenschaften:
                       * `number`: Eine Zeichenfolge
                       * `customer`: Eine Zeichenfolge…
                    3. **Ticket**…
                    4. **Customer**…
  Tools: getOntologySchema
  ✓ Tool 'getOntologySchema' wurde aufgerufen
  ✓ Antwort enthält 'Customer'

[Test 2] Tool: aggregateObjects COUNT
  Frage: Wie viele Customer gibt es insgesamt?
  Antwort (527ms): Es gibt 8 Kunden.
  Tools: aggregateObjects
  ✓ Tool 'aggregateObjects' wurde aufgerufen
  ✓ Antwort enthält '8'

[Test 4] Tool: searchObjects einfach
  Frage: Such mir alle Customer
  Antwort (2874ms): Hier ist eine Liste der gefundenen Kunden:
                    * Schmidt IT Services
                      - E-Mail: office@schmidt-it.ch
                      - Notizen: Managed-Services-Kunde seit 2024
                      - Region: Genf
                      - Status: aktiv
                      - Umsatz: …
                    …
  ✓ Tool 'searchObjects' wurde aufgerufen
```

Run with:
```bash
cd tests/e2e
./test-ai.sh
```

See [`docs/ai-local-llm.md`](ai-local-llm.md) for setting up Ollama locally.

## Performance benchmarks

Measured on an RTX 3080 Ti, 12 GB VRAM, on the seeded demo data.

### API latency (without LLM calls)

| Request | Latency |
|---------|:---:|
| `GET /actuator/health` | < 5 ms |
| `GET /api/v1/ontology/object-types` (JWT) | ~25 ms |
| `POST /graphql { searchObjects(…) }` (100 items) | ~40 ms |
| `POST /graphql { semanticSearch(…) }` (cold, first embedding) | ~300 ms |
| `POST /graphql { semanticSearch(…) }` (warm) | ~30 ms |
| `POST /api/v1/functions/:name/execute` (Rhino JS, trivial) | ~15 ms |

### Semantic search quality

Query `"IT consulting services"` against 5 customer objects:

| Rank | Customer | Industry | Similarity |
|:---:|----------|----------|:---:|
| 1 | Schmidt IT Services | IT Services | 0.20 |
| 2 | Becker GmbH | Startup | 0.18 |
| 3 | Huber & Co | Consulting | 0.14 |

The model — `sentence-transformers/all-MiniLM-L6-v2` via ONNX Runtime —
correctly puts the IT-industry customer at rank 1 even though the query
is in English and the property values are a mix of German and English.

## Architectural checks enforced by tests

These assertions guard against regressions of important invariants:

- `/api/v1/**` requires authentication (never returns 200 without JWT/API key).
- `/api/auth/**` is not reachable as `403` — it must accept unauthenticated POSTs.
- Invalid lifecycle transitions return **400** (not 403 — earlier bug, see below).
- JavaScript syntax errors on function creation return **400** with a message.
- Function execution has a **5 s hard timeout** (enforced by `FunctionExecutionEngine`).
- Schema versioning auto-triggers on `updateObjectType`, `addProperty`, and
  `removeProperty` — the test creates multiple versions and checks history.
- Object creation triggers async embedding via `TransactionSynchronization.afterCommit`
  so semantic search finds new objects within a few seconds.

## Bugs found and fixed by this test suite

| Bug | Commit |
|-----|--------|
| `InvalidTransitionException` and `FunctionSyntaxException` were not caught by `GlobalExceptionHandler` → Spring Security's `ExceptionTranslationFilter` converted them to `403 Forbidden` with an empty body, which leaked as auth failures | [`5ebbfda`](https://github.com/AlsoTheZv3n/nexo-fabric/commit/5ebbfda) |
| Embedding pipeline's `@Async` ran before `createObject` transaction committed → new objects didn't show up in semantic search | [`4a69b8a`](https://github.com/AlsoTheZv3n/nexo-fabric/commit/4a69b8a) |
| DJL native tokenizer required glibc; Alpine base image failed → Dockerfile switched to `eclipse-temurin:21-jre-jammy` | [`1bdadaf`](https://github.com/AlsoTheZv3n/nexo-fabric/commit/1bdadaf) |

## Known issues visible in tests

- **Multi-tenancy RLS isolation** is currently not enforced because the default
  PostgreSQL role (`nexo`) is a superuser with `BYPASSRLS`. The RLS policies and
  the `TenantAwareDataSource` that sets `app.tenant_id` are in place, but take
  effect only when the app connects as a non-superuser role. A follow-up will
  add a `nexo_app` role to the init script.
- **`ObjectTypeEntity` doesn't map `tenant_id`** yet, so inserts store NULL.
  The schema, index, and RLS policy all reference the column. The domain model
  needs a `tenantId` field + mapper update. Tracked as a follow-up.
- **Llama 3.2 3B** occasionally generates hallucinated UUIDs when invoking
  `callFunction`. Use `qwen2.5:7b` or `llama3.1:8b` for production-grade
  reasoning.

## Continuous integration

A CI pipeline that runs `mvn test` on every push is a reasonable next step.
The e2e suite needs a running stack and is more suited to a nightly job or
pre-release check.
