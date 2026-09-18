package br.com.garagem.assinatura.application;

import br.com.garagem.assinatura.domain.*;
import br.com.garagem.assinatura.repository.*;
import br.com.garagem.shared.error.ApiException;
import br.com.garagem.shared.error.ErrorCodes;
import br.com.garagem.tenancy.TenantContext;
import java.time.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ponto único onde um limite de plano é consultado e aplicado. Controllers e serviços chamam os
 * métodos {@code garantir*} antes de criar o recurso; nenhum limite é recalculado em outro lugar,
 * nem confiado ao frontend.
 *
 * <p>A contagem é sempre feita no banco, dentro da oficina autenticada, e no instante da operação —
 * nunca a partir de um total mantido à parte, que sairia do lugar em qualquer falha parcial.
 */
@Service
public class PlanoLimiteService {
  /** Regra de negócio, não constante de infraestrutura: consumo é o que está gravado. */
  private static final String USUARIOS_ATIVOS =
      "select count(*) from usuario where oficina_id=:t and ativo=true";

  private static final String ARMAZENAMENTO =
      "select coalesce(sum(tamanho),0) from foto_veiculo where oficina_id=:t";
  private static final String VEICULOS = "select count(*) from veiculo where oficina_id=:t";
  private static final String ORDENS_NO_MES =
      "select count(*) from ordem_servico where oficina_id=:t and criado_em>=:inicio";

  private final NamedParameterJdbcTemplate jdbc;
  private final CicloAssinatura ciclo;
  private final PlanoRepository planos;
  private final Clock clock;

  public PlanoLimiteService(
      NamedParameterJdbcTemplate jdbc, CicloAssinatura ciclo, PlanoRepository planos, Clock clock) {
    this.jdbc = jdbc;
    this.ciclo = ciclo;
    this.planos = planos;
    this.clock = clock;
  }

  /**
   * Assinatura da oficina autenticada, travada e com o estado temporal aplicado.
   *
   * <p>Antes lia o estado cru. Duas consequências: uma tolerância vencida nunca virava suspensão
   * fora da tela de cobrança, e as contagens de limite corriam sem serialização nenhuma.
   */
  public Assinatura assinatura() {
    return ciclo.atual();
  }

  @Transactional(readOnly = true)
  public Plano plano(Assinatura a) {
    return planos.findById(a.planoId).orElseThrow(ApiException::missing);
  }

  public record Consumo(long usuarios, long armazenamentoBytes, long veiculos, long ordensNoMes) {}

  @Transactional(readOnly = true)
  public Consumo consumo() {
    return new Consumo(
        contar(USUARIOS_ATIVOS, null),
        contar(ARMAZENAMENTO, null),
        contar(VEICULOS, null),
        contar(ORDENS_NO_MES, inicioDoMes()));
  }

  private long contar(String sql, Instant inicio) {
    var p = new MapSqlParameterSource("t", TenantContext.current());
    if (inicio != null) p.addValue("inicio", java.sql.Timestamp.from(inicio));
    Long valor = jdbc.queryForObject(sql, p, Long.class);
    return valor == null ? 0 : valor;
  }

  /** Mês corrente no fuso da operação, não em UTC: o cliente conta o mês pelo calendário dele. */
  private Instant inicioDoMes() {
    var zona = ZoneId.of("America/Sao_Paulo");
    return clock
        .instant()
        .atZone(zona)
        .withDayOfMonth(1)
        .toLocalDate()
        .atStartOfDay(zona)
        .toInstant();
  }

  /**
   * Porta única das operações que aumentam consumo. Suspensa ou cancelada continua lendo tudo, mas
   * não cria: bloquear é preservar, nunca apagar.
   */
  @Transactional
  public Assinatura garantirOperacional(String acao) {
    // A leitura trava a linha da assinatura. Toda contagem de limite depois disto acontece
    // serializada por oficina: duas criações concorrentes deixam de ver o mesmo total.
    var a = assinatura();
    if (!a.status.permiteCrescer())
      throw new ApiException(
          HttpStatus.PAYMENT_REQUIRED,
          ErrorCodes.SUBSCRIPTION_INACTIVE,
          a.status == StatusAssinatura.CANCELADA
              ? "Assinatura cancelada. Reative o plano para "
                  + acao
                  + "; seus dados continuam disponíveis para consulta."
              : "Assinatura suspensa por falta de pagamento. Regularize para "
                  + acao
                  + "; a consulta aos dados segue liberada.");
    return a;
  }

  @Transactional
  public void garantirNovoUsuario() {
    var plano = plano(garantirOperacional("cadastrar usuários"));
    long usados = contar(USUARIOS_ATIVOS, null);
    if (usados >= plano.maxUsuarios)
      throw limite(
          ErrorCodes.PLAN_LIMIT_REACHED,
          "Seu plano permite %d usuário(s) ativo(s) e você já usa %d. Desative um usuário ou mude de plano."
              .formatted(plano.maxUsuarios, usados));
  }

  @Transactional
  public void garantirArmazenamento(long bytesAdicionais) {
    var plano = plano(garantirOperacional("enviar arquivos"));
    long usados = contar(ARMAZENAMENTO, null);
    if (usados + bytesAdicionais > plano.maxArmazenamentoBytes)
      throw limite(
          ErrorCodes.STORAGE_LIMIT_REACHED,
          "O limite de armazenamento do seu plano foi atingido (%s de %s). Libere espaço ou mude de plano."
              .formatted(legivel(usados), legivel(plano.maxArmazenamentoBytes)));
  }

  @Transactional
  public void garantirNovoVeiculo() {
    var plano = plano(garantirOperacional("cadastrar veículos"));
    if (plano.maxVeiculos == null) return;
    long usados = contar(VEICULOS, null);
    if (usados >= plano.maxVeiculos)
      throw limite(
          ErrorCodes.PLAN_LIMIT_REACHED,
          "Seu plano permite %d veículo(s) e você já cadastrou %d. Mude de plano para cadastrar mais."
              .formatted(plano.maxVeiculos, usados));
  }

  @Transactional
  public void garantirNovaOrdemServico() {
    var plano = plano(garantirOperacional("abrir ordens de serviço"));
    if (plano.maxOrdensServicoMes == null) return;
    long usados = contar(ORDENS_NO_MES, inicioDoMes());
    if (usados >= plano.maxOrdensServicoMes)
      throw limite(
          ErrorCodes.PLAN_LIMIT_REACHED,
          "Seu plano permite %d ordem(ns) de serviço por mês e você já abriu %d. Mude de plano para continuar."
              .formatted(plano.maxOrdensServicoMes, usados));
  }

  /**
   * 402 e não 403: o papel do usuário está correto, o que falta é capacidade contratada. O frontend
   * decide pelo {@code code}, que separa limite de plano de armazenamento cheio.
   */
  private static ApiException limite(String code, String mensagem) {
    return new ApiException(HttpStatus.PAYMENT_REQUIRED, code, mensagem);
  }

  public static String legivel(long bytes) {
    if (bytes < 1024) return bytes + " B";
    String[] unidades = {"KB", "MB", "GB", "TB"};
    double valor = bytes;
    int i = -1;
    while (valor >= 1024 && i < unidades.length - 1) {
      valor /= 1024;
      i++;
    }
    return String.format(java.util.Locale.of("pt", "BR"), "%.1f %s", valor, unidades[i]);
  }

  /** Percentual consumido, com teto em 100 para não exibir barra estourada no frontend. */
  public static int percentual(long usado, Long limite) {
    if (limite == null || limite <= 0) return 0;
    return (int) Math.min(100, Math.round(usado * 100.0 / limite));
  }

  public UUID tenant() {
    return TenantContext.current();
  }
}
