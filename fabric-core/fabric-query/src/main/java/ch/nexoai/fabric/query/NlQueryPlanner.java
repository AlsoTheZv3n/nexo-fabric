package ch.nexoai.fabric.query;

import java.util.*;

public class NlQueryPlanner {
    public QueryPlan plan(String naturalLanguageQuery, List<String> availableTypes) {
        String lower = naturalLanguageQuery.toLowerCase();
        List<QueryPlan.QueryStep> steps = new ArrayList<>();

        // Simple keyword-based planning (replace with LLM in production)
        String objectType = availableTypes.isEmpty() ? null : availableTypes.get(0);
        for (String type : availableTypes) {
            if (lower.contains(type.toLowerCase())) { objectType = type; break; }
        }

        if (lower.contains("how many") || lower.contains("count") || lower.contains("wie viele")) {
            steps.add(new QueryPlan.QueryStep("AGGREGATE", Map.of("objectType", objectType != null ? objectType : "", "operation", "COUNT")));
        } else if (lower.contains("find") || lower.contains("search") || lower.contains("suche") || lower.contains("show")) {
            steps.add(new QueryPlan.QueryStep("SEMANTIC_SEARCH", Map.of("objectType", objectType != null ? objectType : "", "query", naturalLanguageQuery, "limit", 10)));
        } else {
            steps.add(new QueryPlan.QueryStep("SEMANTIC_SEARCH", Map.of("objectType", objectType != null ? objectType : "", "query", naturalLanguageQuery, "limit", 5)));
        }

        return new QueryPlan(naturalLanguageQuery, steps);
    }
}
