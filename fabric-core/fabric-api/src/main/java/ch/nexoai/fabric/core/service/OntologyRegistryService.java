package ch.nexoai.fabric.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ch.nexoai.fabric.core.domain.*;
import ch.nexoai.fabric.core.domain.ports.in.*;
import ch.nexoai.fabric.core.domain.ports.out.*;
import ch.nexoai.fabric.core.exception.*;
import ch.nexoai.fabric.core.tenant.TenantContext;
import ch.nexoai.fabric.core.versioning.SchemaVersioningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OntologyRegistryService implements
        RegisterObjectTypeUseCase,
        UpdateObjectTypeUseCase,
        QueryObjectTypeUseCase,
        ManagePropertyTypeUseCase,
        ManageLinkTypeUseCase {

    private final ObjectTypeRepository objectTypeRepository;
    private final PropertyTypeRepository propertyTypeRepository;
    private final LinkTypeRepository linkTypeRepository;
    private final SchemaVersioningService schemaVersioningService;
    private final ObjectMapper objectMapper;

    // ── ObjectType Registration ──────────────────────────────────────────

    @Override
    public ObjectType registerObjectType(RegisterObjectTypeCommand command) {
        if (objectTypeRepository.existsByApiName(command.apiName())) {
            throw new DuplicateApiNameException(command.apiName());
        }

        ObjectType objectType = ObjectType.builder()
                .apiName(command.apiName())
                .displayName(command.displayName())
                .description(command.description())
                .icon(command.icon())
                .color(command.color())
                .isActive(true)
                .properties(new ArrayList<>())
                .build();

        if (command.properties() != null) {
            for (PropertyTypeCommand propCmd : command.properties()) {
                PropertyType property = mapPropertyCommand(propCmd);
                objectType.addProperty(property);
            }
        }

        return objectTypeRepository.save(objectType);
    }

    // ── ObjectType Update ────────────────────────────────────────────────

    @Override
    public ObjectType updateObjectType(UUID id, UpdateObjectTypeCommand command) {
        ObjectType objectType = findObjectTypeOrThrow(id);

        // Schema version snapshot before applying changes
        try {
            ObjectNode snapshot = buildSchemaSnapshot(objectType);
            List<String> changes = new ArrayList<>();
            if (!Objects.equals(objectType.getDisplayName(), command.displayName())) {
                changes.add("displayName: '" + objectType.getDisplayName() + "' → '" + command.displayName() + "'");
            }
            if (!Objects.equals(objectType.getDescription(), command.description())) {
                changes.add("description updated");
            }
            String summary = changes.isEmpty() ? "Updated" : String.join(", ", changes);
            schemaVersioningService.createVersion(id, snapshot, summary, false,
                    TenantContext.getCurrentUser());
        } catch (Exception e) {
            log.warn("Could not create schema version for ObjectType {}: {}", id, e.getMessage());
        }

        objectType.setDisplayName(command.displayName());
        objectType.setDescription(command.description());
        objectType.setIcon(command.icon());
        objectType.setColor(command.color());
        return objectTypeRepository.save(objectType);
    }

    @Override
    public void deactivateObjectType(UUID id) {
        ObjectType objectType = findObjectTypeOrThrow(id);
        objectType.setActive(false);
        objectTypeRepository.save(objectType);
    }

    // ── ObjectType Query ─────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public ObjectType getObjectType(UUID id) {
        return findObjectTypeOrThrow(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ObjectType> getAllObjectTypes() {
        return objectTypeRepository.findAllActive();
    }

    // ── PropertyType Management ──────────────────────────────────────────

    @Override
    public PropertyType addProperty(UUID objectTypeId, RegisterObjectTypeUseCase.PropertyTypeCommand command) {
        ObjectType objectType = findObjectTypeOrThrow(objectTypeId);

        try {
            ObjectNode snapshot = buildSchemaSnapshot(objectType);
            schemaVersioningService.createVersion(objectTypeId, snapshot,
                    "Added property: " + command.apiName() + " (" + command.dataType() + ")",
                    false, TenantContext.getCurrentUser());
        } catch (Exception e) {
            log.warn("Could not create schema version for addProperty on {}: {}", objectTypeId, e.getMessage());
        }

        PropertyType property = mapPropertyCommand(command);
        objectType.addProperty(property);
        return propertyTypeRepository.save(property, objectTypeId);
    }

    @Override
    public void removeProperty(UUID objectTypeId, UUID propertyId) {
        ObjectType objectType = findObjectTypeOrThrow(objectTypeId);
        PropertyType property = objectType.getProperties().stream()
                .filter(p -> p.getId() != null && p.getId().equals(propertyId))
                .findFirst().orElse(null);

        try {
            ObjectNode snapshot = buildSchemaSnapshot(objectType);
            String propName = property != null ? property.getApiName() : propertyId.toString();
            schemaVersioningService.createVersion(objectTypeId, snapshot,
                    "BREAKING: Removed property: " + propName,
                    true, TenantContext.getCurrentUser());
        } catch (Exception e) {
            log.warn("Could not create schema version for removeProperty on {}: {}", objectTypeId, e.getMessage());
        }

        propertyTypeRepository.deleteById(propertyId);
    }

    // ── LinkType Management ──────────────────────────────────────────────

    @Override
    public LinkType createLinkType(CreateLinkTypeCommand command) {
        if (linkTypeRepository.existsByApiName(command.apiName())) {
            throw new DuplicateApiNameException(command.apiName());
        }
        findObjectTypeOrThrow(command.sourceObjectTypeId());
        findObjectTypeOrThrow(command.targetObjectTypeId());

        LinkType linkType = LinkType.builder()
                .apiName(command.apiName())
                .displayName(command.displayName())
                .sourceObjectTypeId(command.sourceObjectTypeId())
                .targetObjectTypeId(command.targetObjectTypeId())
                .cardinality(command.cardinality() != null ? command.cardinality() : Cardinality.ONE_TO_MANY)
                .description(command.description())
                .build();

        return linkTypeRepository.save(linkType);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LinkType> getAllLinkTypes() {
        return linkTypeRepository.findAll();
    }

    @Override
    public void deleteLinkType(UUID id) {
        linkTypeRepository.deleteById(id);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private ObjectNode buildSchemaSnapshot(ObjectType objectType) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("apiName", objectType.getApiName());
        snapshot.put("displayName", objectType.getDisplayName());
        snapshot.put("description", objectType.getDescription() != null ? objectType.getDescription() : "");
        snapshot.set("properties", objectMapper.valueToTree(
                objectType.getProperties().stream().map(p -> {
                    ObjectNode pn = objectMapper.createObjectNode();
                    pn.put("apiName", p.getApiName());
                    pn.put("dataType", p.getDataType().name());
                    pn.put("isRequired", p.isRequired());
                    return pn;
                }).toList()));
        return snapshot;
    }

    private ObjectType findObjectTypeOrThrow(UUID id) {
        return objectTypeRepository.findById(id)
                .orElseThrow(() -> new ObjectTypeNotFoundException(id));
    }

    private PropertyType mapPropertyCommand(RegisterObjectTypeUseCase.PropertyTypeCommand cmd) {
        return PropertyType.builder()
                .apiName(cmd.apiName())
                .displayName(cmd.displayName())
                .dataType(cmd.dataType())
                .isPrimaryKey(cmd.isPrimaryKey())
                .isRequired(cmd.isRequired())
                .isIndexed(cmd.isIndexed())
                .defaultValue(cmd.defaultValue())
                .description(cmd.description())
                .build();
    }
}
