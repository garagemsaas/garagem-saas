package br.com.garagem.dinheiroesquecido.repository;

import br.com.garagem.dinheiroesquecido.domain.ResultadoOportunidade;
import br.com.garagem.shared.persistence.TenantRepository;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ResultadoOportunidadeRepository extends TenantRepository<ResultadoOportunidade> {
  List<ResultadoOportunidade> findByOportunidadeIdAndOficinaIdOrderByCriadoEmAscIdAsc(
      UUID oportunidadeId, UUID oficinaId);
}
