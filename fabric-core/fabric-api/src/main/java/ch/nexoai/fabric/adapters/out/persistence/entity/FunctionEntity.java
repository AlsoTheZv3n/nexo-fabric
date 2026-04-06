package ch.nexoai.fabric.adapters.out.persistence.entity;

import ch.nexoai.fabric.core.functions.FunctionDefinition;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "functions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FunctionEntity {
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "api_name", nullable = false, length = 100)
    private String apiName;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "description")
    private String description;

    @Column(name = "language", nullable = false, length = 20)
    @Builder.Default
    private String language = "javascript";

    @Column(name = "source_code", nullable = false, columnDefinition = "TEXT")
    private String sourceCode;

    @Column(name = "input_type", length = 100)
    private String inputType;

    @Column(name = "output_type", length = 50)
    private String outputType;

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public FunctionDefinition toDomain() {
        return FunctionDefinition.builder()
                .id(id)
                .tenantId(tenantId)
                .apiName(apiName)
                .displayName(displayName)
                .description(description)
                .language(language)
                .sourceCode(sourceCode)
                .inputType(inputType)
                .outputType(outputType)
                .isActive(isActive)
                .createdAt(createdAt)
                .build();
    }
}
