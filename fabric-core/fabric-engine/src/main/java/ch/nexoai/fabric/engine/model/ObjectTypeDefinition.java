package ch.nexoai.fabric.engine.model;

import java.util.List;
import java.util.UUID;

public record ObjectTypeDefinition(
        UUID id,
        String apiName,
        String displayName,
        String description,
        List<PropertyDefinition> properties
) {}
