package br.com.garagem.shared.persistence;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

@NoRepositoryBean
public interface TenantRepository<T extends TenantEntity> extends Repository<T, UUID> {
  Optional<T> findByIdAndOficinaId(UUID id, UUID oficinaId);

  Page<T> findAllByOficinaId(UUID oficinaId, Pageable pageable);

  <S extends T> S save(S entity);
}
