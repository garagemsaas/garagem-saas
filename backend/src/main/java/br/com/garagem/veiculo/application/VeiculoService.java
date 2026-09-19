package br.com.garagem.veiculo.application;

import br.com.garagem.cliente.port.ClientePort;
import br.com.garagem.shared.error.ApiException;
import br.com.garagem.shared.persistence.Filtros;
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
  private final ClientePort clientes;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  public VeiculoService(
      VeiculoRepository repo,
      ClientePort clientes,
      org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.repo = repo;
    this.clientes = clientes;
    this.jdbc = jdbc;
  }

  /** Campos que a listagem aceita em {@code ordenacao}; qualquer outro é recusado com 400. */
  public static final Set<String> ORDENACAO =
      Set.of("placa", "marca", "modelo", "ano", "km", "criadoEm");

  @Transactional(readOnly = true)
  public Pagina<Saida> listar(
      String busca,
      String placa,
      String marca,
      String modelo,
      UUID clienteId,
      int pagina,
      int tamanho,
      String ordenacao) {
    return Pagina.de(
        repo.filtrar(
                TenantContext.current(),
                Filtros.like(busca),
                buscaPorPlaca(busca),
                Filtros.like(normalizarBusca(placa)),
                Filtros.like(marca),
                Filtros.like(modelo),
                clienteId,
                Pagina.request(pagina, tamanho, ordenacao, ORDENACAO))
            .map(Saida::de));
  }

  /** A placa é gravada sem separadores; o filtro remove hífen e espaço antes de comparar. */
  private static String normalizarBusca(String s) {
    return s == null ? null : normalizar(s);
  }

  /**
   * Forma do campo único de pesquisa usada só contra a placa. Marca e modelo continuam comparados
   * com o texto original: normalizá-los faria "Fiat Uno" e "CR-V" não encontrarem nada. Quando a
   * pesquisa só tem separadores a forma normalizada fica vazia, e aí vale o texto original, para
   * que os dois parâmetros nunca cheguem nulos separadamente e anulem o predicado.
   */
  private static String buscaPorPlaca(String busca) {
    String normalizada = Filtros.like(normalizarBusca(busca));
    return normalizada == null ? Filtros.like(busca) : normalizada;
  }

  @Transactional(readOnly = true)
  public Saida obter(UUID id) {
    return Saida.de(entidade(id));
  }

  public Saida criar(Entrada input) {
    auditar("Cadastro inicial do veículo");
    Veiculo v = new Veiculo();
    preencher(v, input);
    repo.save(v);
    log(v.id, "veiculo_criado");
    return Saida.de(v);
  }

  public void atualizar(UUID id, Entrada input) {
    Veiculo v = repo.lock(id, TenantContext.current()).orElseThrow(ApiException::missing);
    if (v.revisao != input.revisao())
      throw ApiException.conflict("Veículo alterado. Atualize a página.");
    if (input.km() < v.km) throw ApiException.invalid("A quilometragem não pode diminuir.");
    if ("EMPRESA".equals(v.propriedade) && input.clienteId() != null)
      throw ApiException.conflict("A propriedade de veículo da empresa é transferida pela venda.");
    auditar("Responsável pelo cadastro atualizou a propriedade");
    preencher(v, input);
    log(id, "veiculo_atualizado");
  }

  private Veiculo entidade(UUID id) {
    return repo.findByIdAndOficinaId(id, TenantContext.current())
        .orElseThrow(ApiException::missing);
  }

  private void preencher(Veiculo v, Entrada i) {
    if (i.clienteId() != null && !clientes.existe(i.clienteId())) throw ApiException.missing();
    if ((i.placa() == null || i.placa().isBlank()) && (i.chassi() == null || i.chassi().isBlank()))
      throw ApiException.invalid("Informe placa ou chassi para identificar o veículo.");
    v.clienteId = i.clienteId();
    if (!"EMPRESA".equals(v.propriedade))
      v.propriedade = i.clienteId() == null ? "NAO_INFORMADA" : "CLIENTE";
    v.placa = normalizar(i.placa());
    v.marca = i.marca().trim();
    v.modelo = i.modelo().trim();
    v.ano = i.ano();
    v.km = i.km();
    v.cor = i.cor().trim();
    v.versao = i.versao();
    v.anoModelo = i.anoModelo() == null ? i.ano() : i.anoModelo();
    v.chassi = normalizar(i.chassi());
    v.renavam = i.renavam();
    v.combustivel = i.combustivel();
    v.cambio = i.cambio();
    v.observacoes = i.observacoes();
  }

  private static String normalizar(String s) {
    return s == null ? null : s.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
  }

  private void auditar(String motivo) {
    jdbc.queryForObject(
        "select set_config('app.autor_propriedade',?,true)",
        String.class,
        br.com.garagem.shared.seguranca.UsuarioAutenticado.id().toString());
    jdbc.queryForObject("select set_config('app.motivo_propriedade',?,true)", String.class, motivo);
  }

  public record PropriedadeHistorico(
      long id,
      String propriedadeAnterior,
      UUID clienteAnteriorId,
      String propriedadeAtual,
      UUID clienteAtualId,
      UUID autorId,
      String motivo,
      java.time.Instant criadoEm) {}

  @Transactional(readOnly = true)
  public Pagina<PropriedadeHistorico> historico(UUID id, int pagina, int tamanho) {
    entidade(id);
    var page = Pagina.request(pagina, tamanho);
    var rows =
        jdbc.query(
            "select * from veiculo_propriedade_historico where oficina_id=? and veiculo_id=? order by id desc limit ? offset ?",
            (r, n) ->
                new PropriedadeHistorico(
                    r.getLong("id"),
                    r.getString("propriedade_anterior"),
                    r.getObject("cliente_anterior_id", UUID.class),
                    r.getString("propriedade_atual"),
                    r.getObject("cliente_atual_id", UUID.class),
                    r.getObject("autor_id", UUID.class),
                    r.getString("motivo"),
                    r.getTimestamp("criado_em").toInstant()),
            TenantContext.current(),
            id,
            page.getPageSize(),
            page.getOffset());
    long total =
        jdbc.queryForObject(
            "select count(*) from veiculo_propriedade_historico where oficina_id=? and veiculo_id=?",
            Long.class,
            TenantContext.current(),
            id);
    return new Pagina<>(
        rows,
        page.getPageNumber(),
        page.getPageSize(),
        total,
        (int) ((total + page.getPageSize() - 1) / page.getPageSize()));
  }

  private void log(UUID id, String event) {
    LoggerFactory.getLogger(VeiculoService.class).atInfo().addKeyValue("veiculo_id", id).log(event);
  }
}
