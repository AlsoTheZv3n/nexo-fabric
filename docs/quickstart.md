# NEXO Fabric -- Quickstart Guide

Get up and running with NEXO Fabric in under 10 minutes.

## Prerequisites

| Tool       | Version | Purpose                                  |
|------------|---------|------------------------------------------|
| Docker     | 24+     | Run Fabric services (Postgres, Redis...) |
| Java       | 21+     | Build and run Fabric Core                |
| Node.js    | 20+     | (Optional) Frontend / JS SDK             |
| Python     | 3.11+   | (Optional) Python SDK                    |

## 1. Clone and Start Infrastructure

```bash
git clone https://github.com/nexoai/nexo-fabric.git
cd nexo-fabric

# Copy environment template
cp .env.example .env
# Edit .env and set POSTGRES_PASSWORD, JWT_SECRET

# Start all services
docker compose -f docker/docker-compose.dev.yml up -d
```

Wait for the health checks to pass (about 15 seconds):

```bash
docker compose -f docker/docker-compose.dev.yml ps
```

## 2. Register a Tenant

```bash
curl -s -X POST http://localhost:8081/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{
    "email": "admin@example.com",
    "password": "securePassword123",
    "tenantName": "my-company"
  }' | jq .
```

Save the returned `token` -- you will need it for every subsequent request.

```bash
export FABRIC_TOKEN="<paste-token-here>"
```

## 3. Install the SDK

**JavaScript / TypeScript:**

```bash
npm install @nexoai/fabric
```

**Python:**

```bash
pip install nexo-fabric
```

## 4. Create an Object Type (Schema)

Define the shape of the data you want to manage.

```typescript
import { FabricClient } from '@nexoai/fabric'

const fabric = new FabricClient({
  baseUrl: 'http://localhost:8081',
  token: process.env.FABRIC_TOKEN,
})

await fabric.objectTypes.create({
  apiName: 'Employee',
  displayName: 'Employee',
  description: 'Company employee record',
  properties: [
    { apiName: 'fullName',   dataType: 'STRING',  isRequired: true },
    { apiName: 'email',      dataType: 'STRING',  isRequired: true },
    { apiName: 'department', dataType: 'STRING' },
    { apiName: 'startDate',  dataType: 'DATE' },
  ],
})
```

## 5. Upsert Objects

Push data into the ontology. Fabric handles deduplication via `externalId`.

```typescript
await fabric.objects.upsert('Employee', [
  {
    externalId: 'emp-001',
    properties: {
      fullName: 'Alice Nguyen',
      email: 'alice@example.com',
      department: 'Engineering',
      startDate: '2023-06-15',
    },
  },
  {
    externalId: 'emp-002',
    properties: {
      fullName: 'Bob Fischer',
      email: 'bob@example.com',
      department: 'Marketing',
      startDate: '2024-01-10',
    },
  },
])
```

## 6. Semantic Search

Find objects by meaning, not just keywords.

```typescript
const results = await fabric.search.semantic({
  query: 'engineers who joined recently',
  objectType: 'Employee',
  limit: 5,
})

console.log(results)
// [{ object: { fullName: 'Alice Nguyen', ... }, similarity: 0.91 }, ...]
```

## 7. Agent Query (Natural Language)

Ask questions in plain English (or German). The agent plans and executes the query.

```typescript
const answer = await fabric.agent.ask(
  'How many employees are in the Engineering department?'
)

console.log(answer.message)
// "There are 1 employee in Engineering."
console.log(answer.toolCalls)
// [{ tool: 'searchObjects', resultSummary: '1 result' }]
```

## 8. GraphQL Playground

Open `http://localhost:8081/graphiql` in your browser to explore the full API interactively.

Example query:

```graphql
{
  getAllObjectTypes {
    id
    apiName
    displayName
    properties {
      apiName
      dataType
    }
  }
}
```

## Next Steps

- Read the [Core Concepts](./concepts.md) to understand the data model
- Browse the [API Reference](./api-reference.md) for all endpoints
- Set up [Self-Hosting](./self-hosting.md) for production deployments
- Connect external data sources via the Connector framework

## Troubleshooting

| Symptom                         | Fix                                                        |
|---------------------------------|------------------------------------------------------------|
| `Connection refused` on 8081    | Ensure `docker compose up -d` completed; check logs        |
| `401 Unauthorized`              | Token expired or missing -- re-register or re-login        |
| `ObjectType not found`          | Create the object type before upserting objects             |
| Semantic search returns nothing | Objects must be indexed; wait a few seconds after upsert    |
