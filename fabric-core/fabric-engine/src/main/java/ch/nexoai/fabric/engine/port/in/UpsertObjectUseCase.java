package ch.nexoai.fabric.engine.port.in;

import ch.nexoai.fabric.engine.model.FabricObject;
import com.fasterxml.jackson.databind.JsonNode;

public interface UpsertObjectUseCase {
    FabricObject upsert(String objectType, String externalId, JsonNode properties);
}
