package br.com.garagem.dinheiroesquecido.repository;

import br.com.garagem.dinheiroesquecido.domain.ContatoOportunidade;
import br.com.garagem.shared.persistence.TenantRepository;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ContatoOportunidadeRepository extends TenantRepository<ContatoOportunidade> {
  List<ContatoOportunidade> findByOportunidadeIdAndOficinaIdOrderByCriadoEmAscIdAsc(
      UUID oportunidadeId, UUID oficinaId);
}
