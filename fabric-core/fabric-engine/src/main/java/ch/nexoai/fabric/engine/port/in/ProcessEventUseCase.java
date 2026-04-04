package ch.nexoai.fabric.engine.port.in;

import ch.nexoai.fabric.engine.model.FabricEvent;
import ch.nexoai.fabric.engine.model.FabricObject;

public interface ProcessEventUseCase {
    FabricObject processEvent(FabricEvent event);
}
