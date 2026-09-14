package br.com.garagem.cliente.application;

import br.com.garagem.cliente.api.ClienteDtos.*;
import br.com.garagem.cliente.domain.Cliente;
import br.com.garagem.cliente.repository.ClienteRepository;
import br.com.garagem.shared.error.ApiException;
import br.com.garagem.shared.persistence.Pagina;
import br.com.garagem.tenancy.TenantContext;
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

  @Transactional(readOnly = true)
  public Pagina<Saida> listar(String busca, int pagina, int tamanho) {
    return Pagina.de(
        repo.findByOficinaIdAndNomeContainingIgnoreCase(
                TenantContext.current(), busca, Pagina.request(pagina, tamanho))
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
