package ch.nexoai.fabric.engine.model;

import java.util.UUID;

public record FabricLink(
        UUID id,
        UUID sourceId,
        UUID targetId,
        String linkType
) {}
