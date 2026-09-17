package br.com.garagem.assinatura.repository;

import br.com.garagem.assinatura.domain.Plano;
import java.util.*;
import org.springframework.data.repository.Repository;

/** Catálogo global: não estende TenantRepository porque plano não pertence a oficina alguma. */
public interface PlanoRepository extends Repository<Plano, UUID> {
  Optional<Plano> findById(UUID id);

  Optional<Plano> findByCodigo(String codigo);

  List<Plano> findByAtivoTrueOrderByOrdemAscCodigoAsc();

  Plano save(Plano plano);
}
