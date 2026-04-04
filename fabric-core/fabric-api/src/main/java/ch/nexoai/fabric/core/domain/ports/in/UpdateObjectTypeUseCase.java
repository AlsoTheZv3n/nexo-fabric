package ch.nexoai.fabric.core.domain.ports.in;

import ch.nexoai.fabric.core.domain.ObjectType;

import java.util.UUID;

public interface UpdateObjectTypeUseCase {

    ObjectType updateObjectType(UUID id, UpdateObjectTypeCommand command);

    void deactivateObjectType(UUID id);

    record UpdateObjectTypeCommand(
            String displayName,
            String description,
            String icon,
            String color
    ) {}
}
