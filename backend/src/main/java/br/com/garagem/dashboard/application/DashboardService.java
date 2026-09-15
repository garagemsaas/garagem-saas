package br.com.garagem.dashboard.application;

import br.com.garagem.dashboard.api.DashboardDtos.*;
import br.com.garagem.ordemservico.domain.StatusOs;
import br.com.garagem.shared.error.ApiException;
import br.com.garagem.tenancy.TenantContext;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agregações do painel. Todas as consultas filtram {@code oficina_id} pelo tenant do token, de modo
 * que os números de uma oficina nunca somam registros de outra.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {
  /** Fuso usado para delimitar "hoje" quando o cliente não informa outro. */
  public static final String FUSO_PADRAO = "America/Sao_Paulo";

  private static final Duration JANELA_CONCLUIDAS = Duration.ofDays(7);

  private final JdbcTemplate jdbc;

  public DashboardService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public DashboardSaida resumo(String fuso, Clock clock) {
    UUID oficina = TenantContext.current();
    ZoneId zona = zona(fuso);
    Instant agora = clock.instant();
    Timestamp inicioDoDia =
        Timestamp.from(agora.atZone(zona).toLocalDate().atStartOfDay(zona).toInstant());
    Timestamp corteConclusao = Timestamp.from(agora.minus(JANELA_CONCLUIDAS));
    Timestamp momento = Timestamp.from(agora);

    Map<StatusOs, Long> porStatus = new EnumMap<>(StatusOs.class);
    for (StatusOs status : StatusOs.values()) porStatus.put(status, 0L);
    jdbc.query(
        "select status, count(*) from ordem_servico where oficina_id=? group by status",
        rs -> {
          porStatus.put(StatusOs.valueOf(rs.getString(1)), rs.getLong(2));
        },
        oficina);

    long emAndamento =
        porStatus.entrySet().stream()
            .filter(e -> e.getKey() != StatusOs.PRONTO)
            .mapToLong(Map.Entry::getValue)
            .sum();

    long concluidasSeteDias =
        contar(
            "select count(*) from ordem_servico where oficina_id=? and concluida_em is not null and"
                + " concluida_em >= ?",
            oficina,
            corteConclusao);
    long entradasHoje =
        contar(
            "select count(*) from ordem_servico where oficina_id=? and criado_em >= ?",
            oficina,
            inicioDoDia);
    long atrasadas =
        contar(
            "select count(*) from ordem_servico where oficina_id=? and status <> 'PRONTO' and"
                + " previsao_entrega is not null and previsao_entrega < ?",
            oficina,
            momento);
    long semResponsavel =
        contar(
            "select count(*) from ordem_servico where oficina_id=? and status <> 'PRONTO' and"
                + " mecanico_id is null",
            oficina);

    var pendentes =
        jdbc.queryForObject(
            """
            with ultima as (
              select distinct on (v.orcamento_id) v.id, v.total, o.ordem_servico_id
                from orcamento_versao v
                join orcamento o on o.id = v.orcamento_id and o.oficina_id = v.oficina_id
               where v.oficina_id = ?
               order by v.orcamento_id, v.numero desc
            )
            select count(*), coalesce(sum(u.total), 0)
              from ultima u
              join ordem_servico os
                on os.id = u.ordem_servico_id and os.oficina_id = ?
              left join aprovacao_orcamento a
                on a.orcamento_versao_id = u.id and a.oficina_id = ?
             where os.status = 'AGUARDANDO_APROVACAO' and a.id is null
            """,
            (rs, n) -> new OrcamentosPendentes(rs.getLong(1), rs.getBigDecimal(2)),
            oficina,
            oficina,
            oficina);

    return new DashboardSaida(
        agora,
        porStatus,
        emAndamento,
        porStatus.get(StatusOs.PRONTO),
        concluidasSeteDias,
        entradasHoje,
        atrasadas,
        semResponsavel,
        pendentes == null ? new OrcamentosPendentes(0, BigDecimal.ZERO) : pendentes);
  }

  private long contar(String sql, Object... args) {
    Long total = jdbc.queryForObject(sql, Long.class, args);
    return total == null ? 0 : total;
  }

  private static ZoneId zona(String fuso) {
    if (fuso == null || fuso.isBlank()) return ZoneId.of(FUSO_PADRAO);
    try {
      return ZoneId.of(fuso.trim());
    } catch (DateTimeException e) {
      throw ApiException.invalid("Fuso horário inválido. Use um identificador IANA.");
    }
  }
}
