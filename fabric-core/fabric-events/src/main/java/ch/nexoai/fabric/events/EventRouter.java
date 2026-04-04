package ch.nexoai.fabric.events;

import ch.nexoai.fabric.engine.model.FabricEvent;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class EventRouter {

    private static final Logger log = LoggerFactory.getLogger(EventRouter.class);

    private final List<EventInterpreter> interpreters;

    public EventRouter(List<EventInterpreter> interpreters) {
        this.interpreters = interpreters;
    }

    public FabricEvent route(String sourceSystem, String eventType, JsonNode rawPayload) {
        log.debug("Routing event: sourceSystem={}, eventType={}", sourceSystem, eventType);

        Optional<EventInterpreter> matched = interpreters.stream()
                .filter(i -> i.getSourceSystem().equalsIgnoreCase(sourceSystem))
                .filter(i -> i.canHandle(eventType))
                .findFirst();

        if (matched.isEmpty()) {
            log.debug("No specific interpreter found for source={}, falling back to generic", sourceSystem);
            matched = interpreters.stream()
                    .filter(i -> "generic".equalsIgnoreCase(i.getSourceSystem()))
                    .findFirst();
        }

        EventInterpreter interpreter = matched.orElseThrow(() ->
                new IllegalStateException("No event interpreter found for: " + sourceSystem + "/" + eventType));

        return interpreter.interpret(eventType, rawPayload);
    }
}
