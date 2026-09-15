package br.com.garagem.veiculo.repository;

import br.com.garagem.shared.persistence.TenantRepository;
import br.com.garagem.veiculo.domain.Veiculo;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.Query;

public interface VeiculoRepository extends TenantRepository<Veiculo> {

  /**
   * Filtro combinado da oficina atual. {@code clienteId} também é confrontado com a oficina, de
   * modo que um cliente de outra oficina devolve página vazia em vez de dados alheios.
   */
  @Query(
      """
      select v from Veiculo v
       where v.oficinaId = :oficinaId
         and (:busca is null or lower(v.placa) like :busca escape '!'
              or lower(v.marca) like :busca escape '!'
              or lower(v.modelo) like :busca escape '!')
         and (:placa is null or lower(v.placa) like :placa escape '!')
         and (:marca is null or lower(v.marca) like :marca escape '!')
         and (:modelo is null or lower(v.modelo) like :modelo escape '!')
         and (:clienteId is null or v.clienteId = :clienteId)
      """)
  Page<Veiculo> filtrar(
      UUID oficinaId,
      String busca,
      String placa,
      String marca,
      String modelo,
      UUID clienteId,
      Pageable pageable);
}
