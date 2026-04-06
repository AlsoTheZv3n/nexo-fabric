package ch.nexoai.fabric.adapters.out.persistence.repository;

import ch.nexoai.fabric.adapters.out.persistence.entity.LifecycleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaLifecycleRepository extends JpaRepository<LifecycleEntity, UUID> {
    Optional<LifecycleEntity> findByObjectTypeApiNameAndTenantId(String objectTypeApiName, UUID tenantId);
    List<LifecycleEntity> findByTenantId(UUID tenantId);
}
