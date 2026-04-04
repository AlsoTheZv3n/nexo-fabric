package ch.nexoai.fabric.events.interpreter;

import ch.nexoai.fabric.engine.model.FabricEvent;
import ch.nexoai.fabric.events.EventInterpreter;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;

@Component
public class StripeEventInterpreter implements EventInterpreter {

    private static final String SOURCE_SYSTEM = "stripe";

    private static final Set<String> SUPPORTED_EVENT_TYPES = Set.of(
            "customer.created",
            "customer.updated",
            "invoice.created",
            "invoice.paid",
            "invoice.payment_failed",
            "subscription.created",
            "subscription.updated",
            "subscription.deleted"
    );

    @Override
    public String getSourceSystem() {
        return SOURCE_SYSTEM;
    }

    @Override
    public boolean canHandle(String eventType) {
        return SUPPORTED_EVENT_TYPES.contains(eventType);
    }

    @Override
    public FabricEvent interpret(String eventType, JsonNode rawPayload) {
        JsonNode dataObject = rawPayload.path("data").path("object");
        String externalId = dataObject.has("id") ? dataObject.get("id").asText() : null;

        return new FabricEvent(
                SOURCE_SYSTEM,
                eventType,
                externalId,
                dataObject,
                Instant.now()
        );
    }
}
