package ch.nexoai.fabric.core.domain.ports.in;

import ch.nexoai.fabric.core.domain.PropertyType;

import java.util.UUID;

public interface ManagePropertyTypeUseCase {
    PropertyType addProperty(UUID objectTypeId, RegisterObjectTypeUseCase.PropertyTypeCommand command);
    void removeProperty(UUID objectTypeId, UUID propertyId);
}
