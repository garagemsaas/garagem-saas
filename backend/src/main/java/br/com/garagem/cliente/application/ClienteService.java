package br.com.garagem.cliente.application;

import br.com.garagem.cliente.api.ClienteDtos.*;
import br.com.garagem.cliente.domain.Cliente;
import br.com.garagem.cliente.repository.ClienteRepository;
import br.com.garagem.shared.error.ApiException;
import br.com.garagem.shared.persistence.Filtros;
import br.com.garagem.shared.persistence.Pagina;
import br.com.garagem.tenancy.TenantContext;
import java.util.Set;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ClienteService {
  private final ClienteRepository repo;

  public ClienteService(ClienteRepository repo) {
    this.repo = repo;
  }

  /** Campos que a listagem aceita em {@code ordenacao}; qualquer outro é recusado com 400. */
  public static final Set<String> ORDENACAO = Set.of("nome", "telefone", "email", "criadoEm");

  @Transactional(readOnly = true)
  public Pagina<Saida> listar(
      String busca,
      String nome,
      String telefone,
      String email,
      int pagina,
      int tamanho,
      String ordenacao) {
    return Pagina.de(
        repo.filtrar(
                TenantContext.current(),
                Filtros.like(busca),
                Filtros.like(nome),
                Filtros.like(telefone),
                Filtros.like(email),
                Pagina.request(pagina, tamanho, ordenacao, ORDENACAO))
            .map(Saida::de));
  }

  @Transactional(readOnly = true)
  public Saida obter(UUID id) {
    return Saida.de(entidade(id));
  }

  public Saida criar(Entrada input) {
    Cliente c = new Cliente();
    preencher(c, input);
    repo.save(c);
    log(c.id, "cliente_criado");
    return Saida.de(c);
  }

  public Saida atualizar(UUID id, Entrada input) {
    Cliente c = entidade(id);
    if (c.revisao != input.revisao())
      throw ApiException.conflict("Cliente alterado. Atualize a página.");
    preencher(c, input);
    log(id, "cliente_atualizado");
    return Saida.de(c);
  }

  private Cliente entidade(UUID id) {
    return repo.findByIdAndOficinaId(id, TenantContext.current())
        .orElseThrow(ApiException::missing);
  }

  private void preencher(Cliente c, Entrada i) {
    c.nome = i.nome().trim();
    c.telefone = i.telefone().trim();
    c.email = i.email() == null ? null : i.email().trim();
  }

  private void log(UUID id, String event) {
    LoggerFactory.getLogger(ClienteService.class).atInfo().addKeyValue("cliente_id", id).log(event);
  }
}
