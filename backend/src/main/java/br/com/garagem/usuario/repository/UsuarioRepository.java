package br.com.garagem.usuario.repository;

import br.com.garagem.shared.persistence.TenantRepository;
import br.com.garagem.usuario.domain.Usuario;

public interface UsuarioRepository extends TenantRepository<Usuario> {}
