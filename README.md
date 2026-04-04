# NEXO Fabric

NEXO Fabric is the open-source semantic integration layer that unifies data across systems using ontology-driven mapping, real-time connectors, and a resolution engine.

## Two-Product Architecture

This monorepo supports a two-product strategy:

1. **NEXO Fabric** (open-source) -- the core integration engine that provides semantic data mapping, event-driven connectors, entity resolution, and a query layer. Found under `fabric-core/`.

2. **NEXO Platform** (commercial) -- a full-featured SaaS application built on top of Fabric, adding multi-tenant management, a web UI, billing, analytics, and enterprise features. Found under `platform/`.

## Repository Structure

```
nexo-fabric/
  fabric-core/          Core engine modules (open-source)
    fabric-api/         REST + GraphQL API (Spring Boot)
    fabric-engine/      Semantic mapping engine
    fabric-events/      Event bus and CDC
    fabric-resolution/  Entity resolution
    fabric-query/       Unified query layer
  fabric-sdk/           Client SDKs (TypeScript, Python, Java)
  fabric-connectors/    Integration connectors
  platform/             Commercial platform
    platform-backend/   Platform API extensions
    platform-frontend/  React web application
  docs/                 Documentation
  docker/               Docker and Compose files
```

## Getting Started

### Prerequisites

- Java 21
- Maven 3.9+
- Node.js 20+
- Docker & Docker Compose

### Build

```bash
# Build the core API
cd fabric-core/fabric-api
mvn compile

# Run the frontend dev server
cd platform/platform-frontend
npm install
npm run dev
```

## License

See [LICENSE](LICENSE) for details.
