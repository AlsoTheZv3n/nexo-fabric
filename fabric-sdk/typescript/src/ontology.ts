/**
 * Runtime ontology client — provides a Proxy-based dynamic API
 * that mirrors the generated TypeScript types.
 *
 * After running `nexo-fabric generate`, users get full type safety
 * via the generated `nexo-fabric.d.ts` declaration file. At runtime
 * this Proxy resolves the per-ObjectType methods.
 */

import type { FabricClient } from './client'

export interface ObjectQueryOptions {
  filter?: Record<string, unknown>
  limit?: number
  offset?: number
}

export class OntologyProxy {
  constructor(private readonly client: FabricClient) {}

  build(objectType: string) {
    return {
      search: async (filter?: Record<string, unknown>, limit = 50) => {
        const page = await this.client.searchObjects(objectType, limit)
        const items = (page.items ?? []).filter((o: any) => matchFilter(o.properties, filter))
        return { items: items.map((i: any) => ({ id: i.id, ...i.properties })), total: items.length }
      },

      get: async (id: string) => {
        const page = await this.client.searchObjects(objectType, 1000)
        const found = (page.items ?? []).find((o: any) => o.id === id)
        return found ? { id: found.id, ...found.properties } : null
      },

      create: async (properties: Record<string, unknown>) => {
        const created = await this.client.createObject(objectType, properties)
        return { id: created.id, ...created.properties }
      },

      ask: async (query: string, limit = 10) => {
        const results = await this.client.semanticSearch(query, objectType, limit)
        return results.map((r: any) => ({
          object: { id: r.object.id, ...r.object.properties },
          similarity: r.similarity,
        }))
      },
    }
  }
}

function matchFilter(props: Record<string, unknown>, filter?: Record<string, unknown>): boolean {
  if (!filter) return true
  for (const [key, condition] of Object.entries(filter)) {
    const value = props[key]
    if (condition === null || typeof condition !== 'object') {
      if (value !== condition) return false
    } else {
      const c = condition as Record<string, unknown>
      if ('eq' in c && value !== c.eq) return false
      if ('neq' in c && value === c.neq) return false
      if ('gt' in c && !(typeof value === 'number' && value > (c.gt as number))) return false
      if ('gte' in c && !(typeof value === 'number' && value >= (c.gte as number))) return false
      if ('lt' in c && !(typeof value === 'number' && value < (c.lt as number))) return false
      if ('lte' in c && !(typeof value === 'number' && value <= (c.lte as number))) return false
      if ('contains' in c && !String(value ?? '').includes(c.contains as string)) return false
      if ('in' in c && Array.isArray(c.in) && !c.in.includes(value)) return false
    }
  }
  return true
}

export function createOntology(client: FabricClient): Record<string, ReturnType<OntologyProxy['build']>> {
  const proxy = new OntologyProxy(client)
  return new Proxy({} as Record<string, ReturnType<OntologyProxy['build']>>, {
    get(_target, prop: string) {
      return proxy.build(prop)
    },
  })
}
