package ch.nexoai.fabric.events;

import ch.nexoai.fabric.engine.model.FabricEvent;
import com.fasterxml.jackson.databind.JsonNode;

public interface EventInterpreter {
    String getSourceSystem();
    boolean canHandle(String eventType);
    FabricEvent interpret(String eventType, JsonNode rawPayload);
}
