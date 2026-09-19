package br.com.garagem.revenda.shared;

import br.com.garagem.cliente.port.ClientePort;
import br.com.garagem.shared.error.ApiException;
import br.com.garagem.usuario.port.UsuarioPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RevendaReferencias {
  private final ClientePort clientes;
  private final UsuarioPort usuarios;

  public RevendaReferencias(ClientePort clientes, UsuarioPort usuarios) {
    this.clientes = clientes;
    this.usuarios = usuarios;
  }

  public void cliente(UUID id) {
    if (id == null || !clientes.existe(id)) throw ApiException.missing();
  }

  public void comercial(UUID id) {
    if (id == null || !usuarios.ehComercialAtivo(id)) throw ApiException.missing();
  }
}
