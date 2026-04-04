package ch.nexoai.fabric.core.agent;

import com.fasterxml.jackson.databind.JsonNode;
import ch.nexoai.fabric.adapters.out.persistence.repository.JpaObjectTypeRepository;
import ch.nexoai.fabric.core.agent.llm.LlmToolDefinition;
import ch.nexoai.fabric.core.ml.SemanticSearchService;
import ch.nexoai.fabric.core.service.object.OntologyObjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Ontology-specific tools the AI agent can invoke.
 * Each tool returns a structured result the agent can interpret.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OntologyAgentTools {

    private final OntologyObjectService objectService;
    private final SemanticSearchService semanticSearchService;
    private final JpaObjectTypeRepository objectTypeRepository;

    /**
     * Returns tool definitions for LLM function-calling.
     */
    public List<LlmToolDefinition> getToolDefinitions() {
        return List.of(
                new LlmToolDefinition("getOntologySchema",
                        "Get all object types in the ontology with their properties. Use this to understand what data exists.",
                        Map.of("type", "object", "properties", Map.of())),
                new LlmToolDefinition("searchObjects",
                        "Search for objects of a given type. Supports semantic/similarity search.",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "objectType", Map.of("type", "string", "description", "The API name of the object type to search"),
                                        "query", Map.of("type", "string", "description", "Search query text"),
                                        "limit", Map.of("type", "integer", "description", "Max results to return (default 10)")
                                ), "required", List.of("objectType", "query"))),
                new LlmToolDefinition("traverseLinks",
                        "Traverse relationships from a specific object to find linked objects.",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "objectId", Map.of("type", "string", "description", "UUID of the source object"),
                                        "linkType", Map.of("type", "string", "description", "API name of the link type to traverse"),
                                        "depth", Map.of("type", "integer", "description", "How many levels deep to traverse (default 1)")
                                ), "required", List.of("objectId", "linkType"))),
                new LlmToolDefinition("aggregateObjects",
                        "Compute aggregations (COUNT, SUM, AVG) on objects of a given type.",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "objectType", Map.of("type", "string", "description", "The API name of the object type"),
                                        "operation", Map.of("type", "string", "enum", List.of("COUNT", "SUM", "AVG"), "description", "Aggregation operation"),
                                        "property", Map.of("type", "string", "description", "Property name for SUM/AVG (ignored for COUNT)")
                                ), "required", List.of("objectType", "operation")))
        );
    }

    /**
     * Execute a tool by name with the given arguments. Returns the result as a map.
     */
    public Map<String, Object> executeTool(String toolName, Map<String, Object> args) {
        return switch (toolName) {
            case "getOntologySchema" -> getOntologySchema();
            case "searchObjects" -> searchObjects(
                    (String) args.get("objectType"),
                    (String) args.get("query"),
                    args.containsKey("limit") ? ((Number) args.get("limit")).intValue() : 10);
            case "traverseLinks" -> traverseLinks(
                    (String) args.get("objectId"),
                    (String) args.get("linkType"),
                    args.containsKey("depth") ? ((Number) args.get("depth")).intValue() : 1);
            case "aggregateObjects" -> aggregateObjects(
                    (String) args.get("objectType"),
                    (String) args.get("operation"),
                    args.containsKey("property") ? (String) args.get("property") : "id");
            default -> Map.of("error", "Unknown tool: " + toolName);
        };
    }

    public Map<String, Object> getOntologySchema() {
        var types = objectTypeRepository.findByIsActiveTrue();
        List<Map<String, Object>> schema = types.stream().map(ot -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("apiName", ot.getApiName());
            m.put("displayName", ot.getDisplayName());
            m.put("propertyCount", ot.getProperties().size());
            m.put("properties", ot.getProperties().stream()
                    .map(p -> p.getApiName() + ":" + p.getDataType()).toList());
            return m;
        }).toList();
        return Map.of("objectTypes", schema, "totalTypes", schema.size());
    }

    public Map<String, Object> searchObjects(String objectType, String query, int limit) {
        try {
            var results = semanticSearchService.search(query, objectType, limit, 0.0f);
            return Map.of(
                    "results", results.stream().map(r -> Map.of(
                            "id", r.id().toString(),
                            "similarity", r.similarity(),
                            "properties", r.properties()
                    )).toList(),
                    "count", results.size(),
                    "tool", "searchObjects"
            );
        } catch (Exception e) {
            // Fallback to regular search
            var page = objectService.searchObjects(objectType, limit, null);
            return Map.of(
                    "results", page.items().stream().map(o -> Map.of(
                            "id", o.getId().toString(),
                            "properties", o.getProperties()
                    )).toList(),
                    "count", page.totalCount(),
                    "tool", "searchObjects"
            );
        }
    }

    public Map<String, Object> traverseLinks(String objectId, String linkType, int depth) {
        var objects = objectService.traverseLinks(UUID.fromString(objectId), linkType, depth);
        return Map.of(
                "linkedObjects", objects.stream().map(o -> Map.of(
                        "id", o.getId().toString(),
                        "objectType", o.getObjectTypeName(),
                        "properties", o.getProperties()
                )).toList(),
                "count", objects.size(),
                "tool", "traverseLinks"
        );
    }

    public Map<String, Object> aggregateObjects(String objectType, String operation, String property) {
        var page = objectService.searchObjects(objectType, 1000, null);
        var items = page.items();

        return switch (operation.toUpperCase()) {
            case "COUNT" -> Map.of("result", items.size(), "operation", "COUNT", "objectType", objectType);
            case "SUM" -> {
                double sum = items.stream()
                        .mapToDouble(o -> {
                            JsonNode val = o.getProperties().get(property);
                            return val != null && val.isNumber() ? val.asDouble() : 0;
                        }).sum();
                yield Map.of("result", sum, "operation", "SUM", "property", property);
            }
            case "AVG" -> {
                var values = items.stream()
                        .map(o -> o.getProperties().get(property))
                        .filter(v -> v != null && v.isNumber())
                        .mapToDouble(JsonNode::asDouble).toArray();
                double avg = values.length > 0 ? Arrays.stream(values).average().orElse(0) : 0;
                yield Map.of("result", avg, "operation", "AVG", "property", property, "count", values.length);
            }
            default -> Map.of("error", "Unknown operation: " + operation);
        };
    }
}
