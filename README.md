# NEXO Fabric

**Palantir-inspired semantic integration platform** that unifies data across systems using ontology-driven mapping, real-time connectors, AI-powered search, and a resolution engine.

Built by [NEXO AI](https://nexoai.ch).

## Architecture

```
                         ┌──────────────────────────────────┐
                         │      React Frontend (:3005)      │
                         │  Dashboard │ Builder │ Explorer   │
                         │  GraphView │ Chat   │ Connectors │
                         └──────────────┬───────────────────┘
                                        │ GraphQL + REST
┌───────────────────────────────────────▼───────────────────────────────────────┐
│                     Spring Boot API (:8081)                                   │
│                                                                              │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────┐  ┌───────────────────┐   │
│  │  Ontology   │  │    Action    │  │  Connector  │  │   AI Agent        │   │
│  │  Registry   │  │    Engine    │  │  Framework  │  │ (Anthropic/OpenAI │   │
│  │ (OT/PT/LT) │  │ (Audit/HITL)│  │ (JDBC/REST) │  │  /Ollama)         │   │
│  └─────────────┘  └──────────────┘  └────────────┘  └───────────────────┘   │
│                                                                              │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────┐  ┌───────────────────┐   │
│  │ Semantic    │  │  Multi-      │  │  Schema     │  │   CDC / Event     │   │
│  │ Search      │  │  Tenancy     │  │  Versioning │  │   Pipeline        │   │
│  │ (ONNX/384d) │  │  (JWT+RLS)  │  │ (History)   │  │  (Redis Streams)  │   │
│  └─────────────┘  └──────────────┘  └────────────┘  └───────────────────┘   │
└───────────────────────────┬───────────────────────────────┬──────────────────┘
                            │                               │
              ┌─────────────▼─────────────┐   ┌─────────────▼─────────────┐
              │  PostgreSQL + pgvector    │   │   Redis 7                 │
              │  (RLS, Flyway, HNSW)      │   │   (Cache, CDC Streams)    │
              └───────────────────────────┘   └───────────────────────────┘
```

### Design Patterns

- **Modular Monolith** — single deployable, cleanly separated modules
- **Hexagonal Architecture** — Ports & Adapters (`adapters/in/`, `core/`, `adapters/out/`)
- **DDD Aggregates** — ObjectType, OntologyObject, Action as aggregate roots

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21, Spring Boot 3.4, Maven |
| Database | PostgreSQL 16 + pgvector, Flyway migrations (V1-V23) |
| Cache | Redis 7 |
| Frontend | React 19, TypeScript, Vite, Tailwind CSS, Apollo Client |
| ML/Embeddings | DJL + ONNX Runtime (`all-MiniLM-L6-v2`, 384 dims) |
| AI Agent | Pluggable LLM (Anthropic Claude, OpenAI, Ollama/Qwen) |
| Graph | Cytoscape.js for relationship visualization |
| Workflow | n8n integration via webhooks |
| CDC | Debezium + Redis Streams (optional) |

## Repository Structure

```
nexo-fabric/
├── fabric-core/                 Core engine (open-source)
│   ├── fabric-api/              REST + GraphQL API (Spring Boot)
│   ├── fabric-engine/           Semantic mapping engine
│   ├── fabric-events/           Event bus and CDC
│   ├── fabric-resolution/       Entity resolution
│   └── fabric-query/            Unified query layer
├── fabric-sdk/                  Client SDKs
│   ├── typescript/              @nexoai/fabric TypeScript SDK
│   ├── python/                  nexo_fabric Python SDK
│   └── java/                    Java SDK
├── platform/
│   ├── platform-backend/        Platform API extensions
│   └── platform-frontend/       React web application (10 pages)
├── docker/                      Docker Compose + infrastructure
│   ├── docker-compose.dev.yml
│   ├── postgres/init.sql
│   └── n8n/templates/
└── docs/                        API reference, concepts, quickstart
```

## Features

### Core
- **Ontology Management** — define Object Types, Property Types, Link Types
- **Data Operations** — CRUD, batch upsert, streaming NDJSON ingestion
- **Semantic Search** — ONNX-powered vector embeddings (384d), pgvector cosine similarity
- **Entity Resolution** — deduplication and conflict resolution across sources
- **Graph Traversal** — link-based relationship exploration

### AI Agent
- Natural language queries with tool-calling (schema, search, traverse, aggregate)
- Pluggable LLM: Anthropic Claude, OpenAI, Ollama (Qwen), or keyword fallback
- Human-in-the-loop approval flow for sensitive actions
- Multi-turn conversation with session history

### Enterprise
- Multi-tenancy with JWT auth + PostgreSQL Row-Level Security
- API key authentication (`X-API-Key` header)
- Rate limiting per tenant plan (FREE/STARTER/PRO/ENTERPRISE)
- Schema versioning with change history and backfill
- CDC real-time sync via Debezium + Redis Streams
- Audit logging, data export, notifications
- n8n workflow automation

## Quick Start

### Prerequisites

- Java 21, Maven 3.9+
- Node.js 20+
- Docker & Docker Compose

### 1. Environment

```bash
cp .env.example .env
# Edit .env — set passwords and optionally configure LLM provider
```

### 2. Start Infrastructure

```bash
docker compose -f docker/docker-compose.dev.yml --env-file .env up -d
```

This starts PostgreSQL (pgvector), Redis, the Spring Boot backend, and n8n.

### 3. Start Frontend

```bash
cd platform/platform-frontend
npm install
npm run dev
```

### 4. Access

| Service | URL |
|---------|-----|
| Frontend | http://localhost:3005 |
| Backend API | http://localhost:8082 |
| GraphiQL IDE | http://localhost:8082/graphiql |
| n8n Workflows | http://localhost:5678 |

### 5. First Steps

```bash
# Register a tenant
curl -X POST http://localhost:8082/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"tenantApiName":"my-org","tenantDisplayName":"My Org","email":"admin@example.com","password":"changeme"}'

# Use the returned JWT token for all subsequent requests
```

## LLM Configuration

Set in `.env` — no code changes needed:

```bash
# Anthropic Claude
NEXO_LLM_PROVIDER=anthropic
ANTHROPIC_API_KEY=sk-ant-...

# OpenAI
NEXO_LLM_PROVIDER=openai
OPENAI_API_KEY=sk-...

# Local Ollama (Qwen, Llama, etc.)
NEXO_LLM_PROVIDER=ollama
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=qwen2.5:7b

# No LLM (keyword-based fallback)
NEXO_LLM_PROVIDER=none
```

## API Overview

### REST (`/api/v1/`)
- `POST /api/auth/register` — create tenant + admin user
- `POST /api/auth/login` — JWT authentication
- `GET/POST /api/v1/ontology/object-types` — manage schemas
- `GET/POST /api/v1/connectors` — manage data sources
- `GET/POST /api/v1/api-keys` — API key management

### GraphQL (`/graphql`)
```graphql
# Query
getAllObjectTypes { apiName displayName properties { apiName dataType } }
searchObjects(objectType: "Customer") { items { id properties } totalCount }
semanticSearch(query: "...", objectType: "...", limit: 5) { object { properties } similarity }
getSchemaVersions(objectTypeApiName: "...") { version changeSummary }

# Mutation
createObject(objectType: "Customer", properties: {...}) { id }
agentChat(message: "How many customers?") { message toolCalls { tool } }
```

## Development

```bash
# Build
cd fabric-core && mvn compile

# Test
cd fabric-core && mvn test
# 19 tests, 0 failures

# Rebuild Docker backend
docker compose -f docker/docker-compose.dev.yml --env-file .env up -d --build backend
```

## License

MIT — see [LICENSE](LICENSE).
