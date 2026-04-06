package ch.nexoai.fabric.adapters.out.persistence.repository;

import ch.nexoai.fabric.adapters.out.persistence.entity.FunctionExecutionEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaFunctionExecutionRepository extends JpaRepository<FunctionExecutionEntity, UUID> {
    List<FunctionExecutionEntity> findByFunctionIdOrderByExecutedAtDesc(UUID functionId, Pageable pageable);
    List<FunctionExecutionEntity> findByTenantIdOrderByExecutedAtDesc(UUID tenantId, Pageable pageable);
}
