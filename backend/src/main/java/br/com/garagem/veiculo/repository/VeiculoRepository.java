package br.com.garagem.veiculo.repository;

import br.com.garagem.shared.persistence.TenantRepository;
import br.com.garagem.veiculo.domain.Veiculo;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.Query;

public interface VeiculoRepository extends TenantRepository<Veiculo> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select v from Veiculo v where v.id=:id and v.oficinaId=:oficinaId")
  java.util.Optional<Veiculo> lock(UUID id, UUID oficinaId);

  /**
   * Filtro combinado da oficina atual. {@code clienteId} também é confrontado com a oficina, de
   * modo que um cliente de outra oficina devolve página vazia em vez de dados alheios.
   *
   * <p>O campo único de pesquisa chega em duas formas: {@code buscaPlaca} já sem hífen nem espaço,
   * para casar com a placa como ela é gravada, e {@code busca} com o texto original, para casar com
   * marca e modelo. Comparar marca e modelo contra a forma normalizada faria "Fiat Uno" e "CR-V"
   * não encontrarem nada. Os dois chegam nulos juntos quando a pesquisa está vazia.
   */
  @Query(
      """
      select v from Veiculo v
       where v.oficinaId = :oficinaId
         and (:busca is null or lower(v.placa) like :buscaPlaca escape '!'
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
      String buscaPlaca,
      String placa,
      String marca,
      String modelo,
      UUID clienteId,
      Pageable pageable);
}
