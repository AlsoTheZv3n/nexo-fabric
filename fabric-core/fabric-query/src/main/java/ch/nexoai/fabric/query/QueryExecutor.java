package ch.nexoai.fabric.query;

import ch.nexoai.fabric.engine.model.*;
import ch.nexoai.fabric.engine.port.in.*;

import java.util.*;

public class QueryExecutor {
    private final QueryObjectsUseCase queryObjects;
    private final SemanticSearchUseCase semanticSearch;

    public QueryExecutor(QueryObjectsUseCase queryObjects, SemanticSearchUseCase semanticSearch) {
        this.queryObjects = queryObjects;
        this.semanticSearch = semanticSearch;
    }

    public Map<String, Object> execute(QueryPlan plan) {
        Map<String, Object> results = new LinkedHashMap<>();
        results.put("query", plan.originalQuery());
        List<Map<String, Object>> stepResults = new ArrayList<>();

        for (QueryPlan.QueryStep step : plan.steps()) {
            Map<String, Object> result = executeStep(step);
            stepResults.add(result);
        }

        results.put("steps", stepResults);
        return results;
    }

    private Map<String, Object> executeStep(QueryPlan.QueryStep step) {
        return switch (step.type()) {
            case "SEMANTIC_SEARCH" -> {
                String objectType = (String) step.params().get("objectType");
                String query = (String) step.params().get("query");
                int limit = step.params().containsKey("limit") ? (int) step.params().get("limit") : 10;
                var results = semanticSearch.semanticSearch(query, objectType, limit, 0.0f);
                yield Map.of("type", "SEMANTIC_SEARCH", "count", results.size(), "results", results);
            }
            case "AGGREGATE" -> {
                String objectType = (String) step.params().get("objectType");
                var objects = queryObjects.search(objectType, 10000);
                yield Map.of("type", "AGGREGATE", "operation", step.params().get("operation"), "count", objects.size());
            }
            default -> Map.of("type", step.type(), "error", "Unknown step type");
        };
    }
}
