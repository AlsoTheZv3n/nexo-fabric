import { FabricConfig, FabricObject, ObjectType, SearchResult, AgentResponse, ObjectPage } from './types'

export class FabricClient {
  private baseUrl: string
  private headers: Record<string, string>

  constructor(config: FabricConfig) {
    this.baseUrl = config.baseUrl.replace(/\/$/, '')
    this.headers = { 'Content-Type': 'application/json' }
    if (config.apiKey) this.headers['Authorization'] = `Bearer ${config.apiKey}`
  }

  // Schema
  async listObjectTypes(): Promise<ObjectType[]> {
    return this.graphql<{ getAllObjectTypes: ObjectType[] }>(
      '{ getAllObjectTypes { id apiName displayName description properties { apiName displayName dataType isPrimaryKey isRequired } } }'
    ).then(d => d.getAllObjectTypes)
  }

  // Objects
  async searchObjects(objectType: string, limit = 20): Promise<ObjectPage> {
    return this.graphql<{ searchObjects: ObjectPage }>(
      `query($type: String!, $pagination: PaginationInput) { searchObjects(objectType: $type, pagination: $pagination) { items { id objectType properties createdAt } totalCount hasNextPage } }`,
      { type: objectType, pagination: { limit } }
    ).then(d => d.searchObjects)
  }

  async createObject(objectType: string, properties: Record<string, unknown>): Promise<FabricObject> {
    return this.graphql<{ createObject: FabricObject }>(
      `mutation($type: String!, $props: JSON!) { createObject(objectType: $type, properties: $props) { id objectType properties createdAt } }`,
      { type: objectType, props: properties }
    ).then(d => d.createObject)
  }

  // Semantic Search
  async semanticSearch(query: string, objectType: string, limit = 10): Promise<SearchResult[]> {
    return this.graphql<{ semanticSearch: SearchResult[] }>(
      `query($q: String!, $type: String!, $limit: Int) { semanticSearch(query: $q, objectType: $type, limit: $limit) { similarity object { id objectType properties } } }`,
      { q: query, type: objectType, limit }
    ).then(d => d.semanticSearch)
  }

  // Agent
  async ask(message: string, sessionId?: string): Promise<AgentResponse> {
    return this.graphql<{ agentChat: AgentResponse }>(
      `mutation($msg: String!, $sid: ID) { agentChat(message: $msg, sessionId: $sid) { message sessionId toolCalls { tool resultSummary } } }`,
      { msg: message, sid: sessionId }
    ).then(d => d.agentChat)
  }

  // Events
  async sendEvent(sourceSystem: string, payload: Record<string, unknown>): Promise<unknown> {
    const res = await fetch(`${this.baseUrl}/api/v1/events/inbound/${sourceSystem}`, {
      method: 'POST', headers: this.headers, body: JSON.stringify(payload)
    })
    return res.json()
  }

  // GraphQL helper
  private async graphql<T>(query: string, variables?: Record<string, unknown>): Promise<T> {
    const res = await fetch(`${this.baseUrl}/graphql`, {
      method: 'POST',
      headers: this.headers,
      body: JSON.stringify({ query, variables })
    })
    const json = await res.json()
    if (json.errors) throw new Error(json.errors[0].message)
    return json.data as T
  }
}
