package ch.nexoai.fabric.core.domain.ports.in;

import ch.nexoai.fabric.core.domain.Cardinality;
import ch.nexoai.fabric.core.domain.LinkType;

import java.util.List;
import java.util.UUID;

public interface ManageLinkTypeUseCase {

    LinkType createLinkType(CreateLinkTypeCommand command);

    List<LinkType> getAllLinkTypes();

    void deleteLinkType(UUID id);

    record CreateLinkTypeCommand(
            String apiName,
            String displayName,
            UUID sourceObjectTypeId,
            UUID targetObjectTypeId,
            Cardinality cardinality,
            String description
    ) {}
}
