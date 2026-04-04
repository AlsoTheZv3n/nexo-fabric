package ch.nexoai.fabric.engine.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record FabricEvent(
        String sourceSystem,
        String eventType,
        String externalId,
        JsonNode payload,
        Instant receivedAt
) {}
