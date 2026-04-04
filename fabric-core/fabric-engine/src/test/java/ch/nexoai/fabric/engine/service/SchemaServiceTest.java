package ch.nexoai.fabric.engine.service;

import ch.nexoai.fabric.engine.exception.FabricException;
import ch.nexoai.fabric.engine.model.ObjectTypeDefinition;
import ch.nexoai.fabric.engine.model.PropertyDefinition;
import ch.nexoai.fabric.engine.port.out.ObjectTypeStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchemaServiceTest {

    @Mock
    private ObjectTypeStore objectTypeStore;

    private SchemaService schemaService;

    @BeforeEach
    void setUp() {
        schemaService = new SchemaService(objectTypeStore);
    }

    @Test
    void register_createsNewType_whenNotExists() {
        List<PropertyDefinition> props = List.of(
                new PropertyDefinition("email", "Email", "string", true, true)
        );
        when(objectTypeStore.existsByApiName("contact")).thenReturn(false);
        when(objectTypeStore.save(any(ObjectTypeDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

        ObjectTypeDefinition result = schemaService.register("contact", "Contact", props);

        assertNotNull(result);
        assertEquals("contact", result.apiName());
        assertEquals("Contact", result.displayName());
        assertEquals(1, result.properties().size());
        assertNotNull(result.id());
        verify(objectTypeStore).save(any(ObjectTypeDefinition.class));
    }

    @Test
    void register_throwsException_whenAlreadyExists() {
        when(objectTypeStore.existsByApiName("contact")).thenReturn(true);

        assertThrows(FabricException.class, () ->
                schemaService.register("contact", "Contact", List.of())
        );

        verify(objectTypeStore, never()).save(any());
    }

    @Test
    void listAll_delegatesToStore() {
        ObjectTypeDefinition def = new ObjectTypeDefinition(
                java.util.UUID.randomUUID(), "contact", "Contact", null, List.of()
        );
        when(objectTypeStore.findAll()).thenReturn(List.of(def));

        List<ObjectTypeDefinition> results = schemaService.listAll();

        assertEquals(1, results.size());
        assertEquals("contact", results.get(0).apiName());
    }
}
