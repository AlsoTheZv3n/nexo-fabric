package ch.nexoai.fabric.adapters.out.persistence.repository;

import ch.nexoai.fabric.adapters.out.persistence.entity.ObjectTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaObjectTypeRepository extends JpaRepository<ObjectTypeEntity, UUID> {
    boolean existsByApiName(String apiName);
    List<ObjectTypeEntity> findByIsActiveTrue();
}
