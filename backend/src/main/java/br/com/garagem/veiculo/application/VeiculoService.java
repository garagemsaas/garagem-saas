package br.com.garagem.veiculo.application;

import br.com.garagem.cliente.repository.ClienteRepository;
import br.com.garagem.shared.error.ApiException;
import br.com.garagem.shared.persistence.Pagina;
import br.com.garagem.tenancy.TenantContext;
import br.com.garagem.veiculo.api.VeiculoDtos.*;
import br.com.garagem.veiculo.domain.Veiculo;
import br.com.garagem.veiculo.repository.VeiculoRepository;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class VeiculoService {
  private final VeiculoRepository repo;
  private final ClienteRepository clientes;

  public VeiculoService(VeiculoRepository repo, ClienteRepository clientes) {
    this.repo = repo;
    this.clientes = clientes;
  }

  @Transactional(readOnly = true)
  public Pagina<Saida> listar(String busca, int pagina, int tamanho) {
    return Pagina.de(
        repo.findByOficinaIdAndPlacaContainingIgnoreCase(
                TenantContext.current(), normalizar(busca), Pagina.request(pagina, tamanho))
            .map(Saida::de));
  }

  @Transactional(readOnly = true)
  public Saida obter(UUID id) {
    return Saida.de(entidade(id));
  }

  public Saida criar(Entrada input) {
    Veiculo v = new Veiculo();
    preencher(v, input);
    repo.save(v);
    log(v.id, "veiculo_criado");
    return Saida.de(v);
  }

  public void atualizar(UUID id, Entrada input) {
    Veiculo v = entidade(id);
    if (v.revisao != input.revisao())
      throw ApiException.conflict("Veículo alterado. Atualize a página.");
    if (input.km() < v.km) throw ApiException.invalid("A quilometragem não pode diminuir.");
    preencher(v, input);
    log(id, "veiculo_atualizado");
  }

  private Veiculo entidade(UUID id) {
    return repo.findByIdAndOficinaId(id, TenantContext.current())
        .orElseThrow(ApiException::missing);
  }

  private void preencher(Veiculo v, Entrada i) {
    clientes
        .findByIdAndOficinaId(i.clienteId(), TenantContext.current())
        .orElseThrow(ApiException::missing);
    v.clienteId = i.clienteId();
    v.placa = normalizar(i.placa());
    v.marca = i.marca().trim();
    v.modelo = i.modelo().trim();
    v.ano = i.ano();
    v.km = i.km();
    v.cor = i.cor().trim();
  }

  private static String normalizar(String s) {
    return s.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
  }

  private void log(UUID id, String event) {
    LoggerFactory.getLogger(VeiculoService.class).atInfo().addKeyValue("veiculo_id", id).log(event);
  }
}
