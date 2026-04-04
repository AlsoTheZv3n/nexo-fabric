package ch.nexoai.fabric.engine.port.out;

import ch.nexoai.fabric.engine.model.ObjectTypeDefinition;

import java.util.List;
import java.util.Optional;

public interface ObjectTypeStore {
    ObjectTypeDefinition save(ObjectTypeDefinition definition);
    Optional<ObjectTypeDefinition> findByApiName(String apiName);
    List<ObjectTypeDefinition> findAll();
    boolean existsByApiName(String apiName);
}
