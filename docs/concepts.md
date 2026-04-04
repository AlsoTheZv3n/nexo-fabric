# NEXO Fabric -- Core Concepts

This document explains the foundational building blocks of NEXO Fabric.

## Objects

An **Object** is the fundamental unit of data in Fabric. Every record stored in the
ontology -- whether it represents an employee, a contract, a machine, or an invoice --
is an Object. Each object has:

- **id** -- a UUID assigned by Fabric
- **objectType** -- the schema it belongs to (e.g. `Employee`)
- **externalId** -- an optional stable identifier from the source system
- **properties** -- a JSON map of field values
- **createdAt / updatedAt** -- automatic timestamps

Objects are immutable in their identity but mutable in their properties; updates create
a new version and emit a change event.

## Object Types

An **Object Type** is a schema definition that describes a category of objects. Think of
it as a table definition (but richer). Each Object Type has:

- **apiName** -- a unique machine-readable identifier (e.g. `Employee`)
- **displayName** -- a human-readable label
- **description** -- optional documentation
- **properties** -- a list of Property Definitions
- **linkTypes** -- relationships to other Object Types

Object Types are tenant-scoped; each tenant manages its own schema.

## Properties

A **Property** defines a single field on an Object Type:

- **apiName** -- field name (e.g. `fullName`)
- **dataType** -- one of `STRING`, `INTEGER`, `DOUBLE`, `BOOLEAN`, `DATE`, `TIMESTAMP`, `JSON`
- **isPrimaryKey** -- marks the natural key for deduplication
- **isRequired** -- enforces non-null on upsert

## Links

A **Link** represents a directed relationship between two objects:

- **sourceObject** -- the object that owns the link
- **targetObject** -- the object being referenced
- **linkType** -- defines the relationship kind and cardinality

Links enable graph-style traversal across the ontology, powering queries like
"find all contracts associated with this customer."

## Events

An **Event** is a timestamped record of something that happened:

- External events pushed via the Event Receiver endpoint
- Internal change-data-capture (CDC) events from connector syncs

Events are stored and can trigger downstream automations (n8n workflows, webhooks).

## Semantic Search

Fabric automatically generates **vector embeddings** for every object. This enables
similarity-based search -- you describe what you are looking for in natural language
and Fabric returns the closest matches ranked by cosine similarity.

Embeddings are computed on upsert and stored in pgvector alongside the object data.

## Agent

The **Agent** is a natural-language interface that sits on top of the query engine.
Users can ask questions like "Show me all open invoices over CHF 10'000" and the
agent will:

1. Parse the intent
2. Build a query plan (semantic search, aggregation, filter)
3. Execute the plan against the ontology
4. Return a human-readable answer plus structured results

The agent is exposed via a `agentChat` GraphQL mutation and the REST `/api/agent/ask`
endpoint.

## Tenants

Fabric is **multi-tenant by design**. Each tenant has:

- Isolated data (objects, schemas, events)
- Its own API credentials
- Independent connector configurations

Row-level security in PostgreSQL ensures strict data isolation. Tenant context is
derived from the JWT token attached to every API request.

## Data Flow Summary

```
Source System  -->  Connector / Event Receiver
                        |
                        v
                  Fabric Engine (validate, embed, store)
                        |
                        v
              PostgreSQL + pgvector
                        |
            +-----------+-----------+
            |                       |
       GraphQL / REST          Agent (NL query)
            |                       |
            v                       v
        Platform UI           Chat Interface
```
