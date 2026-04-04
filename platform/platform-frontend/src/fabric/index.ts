// Fabric SDK singleton for Platform frontend

// Lightweight inline SDK (avoids npm dependency for demo)
export class FabricSDK {
  private baseUrl: string
  private headers: Record<string, string>

  constructor(baseUrl: string = '', apiKey?: string) {
    this.baseUrl = baseUrl
    this.headers = { 'Content-Type': 'application/json' }
    if (apiKey) this.headers['Authorization'] = `Bearer ${apiKey}`
  }

  async listObjectTypes() {
    return this.gql('{ getAllObjectTypes { id apiName displayName properties { apiName dataType } } }')
      .then(d => d.getAllObjectTypes)
  }

  async searchObjects(objectType: string, limit = 20) {
    return this.gql(
      `query($t:String!,$p:PaginationInput){searchObjects(objectType:$t,pagination:$p){items{id objectType properties createdAt}totalCount hasNextPage}}`,
      { t: objectType, p: { limit } }
    ).then(d => d.searchObjects)
  }

  async ask(message: string, sessionId?: string) {
    return this.gql(
      `mutation($m:String!,$s:ID){agentChat(message:$m,sessionId:$s){message sessionId toolCalls{tool resultSummary}}}`,
      { m: message, s: sessionId }
    ).then(d => d.agentChat)
  }

  private async gql(query: string, variables?: Record<string, unknown>) {
    const res = await fetch(`${this.baseUrl}/graphql`, {
      method: 'POST', headers: this.headers,
      body: JSON.stringify({ query, variables })
    })
    const json = await res.json()
    if (json.errors) throw new Error(json.errors[0].message)
    return json.data
  }
}

export const fabric = new FabricSDK()

// Re-export as FabricClient for compatibility
export { FabricSDK as FabricClient }
