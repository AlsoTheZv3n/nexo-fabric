package ch.nexoai.fabric.core.service.action;

import com.fasterxml.jackson.databind.JsonNode;
import ch.nexoai.fabric.core.domain.action.ActionType;
import ch.nexoai.fabric.core.domain.object.OntologyObject;

public interface SideEffect {
    String getType();
    void triggerAsync(ActionType actionType, OntologyObject object, JsonNode newState);
}
