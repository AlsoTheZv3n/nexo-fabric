package ch.nexoai.fabric.adapters.out.persistence.repository;

import ch.nexoai.fabric.adapters.out.persistence.entity.DataSourceDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaDataSourceRepository extends JpaRepository<DataSourceDefinitionEntity, UUID> {
    Optional<DataSourceDefinitionEntity> findByApiName(String apiName);
    boolean existsByApiName(String apiName);
    List<DataSourceDefinitionEntity> findByIsActiveTrue();
    Optional<DataSourceDefinitionEntity> findBySourceTable(String sourceTable);
}
