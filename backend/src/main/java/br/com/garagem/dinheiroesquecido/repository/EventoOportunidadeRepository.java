package br.com.garagem.dinheiroesquecido.repository;

import br.com.garagem.dinheiroesquecido.domain.EventoOportunidade;
import br.com.garagem.shared.persistence.TenantRepository;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface EventoOportunidadeRepository extends TenantRepository<EventoOportunidade> {
  List<EventoOportunidade> findByOportunidadeIdAndOficinaIdOrderByCriadoEmAscIdAsc(
      UUID oportunidadeId, UUID oficinaId);
}
