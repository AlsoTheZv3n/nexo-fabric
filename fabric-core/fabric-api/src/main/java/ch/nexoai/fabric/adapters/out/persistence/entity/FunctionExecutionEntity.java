package ch.nexoai.fabric.adapters.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "function_executions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FunctionExecutionEntity {
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "function_id", nullable = false)
    private UUID functionId;

    @Column(name = "object_id")
    private UUID objectId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_data", columnDefinition = "jsonb")
    private String inputData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_data", columnDefinition = "jsonb")
    private String outputData;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "executed_by")
    private String executedBy;

    @CreationTimestamp
    @Column(name = "executed_at", updatable = false)
    private Instant executedAt;
}
