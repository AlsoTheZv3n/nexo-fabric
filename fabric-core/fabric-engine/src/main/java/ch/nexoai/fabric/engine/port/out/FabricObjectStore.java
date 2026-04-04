package ch.nexoai.fabric.engine.port.out;

import ch.nexoai.fabric.engine.model.FabricObject;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FabricObjectStore {
    FabricObject save(FabricObject object);
    Optional<FabricObject> findById(UUID id);
    Optional<FabricObject> findByExternalId(String objectType, String externalId);
    List<FabricObject> findByType(String objectType, int limit);
    void delete(UUID id);
}
