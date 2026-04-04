package ch.nexoai.fabric.events.interpreter;

import ch.nexoai.fabric.engine.model.FabricEvent;
import ch.nexoai.fabric.events.EventInterpreter;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class GenericEventInterpreter implements EventInterpreter {

    private static final String SOURCE_SYSTEM = "generic";

    @Override
    public String getSourceSystem() {
        return SOURCE_SYSTEM;
    }

    @Override
    public boolean canHandle(String eventType) {
        return true;
    }

    @Override
    public FabricEvent interpret(String eventType, JsonNode rawPayload) {
        String externalId = rawPayload.has("id") ? rawPayload.get("id").asText() : null;

        return new FabricEvent(
                SOURCE_SYSTEM,
                eventType,
                externalId,
                rawPayload,
                Instant.now()
        );
    }
}
