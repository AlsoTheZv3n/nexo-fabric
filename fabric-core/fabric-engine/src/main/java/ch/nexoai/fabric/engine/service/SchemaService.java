package ch.nexoai.fabric.engine.service;

import ch.nexoai.fabric.engine.exception.FabricException;
import ch.nexoai.fabric.engine.model.ObjectTypeDefinition;
import ch.nexoai.fabric.engine.model.PropertyDefinition;
import ch.nexoai.fabric.engine.port.in.RegisterObjectTypeUseCase;
import ch.nexoai.fabric.engine.port.out.ObjectTypeStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

public class SchemaService implements RegisterObjectTypeUseCase {

    private static final Logger log = LoggerFactory.getLogger(SchemaService.class);

    private final ObjectTypeStore objectTypeStore;

    public SchemaService(ObjectTypeStore objectTypeStore) {
        this.objectTypeStore = objectTypeStore;
    }

    @Override
    public ObjectTypeDefinition register(String apiName, String displayName, List<PropertyDefinition> properties) {
        log.debug("Registering object type: apiName={}", apiName);

        if (objectTypeStore.existsByApiName(apiName)) {
            throw new FabricException("Object type already exists: " + apiName);
        }

        ObjectTypeDefinition definition = new ObjectTypeDefinition(
                UUID.randomUUID(),
                apiName,
                displayName,
                null,
                properties
        );

        return objectTypeStore.save(definition);
    }

    @Override
    public List<ObjectTypeDefinition> listAll() {
        return objectTypeStore.findAll();
    }
}
