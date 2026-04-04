package ch.nexoai.fabric.query;

import java.util.List;

public record QueryPlan(String originalQuery, List<QueryStep> steps) {
    public record QueryStep(String type, java.util.Map<String, Object> params) {}
}
