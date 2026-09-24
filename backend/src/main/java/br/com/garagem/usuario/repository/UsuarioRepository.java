package br.com.garagem.usuario.repository;

import br.com.garagem.shared.persistence.TenantRepository;
import br.com.garagem.usuario.domain.Papel;
import br.com.garagem.usuario.domain.Usuario;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.Query;

public interface UsuarioRepository extends TenantRepository<Usuario> {
  void flush();

  /** Equipe da oficina atual, opcionalmente restrita a um papel e/ou à situação de acesso. */
  @Query(
      """
      select u from Usuario u
       where u.oficinaId = :oficinaId
         and (:papel is null or u.papel = :papel)
         and (:ativo is null or u.ativo = :ativo)
         and (lower(u.nome) like :busca or lower(u.email) like :busca)
      """)
  Page<Usuario> filtrar(
      UUID oficinaId, Papel papel, Boolean ativo, String busca, Pageable pageable);
}
