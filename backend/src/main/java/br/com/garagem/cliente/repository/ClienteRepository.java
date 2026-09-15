package br.com.garagem.cliente.repository;

import br.com.garagem.cliente.domain.Cliente;
import br.com.garagem.shared.persistence.TenantRepository;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.Query;

public interface ClienteRepository extends TenantRepository<Cliente> {

  /**
   * Filtro combinado da oficina atual. Cada predicado só entra quando o parâmetro correspondente
   * chega preenchido; {@code busca} continua atendendo o campo único de pesquisa do frontend.
   */
  @Query(
      """
      select c from Cliente c
       where c.oficinaId = :oficinaId
         and (:busca is null or lower(c.nome) like :busca escape '!'
              or lower(c.telefone) like :busca escape '!'
              or (c.email is not null and lower(c.email) like :busca escape '!'))
         and (:nome is null or lower(c.nome) like :nome escape '!')
         and (:telefone is null or lower(c.telefone) like :telefone escape '!')
         and (:email is null or (c.email is not null and lower(c.email) like :email escape '!'))
      """)
  Page<Cliente> filtrar(
      UUID oficinaId, String busca, String nome, String telefone, String email, Pageable pageable);
}
