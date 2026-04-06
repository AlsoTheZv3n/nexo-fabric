package ch.nexoai.fabric.core.functions;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class FunctionDefinition {
    private final UUID id;
    private final UUID tenantId;
    private final String apiName;
    private final String displayName;
    private final String description;
    private final String language;
    private final String sourceCode;
    private final String inputType;
    private final String outputType;
    private final boolean isActive;
    private final Instant createdAt;
}
