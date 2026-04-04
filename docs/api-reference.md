# NEXO Fabric -- API Reference

All endpoints require a valid JWT token in the `Authorization: Bearer <token>` header
unless noted otherwise.

Base URL: `http://localhost:8081` (default dev port)

---

## Authentication

| Method | Endpoint                   | Description                              |
|--------|----------------------------|------------------------------------------|
| POST   | `/api/auth/register`       | Register a new tenant and admin user     |
| POST   | `/api/auth/login`          | Authenticate and receive a JWT token     |
| POST   | `/api/auth/refresh`        | Refresh an expiring token                |

### POST /api/auth/register

```json
{ "email": "admin@co.com", "password": "secret", "tenantName": "my-co" }
```

Response: `{ "token": "eyJ...", "tenantId": "uuid" }`

### POST /api/auth/login

```json
{ "email": "admin@co.com", "password": "secret" }
```

Response: `{ "token": "eyJ..." }`

---

## Schema (Object Types)

| Method | Endpoint                         | Description                        |
|--------|----------------------------------|------------------------------------|
| GET    | `/api/object-types`              | List all object types              |
| GET    | `/api/object-types/{apiName}`    | Get a single object type           |
| POST   | `/api/object-types`              | Create a new object type           |
| PUT    | `/api/object-types/{apiName}`    | Update an object type              |
| DELETE | `/api/object-types/{apiName}`    | Delete an object type              |

### POST /api/object-types

```json
{
  "apiName": "Employee",
  "displayName": "Employee",
  "properties": [
    { "apiName": "fullName", "dataType": "STRING", "isRequired": true }
  ]
}
```

---

## Objects

| Method | Endpoint                                  | Description                           |
|--------|-------------------------------------------|---------------------------------------|
| GET    | `/api/objects/{objectType}`               | List objects (paginated)              |
| GET    | `/api/objects/{objectType}/{id}`          | Get object by ID                      |
| POST   | `/api/objects/{objectType}`               | Create a single object                |
| PUT    | `/api/objects/{objectType}/{id}`          | Update object properties              |
| DELETE | `/api/objects/{objectType}/{id}`          | Delete an object                      |
| POST   | `/api/objects/{objectType}/upsert`        | Batch upsert (create or update)       |
| POST   | `/api/objects/{objectType}/upsert/stream` | Streaming batch upsert (NDJSON)       |

### POST /api/objects/{objectType}/upsert

```json
{
  "objects": [
    { "externalId": "emp-001", "properties": { "fullName": "Alice" } }
  ]
}
```

---

## Search

| Method | Endpoint                     | Description                              |
|--------|------------------------------|------------------------------------------|
| POST   | `/api/search/semantic`       | Vector similarity search                 |
| POST   | `/api/search/filter`         | Structured property-based filtering      |

### POST /api/search/semantic

```json
{ "query": "recent engineering hires", "objectType": "Employee", "limit": 10 }
```

Response: `{ "results": [{ "object": {...}, "similarity": 0.93 }] }`

---

## Events

| Method | Endpoint                     | Description                              |
|--------|------------------------------|------------------------------------------|
| POST   | `/api/events`                | Ingest an external event                 |
| GET    | `/api/events`                | List events (paginated, filterable)      |
| GET    | `/api/events/{id}`           | Get a single event by ID                 |

### POST /api/events

```json
{
  "eventType": "CONTRACT_SIGNED",
  "objectType": "Contract",
  "objectId": "uuid",
  "payload": { "signedBy": "Alice", "date": "2025-03-15" }
}
```

---

## Agent

| Method | Endpoint                     | Description                              |
|--------|------------------------------|------------------------------------------|
| POST   | `/api/agent/ask`             | Send a natural-language question         |

### POST /api/agent/ask

```json
{ "message": "How many employees are in Engineering?", "sessionId": "optional" }
```

Response:
```json
{
  "message": "There are 12 employees in Engineering.",
  "sessionId": "sess-abc",
  "toolCalls": [{ "tool": "searchObjects", "resultSummary": "12 results" }]
}
```

---

## Connectors & Data Sources

| Method | Endpoint                           | Description                          |
|--------|------------------------------------|--------------------------------------|
| GET    | `/api/data-sources`                | List configured data sources         |
| POST   | `/api/data-sources`                | Register a new data source           |
| PUT    | `/api/data-sources/{id}`           | Update data source config            |
| DELETE | `/api/data-sources/{id}`           | Remove a data source                 |
| POST   | `/api/data-sources/{id}/sync`      | Trigger a manual sync                |
| GET    | `/api/data-sources/{id}/sync-logs` | View sync history                    |

---

## Action Log (Audit)

| Method | Endpoint                     | Description                              |
|--------|------------------------------|------------------------------------------|
| GET    | `/api/audit-log`             | List action log entries (paginated)      |
| GET    | `/api/audit-log/{id}`        | Get a single audit entry                 |

---

## GraphQL

| Method | Endpoint     | Description                                    |
|--------|--------------|------------------------------------------------|
| POST   | `/graphql`   | GraphQL endpoint (queries + mutations)         |
| GET    | `/graphiql`  | Interactive GraphQL IDE (dev only)             |

### Key Queries

```graphql
# List object types
{ getAllObjectTypes { id apiName displayName properties { apiName dataType } } }

# Search objects
query { searchObjects(objectType: "Employee", pagination: { limit: 20 }) {
  items { id objectType properties createdAt } totalCount hasNextPage
}}

# Semantic search
query { semanticSearch(query: "senior engineers", objectType: "Employee", limit: 5) {
  object { id properties } similarity
}}
```

### Key Mutations

```graphql
# Create object type
mutation { createObjectType(input: { apiName: "Employee", displayName: "Employee",
  properties: [{ apiName: "fullName", dataType: "STRING" }] }) { id } }

# Upsert objects
mutation { upsertObjects(objectType: "Employee", objects: [
  { externalId: "e1", properties: "{\"fullName\":\"Alice\"}" }
]) { id } }

# Agent chat
mutation { agentChat(message: "Show open contracts", sessionId: "s1") {
  message sessionId toolCalls { tool resultSummary }
}}
```
