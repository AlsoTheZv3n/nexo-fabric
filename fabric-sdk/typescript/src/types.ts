export interface FabricConfig {
  baseUrl: string
  apiKey?: string
  tenantId?: string
}

export interface FabricObject {
  id: string
  objectType: string
  properties: Record<string, unknown>
  createdAt: string
  updatedAt?: string
}

export interface ObjectType {
  id: string
  apiName: string
  displayName: string
  description?: string
  properties: PropertyDef[]
}

export interface PropertyDef {
  apiName: string
  displayName: string
  dataType: string
  isPrimaryKey: boolean
  isRequired: boolean
}

export interface SearchResult {
  object: FabricObject
  similarity: number
}

export interface AgentResponse {
  message: string
  sessionId: string
  toolCalls: { tool: string; resultSummary: string }[]
}

export interface ObjectPage {
  items: FabricObject[]
  totalCount: number
  hasNextPage: boolean
}
