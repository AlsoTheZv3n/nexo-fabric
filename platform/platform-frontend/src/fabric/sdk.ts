// Re-export types for platform use
export type {
  OntologyObject as FabricObject,
  ObjectTypeSchema as ObjectType,
  ObjectPage as SearchResult,
} from '../types'

export type AgentResponse = {
  message: string
  sessionId: string
  toolCalls: { tool: string; resultSummary: string }[]
}

export { FabricClient } from './index'
