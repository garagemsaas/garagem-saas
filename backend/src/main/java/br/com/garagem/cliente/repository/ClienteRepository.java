package br.com.garagem.cliente.repository;

import br.com.garagem.cliente.domain.Cliente;
import br.com.garagem.shared.persistence.TenantRepository;

public interface ClienteRepository extends TenantRepository<Cliente> {
  org.springframework.data.domain.Page<Cliente> findByOficinaIdAndNomeContainingIgnoreCase(
      java.util.UUID oficinaId, String nome, org.springframework.data.domain.Pageable pageable);
}
