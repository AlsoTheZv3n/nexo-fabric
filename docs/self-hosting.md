# NEXO Fabric -- Self-Hosting Guide

Deploy Fabric on your own infrastructure using Docker Compose.

## System Requirements

| Resource | Minimum   | Recommended |
|----------|-----------|-------------|
| CPU      | 2 cores   | 4+ cores    |
| RAM      | 4 GB      | 8+ GB       |
| Disk     | 20 GB SSD | 50+ GB SSD  |
| OS       | Linux x86_64 (Docker-capable) | Ubuntu 22.04 / Debian 12 |

Software:

- Docker Engine 24+ and Docker Compose v2
- (Optional) Java 21+ if building from source

## Quick Start with Docker Compose

```bash
git clone https://github.com/nexoai/nexo-fabric.git
cd nexo-fabric
cp .env.example .env
```

Edit `.env` with your production values (see Environment Variables below), then:

```bash
docker compose -f docker/docker-compose.dev.yml up -d
```

Verify all services are healthy:

```bash
docker compose -f docker/docker-compose.dev.yml ps
```

## Environment Variables

| Variable              | Required | Default           | Description                              |
|-----------------------|----------|-------------------|------------------------------------------|
| `POSTGRES_DB`         | No       | `nexo_ontology`   | PostgreSQL database name                 |
| `POSTGRES_USER`       | No       | `nexo`            | PostgreSQL user                          |
| `POSTGRES_PASSWORD`   | Yes      | --                | PostgreSQL password                      |
| `JWT_SECRET`          | Yes      | --                | Secret for signing JWT tokens            |
| `SERVER_PORT`         | No       | `8081`            | Port for the backend API                 |
| `CDC_ENABLED`         | No       | `false`           | Enable Debezium CDC connector            |
| `CUSTOMER_DB_HOST`    | No       | `localhost`       | External DB host for CDC                 |
| `CUSTOMER_DB_USER`    | No       | `nexo`            | External DB user for CDC                 |
| `CUSTOMER_DB_PASSWORD`| No       | `nexo_secret`     | External DB password for CDC             |
| `CUSTOMER_DB_NAME`    | No       | `nexo_ontology`   | External DB name for CDC                 |
| `N8N_BASIC_AUTH_USER` | No       | `admin`           | n8n web UI username                      |
| `N8N_BASIC_AUTH_PASSWORD`| Yes   | --                | n8n web UI password                      |
| `N8N_WEBHOOK_SECRET`  | No       | `changeme`        | Shared secret between backend and n8n    |
| `REDIS_HOST`          | No       | `redis`           | Redis hostname (internal Docker network) |

## Services Overview

| Service    | Image                       | Port  | Purpose                         |
|------------|-----------------------------|-------|---------------------------------|
| postgres   | `pgvector/pgvector:pg16`    | 5434  | Primary database + vector store |
| backend    | Custom (Dockerfile)         | 8081  | Fabric API server               |
| n8n        | `n8nio/n8n:latest`          | 5678  | Workflow automation              |
| redis      | `redis:7-alpine`            | 6379  | Caching and CDC message bus     |
| debezium   | `debezium/server:2.5`       | 8083  | CDC connector (optional)        |

## Enabling CDC (Change Data Capture)

To enable the Debezium connector for real-time sync from external databases:

```bash
CDC_ENABLED=true docker compose -f docker/docker-compose.dev.yml --profile cdc up -d
```

## Persistent Storage

Docker volumes are used for data persistence:

- `postgres_data` -- PostgreSQL data directory
- `n8n_data` -- n8n workflows and credentials
- `redis_data` -- Redis AOF persistence

To back up PostgreSQL:

```bash
docker exec nexo-postgres pg_dump -U nexo nexo_ontology > backup.sql
```

## TLS / Reverse Proxy

For production, place a reverse proxy (nginx, Caddy, Traefik) in front of the backend:

```
Client --> TLS (443) --> Reverse Proxy --> Backend (8081)
```

A minimal Caddy example:

```
fabric.example.com {
    reverse_proxy localhost:8081
}
```

## Updating

```bash
git pull origin main
docker compose -f docker/docker-compose.dev.yml up -d --build
```

This rebuilds the backend image and restarts changed services. Data volumes are preserved.
