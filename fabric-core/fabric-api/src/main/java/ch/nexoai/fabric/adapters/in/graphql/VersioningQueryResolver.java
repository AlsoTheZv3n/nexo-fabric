package ch.nexoai.fabric.adapters.in.graphql;

import ch.nexoai.fabric.adapters.out.persistence.repository.JpaObjectTypeRepository;
import ch.nexoai.fabric.core.exception.OntologyException;
import ch.nexoai.fabric.core.versioning.ObjectHistoryService;
import ch.nexoai.fabric.core.versioning.SchemaVersioningService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.util.*;

@Controller
@RequiredArgsConstructor
public class VersioningQueryResolver {

    private final SchemaVersioningService schemaVersioningService;
    private final ObjectHistoryService objectHistoryService;
    private final JpaObjectTypeRepository objectTypeRepository;

    @QueryMapping
    public List<Map<String, Object>> getSchemaVersions(@Argument String objectTypeApiName) {
        UUID objectTypeId = objectTypeRepository.findByIsActiveTrue().stream()
                .filter(ot -> ot.getApiName().equals(objectTypeApiName))
                .findFirst()
                .map(ot -> ot.getId())
                .orElseThrow(() -> new OntologyException("ObjectType not found: " + objectTypeApiName));

        return schemaVersioningService.getSchemaVersions(objectTypeId);
    }

    @QueryMapping
    public List<Map<String, Object>> getObjectHistory(@Argument String objectId, @Argument Integer limit) {
        int lim = limit != null ? limit : 20;
        return objectHistoryService.getObjectHistory(UUID.fromString(objectId), lim);
    }

    @QueryMapping
    public Map<String, Object> getObjectAsOf(@Argument String objectId, @Argument String asOf) {
        Instant timestamp = Instant.parse(asOf);
        return objectHistoryService.getObjectAsOf(UUID.fromString(objectId), timestamp);
    }

    @QueryMapping
    public Map<String, Object> diffObject(@Argument String objectId,
                                           @Argument String from, @Argument String to) {
        return objectHistoryService.diffObject(
                UUID.fromString(objectId), Instant.parse(from), Instant.parse(to));
    }
}
