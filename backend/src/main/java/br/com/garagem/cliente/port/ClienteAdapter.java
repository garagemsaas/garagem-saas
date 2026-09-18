package br.com.garagem.cliente.port;

import br.com.garagem.cliente.repository.ClienteRepository;
import br.com.garagem.tenancy.TenantContext;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ClienteAdapter implements ClientePort {
  private final ClienteRepository clientes;

  public ClienteAdapter(ClienteRepository clientes) {
    this.clientes = clientes;
  }

  @Override
  public boolean existe(UUID clienteId) {
    return clienteId != null
        && clientes.findByIdAndOficinaId(clienteId, TenantContext.current()).isPresent();
  }
}
