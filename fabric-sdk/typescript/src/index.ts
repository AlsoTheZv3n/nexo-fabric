export { FabricClient } from './client'
export { OntologyProxy, createOntology } from './ontology'
export type { ObjectQueryOptions } from './ontology'
export type { FabricConfig, FabricObject, ObjectType, SearchResult, AgentResponse, ObjectPage, PropertyDef } from './types'

// Connector SDK (re-exported for convenience; also available at @nexoai/fabric/connector-sdk)
export {
  createConnectorHandler,
  verifySignature,
} from './connector-sdk'
export type {
  ConnectorRecord,
  ConnectorRequest,
  ConnectorDefinition,
  SchemaColumn,
} from './connector-sdk'
