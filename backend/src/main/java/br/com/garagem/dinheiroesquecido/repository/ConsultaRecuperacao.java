package br.com.garagem.dinheiroesquecido.repository;

import br.com.garagem.dinheiroesquecido.api.RecuperacaoDtos.*;
import br.com.garagem.dinheiroesquecido.domain.*;
import br.com.garagem.shared.error.ApiException;
import br.com.garagem.shared.persistence.Pagina;
import br.com.garagem.tenancy.TenantContext;
import java.math.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

/** Projeções e agregações JDBC parametrizadas, sempre com tenant explícito. */
@Repository
public class ConsultaRecuperacao {
  private final NamedParameterJdbcTemplate jdbc;
  private static final Map<String, String> ORDEM =
      Map.of(
          "criadoEm",
          "o.criado_em",
          "elegivelDesde",
          "o.elegivel_desde",
          "proximoContatoEm",
          "o.proximo_contato_em",
          "valorPotencial",
          "o.valor_potencial",
          "status",
          "o.status",
          "tipo",
          "o.tipo",
          "id",
          "o.id");
  private static final String BASE = " from oportunidade_recuperacao o where o.oficina_id=:tenant";
  private static final String ATIVAS = "('ABERTA','EM_CONTATO','AGENDADA')";
  private static final String IDADE =
      "floor(extract(epoch from (coalesce(o.encerrada_em,:agora)-o.elegivel_desde))/86400)";
  private static final String FAIXA =
      "case when "
          + IDADE
          + "<=7 then 'DIAS_0_7' when "
          + IDADE
          + "<=15 then 'DIAS_8_15' when "
          + IDADE
          + "<=30 then 'DIAS_16_30' when "
          + IDADE
          + "<=60 then 'DIAS_31_60' else 'MAIS_60' end";

  public ConsultaRecuperacao(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private MapSqlParameterSource parametros(Instant agora) {
    return new MapSqlParameterSource("tenant", TenantContext.current())
        .addValue("agora", Timestamp.from(agora));
  }

  private String filtro(Filtro f, MapSqlParameterSource p) {
    intervalo(f.de(), f.ate());
    intervalo(f.proximoContatoDe(), f.proximoContatoAte());
    StringBuilder s = new StringBuilder();
    adicionar(s, p, "tipo", "o.tipo=", f.tipo() == null ? null : f.tipo().name());
    adicionar(s, p, "status", "o.status=", f.status() == null ? null : f.status().name());
    adicionar(s, p, "responsavel", "o.responsavel_id=", f.responsavelId());
    adicionar(s, p, "cliente", "o.cliente_id=", f.clienteId());
    adicionar(s, p, "veiculo", "o.veiculo_id=", f.veiculoId());
    adicionar(s, p, "de", "o.criado_em>=", ts(f.de()));
    adicionar(s, p, "ate", "o.criado_em<=", ts(f.ate()));
    adicionar(s, p, "contatoDe", "o.proximo_contato_em>=", ts(f.proximoContatoDe()));
    adicionar(s, p, "contatoAte", "o.proximo_contato_em<=", ts(f.proximoContatoAte()));
    adicionar(
        s, p, "idade", "(" + FAIXA + ")=", f.faixaIdade() == null ? null : f.faixaIdade().name());
    return s.toString();
  }

  private static void adicionar(
      StringBuilder s, MapSqlParameterSource p, String chave, String sql, Object valor) {
    if (valor != null) {
      s.append(" and ").append(sql).append(":").append(chave);
      p.addValue(chave, valor);
    }
  }

  public static void intervalo(Instant de, Instant ate) {
    if (de != null && ate != null && de.isAfter(ate))
      throw ApiException.invalid("O início deve ser anterior ao fim do período.");
  }

  private static Timestamp ts(Instant i) {
    return i == null ? null : Timestamp.from(i);
  }

  private static Instant instante(ResultSet r, String campo) throws SQLException {
    Timestamp t = r.getTimestamp(campo);
    return t == null ? null : t.toInstant();
  }

  private static final String PROJECAO =
      """
      select o.*, c.nome cliente_nome,c.telefone,c.email,v.placa,v.marca,v.modelo,
      u.nome responsavel_nome,os.numero numero_os
      from oportunidade_recuperacao o
      join cliente c on c.id=o.cliente_id and c.oficina_id=o.oficina_id
      join veiculo v on v.id=o.veiculo_id and v.oficina_id=o.oficina_id
      join ordem_servico os on os.id=o.ordem_servico_id and os.oficina_id=o.oficina_id
      left join usuario u on u.id=o.responsavel_id and u.oficina_id=o.oficina_id
      where o.oficina_id=:tenant
      """;

  private OportunidadeSaida saida(ResultSet r, Instant agora) throws SQLException {
    Instant elegivel = instante(r, "elegivel_desde"), fim = instante(r, "encerrada_em");
    UUID responsavel = r.getObject("responsavel_id", UUID.class);
    return new OportunidadeSaida(
        r.getObject("id", UUID.class),
        TipoOportunidade.valueOf(r.getString("tipo")),
        StatusOportunidade.valueOf(r.getString("status")),
        new ClienteResumo(
            r.getObject("cliente_id", UUID.class),
            r.getString("cliente_nome"),
            r.getString("telefone"),
            r.getString("email")),
        new VeiculoResumo(
            r.getObject("veiculo_id", UUID.class),
            r.getString("placa"),
            r.getString("marca"),
            r.getString("modelo")),
        new OrigemResumo(
            r.getObject("ordem_servico_id", UUID.class),
            r.getLong("numero_os"),
            r.getObject("orcamento_versao_id", UUID.class)),
        responsavel == null
            ? null
            : new ResponsavelResumo(responsavel, r.getString("responsavel_nome")),
        r.getBigDecimal("valor_potencial"),
        instante(r, "criado_em"),
        elegivel,
        Math.max(0, Duration.between(elegivel, fim == null ? agora : fim).toDays()),
        instante(r, "ultimo_contato_em"),
        instante(r, "proximo_contato_em"),
        fim,
        r.getLong("revisao"));
  }

  public Pagina<OportunidadeSaida> listar(
      Filtro f, int pagina, int tamanho, String ordem, Instant agora) {
    var page = Pagina.request(pagina, tamanho, ordem, ORDEM.keySet());
    var p = parametros(agora);
    String where = filtro(f, p);
    List<String> termos = new ArrayList<>();
    page.getSort()
        .forEach(
            o -> {
              String campo = ORDEM.get(o.getProperty());
              if (campo == null) throw ApiException.invalid("Ordenação inválida.");
              termos.add(campo + (o.isAscending() ? " asc" : " desc") + " nulls last");
            });
    p.addValue("limite", page.getPageSize()).addValue("offset", page.getOffset());
    var itens =
        jdbc.query(
            PROJECAO
                + where
                + " order by "
                + String.join(",", termos)
                + " limit :limite offset :offset",
            p,
            (r, n) -> saida(r, agora));
    long total = jdbc.queryForObject("select count(*)" + BASE + where, p, Long.class);
    return Pagina.de(new PageImpl<>(itens, page, total));
  }

  public OportunidadeSaida obter(UUID id, Instant agora) {
    var itens =
        jdbc.query(
            PROJECAO + " and o.id=:id",
            parametros(agora).addValue("id", id),
            (r, n) -> saida(r, agora));
    if (itens.isEmpty()) throw ApiException.missing();
    return itens.getFirst();
  }

  public record Origem(
      UUID os,
      UUID versao,
      UUID cliente,
      UUID veiculo,
      TipoOportunidade tipo,
      BigDecimal valor,
      Instant elegivel) {}

  public List<UUID> proximasOrigens(UUID depois, Instant agora) {
    var p = parametros(agora).addValue("depois", depois);
    return jdbc.query(
        """
        select os.id from ordem_servico os where os.oficina_id=:tenant and os.id>:depois
        and (exists(select 1 from origem_recuperacao r where r.oficina_id=:tenant
             and r.ordem_servico_id=os.id and r.elegivel_desde<=:agora)
          or exists(select 1 from oportunidade_recuperacao o where o.oficina_id=:tenant
             and o.ordem_servico_id=os.id and o.encerrada_em is null))
        order by os.id limit 100
        """,
        p,
        (r, n) -> r.getObject(1, UUID.class));
  }

  public List<Origem> origens(UUID os, Instant agora) {
    return jdbc.query(
        "select * from origem_recuperacao where oficina_id=:tenant and ordem_servico_id=:os and elegivel_desde<=:agora",
        parametros(agora).addValue("os", os),
        (r, n) ->
            new Origem(
                os,
                r.getObject("orcamento_versao_id", UUID.class),
                r.getObject("cliente_id", UUID.class),
                r.getObject("veiculo_id", UUID.class),
                TipoOportunidade.valueOf(r.getString("tipo")),
                r.getBigDecimal("valor_potencial"),
                instante(r, "elegivel_desde")));
  }

  public Resumo resumo(Instant de, Instant ate, Instant agora) {
    intervalo(de, ate);
    var p = parametros(agora);
    StringBuilder criacao = new StringBuilder(), encerramento = new StringBuilder();
    adicionar(criacao, p, "de", "o.criado_em>=", ts(de));
    adicionar(criacao, p, "ate", "o.criado_em<=", ts(ate));
    adicionar(encerramento, p, "de", "o.encerrada_em>=", ts(de));
    adicionar(encerramento, p, "ate", "o.encerrada_em<=", ts(ate));
    long abertas =
        jdbc.queryForObject(
            "select count(*)" + BASE + " and o.status in " + ATIVAS + criacao, p, Long.class);
    BigDecimal potencial =
        jdbc.queryForObject(
            "select coalesce(sum(o.valor_potencial),0)"
                + BASE
                + " and o.status in "
                + ATIVAS
                + criacao,
            p,
            BigDecimal.class);
    long recuperadas =
        jdbc.queryForObject(
            "select count(*)" + BASE + " and o.status='RECUPERADA'" + encerramento, p, Long.class);
    long perdidas =
        jdbc.queryForObject(
            "select count(*)" + BASE + " and o.status='PERDIDA'" + encerramento, p, Long.class);
    BigDecimal valor =
        jdbc.queryForObject(
            "select coalesce(sum(r.valor_recuperado),0) from resultado_oportunidade r join oportunidade_recuperacao o on o.id=r.oportunidade_id and o.oficina_id=r.oficina_id where o.oficina_id=:tenant"
                + encerramento,
            p,
            BigDecimal.class);
    BigDecimal taxa =
        recuperadas + perdidas == 0
            ? BigDecimal.ZERO.setScale(2)
            : BigDecimal.valueOf(recuperadas)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(recuperadas + perdidas), 2, RoundingMode.HALF_UP);
    return new Resumo(
        agora,
        abertas,
        potencial,
        valor,
        recuperadas,
        perdidas,
        taxa,
        grupos("o.tipo", criacao.toString(), p),
        grupos("o.status", criacao.toString(), p),
        grupos("coalesce(cast(o.responsavel_id as text),'SEM_RESPONSAVEL')", criacao.toString(), p),
        grupos(
            "to_char(o.criado_em at time zone 'America/Sao_Paulo','YYYY-MM')",
            criacao.toString(),
            p),
        grupos(FAIXA, criacao + " and o.status in " + ATIVAS, p));
  }

  private List<Grupo> grupos(String expressao, String filtro, MapSqlParameterSource p) {
    return jdbc.query(
        "select "
            + expressao
            + " chave,count(*) quantidade"
            + BASE
            + filtro
            + " group by 1 order by 1",
        p,
        (r, n) -> new Grupo(r.getString(1), r.getLong(2)));
  }
}
