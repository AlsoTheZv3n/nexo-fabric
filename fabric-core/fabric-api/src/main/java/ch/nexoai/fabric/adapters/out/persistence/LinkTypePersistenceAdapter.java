package ch.nexoai.fabric.adapters.out.persistence;

import ch.nexoai.fabric.adapters.out.persistence.mapper.LinkTypeMapper;
import ch.nexoai.fabric.adapters.out.persistence.repository.JpaLinkTypeRepository;
import ch.nexoai.fabric.core.domain.LinkType;
import ch.nexoai.fabric.core.domain.ports.out.LinkTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class LinkTypePersistenceAdapter implements LinkTypeRepository {

    private final JpaLinkTypeRepository jpaRepository;
    private final LinkTypeMapper mapper;

    @Override
    public LinkType save(LinkType linkType) {
        var entity = mapper.toEntity(linkType);
        var saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<LinkType> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<LinkType> findAll() {
        return jpaRepository.findAll().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsByApiName(String apiName) {
        return jpaRepository.existsByApiName(apiName);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }
}
