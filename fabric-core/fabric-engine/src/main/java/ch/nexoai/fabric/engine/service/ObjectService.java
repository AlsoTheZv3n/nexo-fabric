package ch.nexoai.fabric.engine.service;

import ch.nexoai.fabric.engine.model.FabricObject;
import ch.nexoai.fabric.engine.port.in.QueryObjectsUseCase;
import ch.nexoai.fabric.engine.port.in.UpsertObjectUseCase;
import ch.nexoai.fabric.engine.port.out.FabricObjectStore;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ObjectService implements UpsertObjectUseCase, QueryObjectsUseCase {

    private static final Logger log = LoggerFactory.getLogger(ObjectService.class);

    private final FabricObjectStore objectStore;

    public ObjectService(FabricObjectStore objectStore) {
        this.objectStore = objectStore;
    }

    @Override
    public FabricObject upsert(String objectType, String externalId, JsonNode properties) {
        log.debug("Upserting object: type={}, externalId={}", objectType, externalId);

        Optional<FabricObject> existing = objectStore.findByExternalId(objectType, externalId);

        if (existing.isPresent()) {
            FabricObject current = existing.get();
            FabricObject updated = new FabricObject(
                    current.id(),
                    current.objectType(),
                    properties,
                    current.externalId(),
                    current.createdAt(),
                    Instant.now()
            );
            log.debug("Updating existing object: id={}", current.id());
            return objectStore.save(updated);
        } else {
            FabricObject newObject = new FabricObject(
                    UUID.randomUUID(),
                    objectType,
                    properties,
                    externalId,
                    Instant.now(),
                    Instant.now()
            );
            log.debug("Creating new object: id={}", newObject.id());
            return objectStore.save(newObject);
        }
    }

    @Override
    public List<FabricObject> search(String objectType, int limit) {
        return objectStore.findByType(objectType, limit);
    }

    @Override
    public Optional<FabricObject> findById(UUID id) {
        return objectStore.findById(id);
    }

    @Override
    public Optional<FabricObject> findByExternalId(String objectType, String externalId) {
        return objectStore.findByExternalId(objectType, externalId);
    }
}
