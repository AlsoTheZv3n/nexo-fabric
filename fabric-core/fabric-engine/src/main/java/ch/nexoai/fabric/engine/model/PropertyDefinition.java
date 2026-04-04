package ch.nexoai.fabric.engine.model;

public record PropertyDefinition(
        String apiName,
        String displayName,
        String dataType,
        boolean isPrimaryKey,
        boolean isRequired
) {}
