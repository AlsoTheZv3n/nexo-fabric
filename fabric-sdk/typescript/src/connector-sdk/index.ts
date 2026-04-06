/**
 * NEXO Fabric Connector SDK
 *
 * For developers building custom connectors that NEXO Fabric can call
 * to fetch data from external systems.
 *
 * Usage:
 *   import { createConnectorHandler } from '@nexoai/fabric/connector-sdk'
 *
 *   export default createConnectorHandler({
 *     apiName: 'my-connector',
 *     displayName: 'My Custom Connector',
 *     async fetch(config) {
 *       const records = await fetchFromMyApi(config)
 *       return records.map(r => ({ id: r.uuid, properties: r }))
 *     }
 *   })
 *
 * The handler is a Web API Request -> Response function compatible with
 * Cloudflare Workers, Deno, Bun, Next.js Route Handlers, and Hono.
 * For Express/Node compatibility, wrap with `expressAdapter()`.
 */

import { createHmac, timingSafeEqual } from 'crypto'

export interface ConnectorRecord {
  /** Stable external identifier (used as externalId for upserts) */
  id: string
  /** Object properties to upsert into the ontology */
  properties: Record<string, unknown>
}

export interface ConnectorRequest {
  operation: 'FETCH' | 'SCHEMA' | 'TEST'
  config: Record<string, unknown>
  requestId: string
}

export interface SchemaColumn {
  name: string
  type: 'STRING' | 'INTEGER' | 'FLOAT' | 'BOOLEAN' | 'DATETIME' | 'JSON'
  nullable: boolean
}

export interface ConnectorDefinition {
  apiName: string
  displayName: string
  description?: string

  /** Webhook secret used to verify incoming requests from Fabric */
  webhookSecret?: string

  /** Returns all records matching the config */
  fetch(config: Record<string, unknown>): Promise<ConnectorRecord[]>

  /** Returns the column structure (optional) */
  schema?(config: Record<string, unknown>): Promise<SchemaColumn[]>

  /** Tests the connection (optional) */
  test?(config: Record<string, unknown>): Promise<{ success: boolean; message: string }>
}

/**
 * Verify HMAC-SHA256 signature using a constant-time comparison.
 * Protects against timing attacks.
 */
export function verifySignature(payload: string, signature: string | null, secret: string): boolean {
  if (!signature) return false
  const expected = createHmac('sha256', secret).update(payload).digest('hex')
  try {
    return timingSafeEqual(Buffer.from(signature), Buffer.from(expected))
  } catch {
    return false
  }
}

/**
 * Create a Web API request handler for a connector definition.
 *
 * The handler accepts POST requests with a JSON body matching ConnectorRequest
 * and returns the result of the operation as JSON.
 */
export function createConnectorHandler(definition: ConnectorDefinition) {
  return async function handler(req: Request): Promise<Response> {
    if (req.method !== 'POST') {
      return new Response('Method not allowed', { status: 405 })
    }

    const body = await req.text()

    // Verify signature if a secret is configured
    if (definition.webhookSecret) {
      const signature = req.headers.get('X-Fabric-Signature')
      if (!verifySignature(body, signature, definition.webhookSecret)) {
        return new Response('Unauthorized', { status: 401 })
      }
    }

    let request: ConnectorRequest
    try {
      request = JSON.parse(body)
    } catch {
      return new Response('Invalid JSON', { status: 400 })
    }

    try {
      switch (request.operation) {
        case 'FETCH': {
          const records = await definition.fetch(request.config)
          return Response.json({ records, idField: 'id' })
        }
        case 'SCHEMA': {
          if (!definition.schema) return Response.json({ columns: [] })
          const columns = await definition.schema(request.config)
          return Response.json({ columns })
        }
        case 'TEST': {
          if (!definition.test) return Response.json({ success: true, message: 'OK' })
          const result = await definition.test(request.config)
          return Response.json(result)
        }
        default:
          return new Response(`Unknown operation: ${request.operation}`, { status: 400 })
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error)
      return Response.json({ error: message }, { status: 500 })
    }
  }
}
