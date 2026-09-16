package br.com.garagem.ordemservico.repository;

import br.com.garagem.ordemservico.domain.OrdemServico;
import br.com.garagem.ordemservico.domain.StatusOs;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Listagem filtrada da OS, montada em tempo de execução a partir dos filtros informados. */
public interface OrdemServicoRepositoryCustom {

  /**
   * Busca as OS da oficina. Todo filtro é opcional: os que chegarem nulos não entram na consulta.
   *
   * @param busca texto único, já normalizado como padrão {@code like}, que casa com nome do cliente
   *     ou número da OS
   * @param buscaPlaca o mesmo texto único, porém sem hífen nem espaço, para casar com a placa como
   *     ela é gravada. Chega nulo junto com {@code busca}
   * @param de recorte inferior sobre a data de abertura, inclusivo
   * @param ate recorte superior sobre a data de abertura, inclusivo
   */
  Page<OrdemServico> filtrar(
      UUID oficinaId,
      String busca,
      String buscaPlaca,
      Long numero,
      StatusOs status,
      UUID clienteId,
      UUID veiculoId,
      UUID mecanicoId,
      String placa,
      Instant de,
      Instant ate,
      Pageable pageable);
}
