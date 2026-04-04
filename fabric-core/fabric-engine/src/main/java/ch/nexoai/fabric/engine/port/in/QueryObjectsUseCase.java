package ch.nexoai.fabric.engine.port.in;

import ch.nexoai.fabric.engine.model.FabricObject;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QueryObjectsUseCase {
    List<FabricObject> search(String objectType, int limit);
    Optional<FabricObject> findById(UUID id);
    Optional<FabricObject> findByExternalId(String objectType, String externalId);
}
