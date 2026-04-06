package ch.nexoai.fabric.core.lifecycle;

import ch.nexoai.fabric.adapters.out.persistence.entity.LifecycleEntity;
import ch.nexoai.fabric.adapters.out.persistence.entity.ObjectTypeEntity;
import ch.nexoai.fabric.adapters.out.persistence.entity.OntologyObjectEntity;
import ch.nexoai.fabric.adapters.out.persistence.repository.JpaLifecycleRepository;
import ch.nexoai.fabric.adapters.out.persistence.repository.JpaObjectTypeRepository;
import ch.nexoai.fabric.adapters.out.persistence.repository.JpaOntologyObjectRepository;
import ch.nexoai.fabric.core.tenant.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class LifecycleService {

    private final JpaLifecycleRepository lifecycleRepository;
    private final JpaOntologyObjectRepository objectRepository;
    private final JpaObjectTypeRepository objectTypeRepository;
    private final ObjectMapper objectMapper;

    // ─── CRUD ─────────────────────────────────────────────────────────────────

    public LifecycleDefinition upsert(String objectTypeApiName,
                                       LifecycleDefinition definition,
                                       UUID tenantId) {
        validateDefinition(definition);

        LifecycleEntity entity = lifecycleRepository
                .findByObjectTypeApiNameAndTenantId(objectTypeApiName, tenantId)
                .orElseGet(LifecycleEntity::new);

        entity.setTenantId(tenantId);
        entity.setObjectTypeApiName(objectTypeApiName);
        entity.setStateProperty(definition.getStateProperty() != null ? definition.getStateProperty() : "status");
        entity.setDefinition(serializeDefinition(definition));
        entity.setActive(true);

        LifecycleEntity saved = lifecycleRepository.save(entity);
        return parseDefinition(saved);
    }

    @Transactional(readOnly = true)
    public Optional<LifecycleDefinition> findForObjectType(String objectTypeApiName, UUID tenantId) {
        return lifecycleRepository.findByObjectTypeApiNameAndTenantId(objectTypeApiName, tenantId)
                .map(this::parseDefinition);
    }

    @Transactional(readOnly = true)
    public List<LifecycleEntity> findAll(UUID tenantId) {
        return lifecycleRepository.findByTenantId(tenantId);
    }

    public void delete(String objectTypeApiName, UUID tenantId) {
        lifecycleRepository.findByObjectTypeApiNameAndTenantId(objectTypeApiName, tenantId)
                .ifPresent(lifecycleRepository::delete);
    }

    // ─── Transition ───────────────────────────────────────────────────────────

    public TransitionResult transition(UUID objectId, String toState, String performedBy, UUID tenantId) {
        OntologyObjectEntity object = objectRepository.findById(objectId)
                .orElseThrow(() -> new IllegalArgumentException("Object not found: " + objectId));

        ObjectTypeEntity ot = objectTypeRepository.findById(object.getObjectTypeId())
                .orElseThrow(() -> new IllegalStateException("ObjectType not found: " + object.getObjectTypeId()));

        LifecycleDefinition lifecycle = findForObjectType(ot.getApiName(), tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "No lifecycle defined for: " + ot.getApiName()));

        String detectedState = getCurrentState(object, lifecycle);
        final String currentState = detectedState != null ? detectedState : lifecycle.getInitialState();

        if (!lifecycle.canTransition(currentState, toState)) {
            List<String> available = lifecycle.getAvailableTransitions(currentState).stream()
                    .map(LifecycleDefinition.Transition::getTo).toList();
            throw new InvalidTransitionException(
                    "Transition '" + currentState + "' -> '" + toState + "' is not allowed. " +
                    "Available: " + available);
        }

        LifecycleDefinition.Transition transition = lifecycle.getTransitions().stream()
                .filter(t -> t.getFrom().equals(currentState) && t.getTo().equals(toState))
                .findFirst().orElseThrow();

        if (transition.getAllowedRoles() != null && !transition.getAllowedRoles().isEmpty()) {
            String role = TenantContext.getCurrentRole();
            if (!transition.getAllowedRoles().contains(role)) {
                throw new InvalidTransitionException(
                        "Role '" + role + "' cannot perform this transition. Allowed: " + transition.getAllowedRoles());
            }
        }

        // Apply the state change to the JSON properties + dedicated column
        try {
            ObjectNode props = (ObjectNode) parseProperties(object);
            props.put(lifecycle.getStateProperty(), toState);
            object.setProperties(props.toString());
        } catch (Exception e) {
            log.warn("Failed to update properties JSON: {}", e.getMessage());
        }
        object.setCurrentState(toState);
        objectRepository.save(object);

        log.info("[Lifecycle] {} -> {} on {} ({})", currentState, toState, objectId, performedBy);

        return new TransitionResult(objectId, currentState, toState, true);
    }

    @Transactional(readOnly = true)
    public List<LifecycleDefinition.Transition> getAvailableTransitions(UUID objectId, UUID tenantId) {
        OntologyObjectEntity object = objectRepository.findById(objectId)
                .orElseThrow(() -> new IllegalArgumentException("Object not found: " + objectId));

        ObjectTypeEntity ot = objectTypeRepository.findById(object.getObjectTypeId())
                .orElseThrow();

        return findForObjectType(ot.getApiName(), tenantId)
                .map(lc -> lc.getAvailableTransitions(getCurrentState(object, lc)))
                .orElse(List.of());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String getCurrentState(OntologyObjectEntity object, LifecycleDefinition lifecycle) {
        if (object.getCurrentState() != null) return object.getCurrentState();
        try {
            JsonNode props = parseProperties(object);
            JsonNode stateNode = props.get(lifecycle.getStateProperty());
            return stateNode != null && !stateNode.isNull() ? stateNode.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void validateDefinition(LifecycleDefinition def) {
        if (def.getStates() == null || def.getStates().isEmpty()) {
            throw new IllegalArgumentException("Lifecycle must have at least one state");
        }
        Set<String> stateNames = new HashSet<>();
        def.getStates().forEach(s -> stateNames.add(s.getName()));

        if (def.getTransitions() != null) {
            def.getTransitions().forEach(t -> {
                if (!stateNames.contains(t.getFrom())) {
                    throw new IllegalArgumentException("Transition from unknown state: " + t.getFrom());
                }
                if (!stateNames.contains(t.getTo())) {
                    throw new IllegalArgumentException("Transition to unknown state: " + t.getTo());
                }
            });
        }

        if (def.getInitialState() != null && !stateNames.contains(def.getInitialState())) {
            throw new IllegalArgumentException("Initial state not in states list: " + def.getInitialState());
        }
    }

    private JsonNode parseProperties(OntologyObjectEntity entity) {
        try {
            String props = entity.getProperties();
            if (props == null) return objectMapper.createObjectNode();
            return objectMapper.readTree(props);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private String serializeDefinition(LifecycleDefinition def) {
        try {
            return objectMapper.writeValueAsString(def);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize lifecycle definition", e);
        }
    }

    private LifecycleDefinition parseDefinition(LifecycleEntity entity) {
        try {
            LifecycleDefinition def = objectMapper.readValue(entity.getDefinition(), LifecycleDefinition.class);
            def.setObjectTypeApiName(entity.getObjectTypeApiName());
            def.setStateProperty(entity.getStateProperty());
            return def;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse lifecycle definition", e);
        }
    }
}
