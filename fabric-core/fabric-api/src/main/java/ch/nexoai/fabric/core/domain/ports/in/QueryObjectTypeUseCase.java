package ch.nexoai.fabric.core.domain.ports.in;

import ch.nexoai.fabric.core.domain.ObjectType;

import java.util.List;
import java.util.UUID;

public interface QueryObjectTypeUseCase {
    ObjectType getObjectType(UUID id);
    List<ObjectType> getAllObjectTypes();
}
