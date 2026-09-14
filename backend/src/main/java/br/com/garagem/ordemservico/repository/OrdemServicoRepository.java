package br.com.garagem.ordemservico.repository;

import br.com.garagem.ordemservico.domain.OrdemServico;
import br.com.garagem.shared.persistence.TenantRepository;

public interface OrdemServicoRepository extends TenantRepository<OrdemServico> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query(
      "select o from OrdemServico o where o.id = :id and o.oficinaId = :oficinaId")
  java.util.Optional<OrdemServico> lock(java.util.UUID id, java.util.UUID oficinaId);

  @org.springframework.data.jpa.repository.Query(
      "select o from OrdemServico o, Veiculo v, Cliente c where o.oficinaId = :oficinaId and v.oficinaId = :oficinaId and c.oficinaId = :oficinaId and o.veiculoId = v.id and o.clienteId = c.id and (lower(v.placa) like :busca escape '!' or lower(c.nome) like :busca escape '!' or cast(o.numero as string) like :busca escape '!')")
  org.springframework.data.domain.Page<OrdemServico> search(
      java.util.UUID oficinaId, String busca, org.springframework.data.domain.Pageable pageable);
}
