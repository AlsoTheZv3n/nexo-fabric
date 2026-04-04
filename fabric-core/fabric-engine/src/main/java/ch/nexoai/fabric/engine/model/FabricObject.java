package ch.nexoai.fabric.engine.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record FabricObject(
        UUID id,
        String objectType,
        JsonNode properties,
        String externalId,
        Instant createdAt,
        Instant updatedAt
) {}
