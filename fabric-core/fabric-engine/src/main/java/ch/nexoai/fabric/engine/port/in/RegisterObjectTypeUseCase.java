package ch.nexoai.fabric.engine.port.in;

import ch.nexoai.fabric.engine.model.ObjectTypeDefinition;
import ch.nexoai.fabric.engine.model.PropertyDefinition;

import java.util.List;

public interface RegisterObjectTypeUseCase {
    ObjectTypeDefinition register(String apiName, String displayName, List<PropertyDefinition> properties);
    List<ObjectTypeDefinition> listAll();
}
