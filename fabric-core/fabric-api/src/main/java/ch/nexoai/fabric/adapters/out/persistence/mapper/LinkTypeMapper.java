package ch.nexoai.fabric.adapters.out.persistence.mapper;

import ch.nexoai.fabric.adapters.out.persistence.entity.LinkTypeEntity;
import ch.nexoai.fabric.core.domain.Cardinality;
import ch.nexoai.fabric.core.domain.LinkType;
import org.springframework.stereotype.Component;

@Component
public class LinkTypeMapper {

    public LinkType toDomain(LinkTypeEntity entity) {
        return LinkType.builder()
                .id(entity.getId())
                .apiName(entity.getApiName())
                .displayName(entity.getDisplayName())
                .sourceObjectTypeId(entity.getSourceObjectTypeId())
                .targetObjectTypeId(entity.getTargetObjectTypeId())
                .cardinality(Cardinality.valueOf(entity.getCardinality()))
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public LinkTypeEntity toEntity(LinkType domain) {
        return LinkTypeEntity.builder()
                .id(domain.getId())
                .apiName(domain.getApiName())
                .displayName(domain.getDisplayName())
                .sourceObjectTypeId(domain.getSourceObjectTypeId())
                .targetObjectTypeId(domain.getTargetObjectTypeId())
                .cardinality(domain.getCardinality().name())
                .description(domain.getDescription())
                .build();
    }
}
