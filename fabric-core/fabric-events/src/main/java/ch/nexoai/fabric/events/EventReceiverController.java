package ch.nexoai.fabric.events;

import ch.nexoai.fabric.engine.model.FabricEvent;
import ch.nexoai.fabric.engine.model.FabricObject;
import ch.nexoai.fabric.engine.port.in.ProcessEventUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/events")
public class EventReceiverController {

    private static final Logger log = LoggerFactory.getLogger(EventReceiverController.class);

    private final EventRouter eventRouter;
    private final ProcessEventUseCase processEventUseCase;

    @Value("${fabric.webhook.secret:}")
    private String webhookSecret;

    public EventReceiverController(EventRouter eventRouter, ProcessEventUseCase processEventUseCase) {
        this.eventRouter = eventRouter;
        this.processEventUseCase = processEventUseCase;
    }

    @PostMapping("/inbound/{sourceSystem}")
    public ResponseEntity<?> receiveEvent(
            @PathVariable String sourceSystem,
            @RequestHeader(value = "X-Webhook-Secret", required = false) String providedSecret,
            @RequestParam(value = "eventType", defaultValue = "unknown") String eventType,
            @RequestBody JsonNode rawPayload) {

        log.info("Received event from source={}, type={}", sourceSystem, eventType);

        if (!webhookSecret.isEmpty() && !webhookSecret.equals(providedSecret)) {
            log.warn("Invalid webhook secret from source={}", sourceSystem);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid webhook secret"));
        }

        try {
            FabricEvent event = eventRouter.route(sourceSystem, eventType, rawPayload);
            FabricObject result = processEventUseCase.processEvent(event);

            return ResponseEntity.ok(Map.of(
                    "status", "accepted",
                    "objectId", result.id().toString(),
                    "objectType", result.objectType()
            ));
        } catch (Exception e) {
            log.error("Failed to process event from source={}: {}", sourceSystem, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process event: " + e.getMessage()));
        }
    }
}
