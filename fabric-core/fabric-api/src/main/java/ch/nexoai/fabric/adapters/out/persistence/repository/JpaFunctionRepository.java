package ch.nexoai.fabric.adapters.out.persistence.repository;

import ch.nexoai.fabric.adapters.out.persistence.entity.FunctionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaFunctionRepository extends JpaRepository<FunctionEntity, UUID> {
    List<FunctionEntity> findByTenantId(UUID tenantId);
    Optional<FunctionEntity> findByApiNameAndTenantId(String apiName, UUID tenantId);
}
