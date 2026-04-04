package ch.nexoai.fabric.core.connector;

import java.util.Map;

public record RawRecord(
        Map<String, Object> fields,
        String externalId
) {}
