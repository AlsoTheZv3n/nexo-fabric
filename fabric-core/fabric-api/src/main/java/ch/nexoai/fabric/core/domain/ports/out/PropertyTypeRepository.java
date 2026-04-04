package ch.nexoai.fabric.core.domain.ports.out;

import ch.nexoai.fabric.core.domain.PropertyType;

import java.util.List;
import java.util.UUID;

public interface PropertyTypeRepository {
    PropertyType save(PropertyType propertyType, UUID objectTypeId);
    List<PropertyType> findByObjectTypeId(UUID objectTypeId);
    void deleteById(UUID id);
}
