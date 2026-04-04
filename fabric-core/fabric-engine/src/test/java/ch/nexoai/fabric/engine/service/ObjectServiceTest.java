package ch.nexoai.fabric.engine.service;

import ch.nexoai.fabric.engine.model.FabricObject;
import ch.nexoai.fabric.engine.port.out.FabricObjectStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ObjectServiceTest {

    @Mock
    private FabricObjectStore objectStore;

    private ObjectService objectService;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        objectService = new ObjectService(objectStore);
    }

    @Test
    void upsert_createsNewObject_whenNotExists() {
        JsonNode props = mapper.createObjectNode().put("name", "Test");
        when(objectStore.findByExternalId("contact", "ext-1")).thenReturn(Optional.empty());
        when(objectStore.save(any(FabricObject.class))).thenAnswer(inv -> inv.getArgument(0));

        FabricObject result = objectService.upsert("contact", "ext-1", props);

        assertNotNull(result);
        assertEquals("contact", result.objectType());
        assertEquals("ext-1", result.externalId());
        assertEquals(props, result.properties());
        assertNotNull(result.id());
        assertNotNull(result.createdAt());
        verify(objectStore).save(any(FabricObject.class));
    }

    @Test
    void upsert_updatesExisting_whenExists() {
        UUID existingId = UUID.randomUUID();
        Instant createdAt = Instant.now().minusSeconds(3600);
        JsonNode oldProps = mapper.createObjectNode().put("name", "Old");
        JsonNode newProps = mapper.createObjectNode().put("name", "New");

        FabricObject existing = new FabricObject(existingId, "contact", oldProps, "ext-1", createdAt, createdAt);
        when(objectStore.findByExternalId("contact", "ext-1")).thenReturn(Optional.of(existing));
        when(objectStore.save(any(FabricObject.class))).thenAnswer(inv -> inv.getArgument(0));

        FabricObject result = objectService.upsert("contact", "ext-1", newProps);

        assertEquals(existingId, result.id());
        assertEquals(newProps, result.properties());
        assertEquals(createdAt, result.createdAt());
        assertTrue(result.updatedAt().isAfter(createdAt));
        verify(objectStore).save(any(FabricObject.class));
    }

    @Test
    void search_delegatesToStore() {
        FabricObject obj = new FabricObject(UUID.randomUUID(), "contact", null, "ext-1", Instant.now(), Instant.now());
        when(objectStore.findByType("contact", 10)).thenReturn(List.of(obj));

        List<FabricObject> results = objectService.search("contact", 10);

        assertEquals(1, results.size());
        assertEquals(obj, results.get(0));
    }

    @Test
    void findById_delegatesToStore() {
        UUID id = UUID.randomUUID();
        FabricObject obj = new FabricObject(id, "contact", null, "ext-1", Instant.now(), Instant.now());
        when(objectStore.findById(id)).thenReturn(Optional.of(obj));

        Optional<FabricObject> result = objectService.findById(id);

        assertTrue(result.isPresent());
        assertEquals(id, result.get().id());
    }

    @Test
    void findByExternalId_delegatesToStore() {
        FabricObject obj = new FabricObject(UUID.randomUUID(), "contact", null, "ext-1", Instant.now(), Instant.now());
        when(objectStore.findByExternalId("contact", "ext-1")).thenReturn(Optional.of(obj));

        Optional<FabricObject> result = objectService.findByExternalId("contact", "ext-1");

        assertTrue(result.isPresent());
        assertEquals("ext-1", result.get().externalId());
    }
}
