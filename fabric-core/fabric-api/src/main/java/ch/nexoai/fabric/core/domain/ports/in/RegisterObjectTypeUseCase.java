package ch.nexoai.fabric.core.domain.ports.in;

import ch.nexoai.fabric.core.domain.ObjectType;
import ch.nexoai.fabric.core.domain.PropertyDataType;

import java.util.List;

public interface RegisterObjectTypeUseCase {

    ObjectType registerObjectType(RegisterObjectTypeCommand command);

    record RegisterObjectTypeCommand(
            String apiName,
            String displayName,
            String description,
            String icon,
            String color,
            List<PropertyTypeCommand> properties
    ) {}

    record PropertyTypeCommand(
            String apiName,
            String displayName,
            PropertyDataType dataType,
            boolean isPrimaryKey,
            boolean isRequired,
            boolean isIndexed,
            String defaultValue,
            String description
    ) {}
}
