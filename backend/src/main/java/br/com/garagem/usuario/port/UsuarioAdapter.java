package br.com.garagem.usuario.port;

import br.com.garagem.tenancy.TenantContext;
import br.com.garagem.usuario.domain.Papel;
import br.com.garagem.usuario.domain.Usuario;
import br.com.garagem.usuario.repository.UsuarioRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UsuarioAdapter implements UsuarioPort {
  private final UsuarioRepository usuarios;

  public UsuarioAdapter(UsuarioRepository usuarios) {
    this.usuarios = usuarios;
  }

  private Optional<Usuario> naOficina(UUID id) {
    return id == null
        ? Optional.empty()
        : usuarios.findByIdAndOficinaId(id, TenantContext.current());
  }

  @Override
  public boolean existe(UUID usuarioId) {
    return naOficina(usuarioId).isPresent();
  }

  @Override
  public boolean ehMecanicoAtivo(UUID usuarioId) {
    return naOficina(usuarioId).filter(u -> u.ativo && u.papel == Papel.MECANICO).isPresent();
  }

  @Override
  public boolean ehComercialAtivo(UUID usuarioId) {
    return naOficina(usuarioId)
        .filter(u -> u.ativo && (u.papel == Papel.OWNER || u.papel == Papel.ATENDENTE))
        .isPresent();
  }
}
