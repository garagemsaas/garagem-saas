package br.com.garagem.retorno;

import br.com.garagem.shared.error.ApiException;
import br.com.garagem.shared.seguranca.UsuarioAutenticado;
import br.com.garagem.tenancy.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@SemModulo
@RequestMapping("/api/v1/retornos")
@PreAuthorize("hasAnyRole('OWNER','ATENDENTE')")
@Transactional
public class RetornoController {
  private final JdbcTemplate jdbc;
  private final br.com.garagem.dinheiroesquecido.port.IdentificacaoRetornosPort identificacao;

  public RetornoController(
      JdbcTemplate jdbc,
      br.com.garagem.dinheiroesquecido.port.IdentificacaoRetornosPort identificacao) {
    this.jdbc = jdbc;
    this.identificacao = identificacao;
  }

  @PostMapping("/sugestoes")
  public Map<String, Integer> sugerir() {
    String modo = operacao();
    UUID tenant = TenantContext.current();
    // Uma origem só gera um retorno, inclusive quando já concluído ou cancelado.
    // A identificação da oficina continua no módulo que conhece orçamento e revisão.
    String source;
    if ("OFICINA".equals(modo)) {
      identificacao.identificar();
      source =
          """
        select o.id origem_id,'OPORTUNIDADE' origem_tipo,o.cliente_id,o.veiculo_id,o.responsavel_id,
        case o.tipo when 'ORCAMENTO_ESQUECIDO' then 'Consultar cliente sobre orçamento sem resposta'
        when 'REVISAO_ATRASADA' then 'Agendar revisão do veículo' else 'Conversar sobre orçamento recusado' end motivo,
        coalesce(o.proximo_contato_em,o.criado_em) agendado_em
        from oportunidade_recuperacao o where o.oficina_id=? and o.status in ('ABERTA','EM_CONTATO','AGENDADA')
        """;
    } else {
      source =
          """
        select l.id origem_id,'INTERESSADO' origem_tipo,l.cliente_id,l.veiculo_id,l.vendedor_id responsavel_id,
        'Retomar conversa sobre interesse no veículo' motivo,l.criado_em+interval '7 days' agendado_em
        from revenda_lead l where l.oficina_id=? and l.status in ('NOVO','CONTATO_REALIZADO','INTERESSADO','NEGOCIACAO')
        and greatest(l.criado_em,coalesce((select max(e.criado_em) from revenda_evento e where e.oficina_id=l.oficina_id and e.lead_id=l.id),l.criado_em))<=now()-interval '7 days'
        union all
        select p.id,'PROPOSTA',p.cliente_id,e.veiculo_id,p.vendedor_id,'Consultar cliente sobre proposta enviada',p.criado_em+interval '7 days'
        from revenda_proposta p join revenda_estoque e on e.id=p.estoque_id and e.oficina_id=p.oficina_id
        where p.oficina_id=? and p.status='ENVIADA' and p.criado_em<=now()-interval '7 days'
        union all
        select r.id,'RESERVA',r.cliente_id,e.veiculo_id,r.vendedor_id,'Retomar contato após fim da reserva',r.criado_em
        from revenda_reserva r join revenda_estoque e on e.id=r.estoque_id and e.oficina_id=r.oficina_id
        where r.oficina_id=? and r.status in ('CANCELADA','EXPIRADA')
        and not exists(select 1 from revenda_venda v where v.oficina_id=r.oficina_id and v.estoque_id=r.estoque_id)
        """;
    }
    var ids =
        jdbc.query(
            """
      insert into retorno(id,oficina_id,operacao,cliente_id,veiculo_id,responsavel_id,motivo,agendado_em,prioridade,origem_tipo,origem_id)
      select gen_random_uuid(),?,?,s.cliente_id,s.veiculo_id,coalesce(u.id,?),s.motivo,s.agendado_em,'NORMAL',s.origem_tipo,s.origem_id
      from (
      """
                + source
                + """
      ) s left join usuario u on u.id=s.responsavel_id and u.oficina_id=? and u.ativo and u.papel in ('OWNER','ATENDENTE')
      on conflict(oficina_id,origem_tipo,origem_id) do nothing returning id
      """,
            (r, n) -> r.getObject(1, UUID.class),
            argumentosSugestoes(tenant, modo));
    ids.forEach(id -> evento(id, "Retorno sugerido a partir de atendimento existente."));
    return Map.of("criados", ids.size());
  }

  private Object[] argumentosSugestoes(UUID tenant, String modo) {
    var values = new ArrayList<Object>(List.of(tenant, modo, UsuarioAutenticado.id(), tenant));
    if ("REVENDA".equals(modo)) {
      values.add(tenant);
      values.add(tenant);
    }
    values.add(tenant);
    return values.toArray();
  }

  @io.swagger.v3.oas.annotations.media.Schema(name = "RetornoEntrada")
  public record Entrada(
      @NotNull UUID clienteId,
      UUID veiculoId,
      @NotNull UUID responsavelId,
      @NotBlank @Size(max = 500) String motivo,
      @NotNull Instant agendadoEm,
      @NotBlank @Pattern(regexp = "NORMAL|ALTA") String prioridade,
      @Size(max = 2000) String observacoes,
      @NotNull @PositiveOrZero Long revisao) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "RetornoEncerramento")
  public record Encerramento(
      @NotNull @PositiveOrZero Long revisao,
      @NotBlank @Pattern(regexp = "CONCLUIDO|CANCELADO") String status,
      @NotBlank @Size(max = 2000) String resultado) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "RetornoSaida")
  public record Retorno(
      UUID id,
      UUID clienteId,
      String cliente,
      String telefone,
      UUID veiculoId,
      String veiculo,
      UUID responsavelId,
      String responsavel,
      String motivo,
      Instant agendadoEm,
      String prioridade,
      String status,
      String observacoes,
      String resultado,
      long revisao) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "RetornoPagina")
  public record Lista(List<Retorno> itens, long total, int pagina, int tamanho) {}

  private String operacao() {
    return jdbc.queryForObject(
        "select modulo from empresa_modulo where oficina_id=?",
        String.class,
        TenantContext.current());
  }

  private static final String SELECT =
      """
    select r.*,c.nome cliente,c.telefone,concat_ws(' ',v.marca,v.modelo,v.placa) veiculo,u.nome responsavel
    from retorno r join cliente c on c.id=r.cliente_id and c.oficina_id=r.oficina_id
    left join veiculo v on v.id=r.veiculo_id and v.oficina_id=r.oficina_id
    join usuario u on u.id=r.responsavel_id and u.oficina_id=r.oficina_id
    """;

  private static Retorno map(java.sql.ResultSet r, int n) throws java.sql.SQLException {
    return new Retorno(
        r.getObject("id", UUID.class),
        r.getObject("cliente_id", UUID.class),
        r.getString("cliente"),
        r.getString("telefone"),
        r.getObject("veiculo_id", UUID.class),
        r.getString("veiculo"),
        r.getObject("responsavel_id", UUID.class),
        r.getString("responsavel"),
        r.getString("motivo"),
        r.getTimestamp("agendado_em").toInstant(),
        r.getString("prioridade"),
        r.getString("status"),
        r.getString("observacoes"),
        r.getString("resultado"),
        r.getLong("revisao"));
  }

  @GetMapping
  @Transactional(readOnly = true)
  public Lista listar(
      @RequestParam(defaultValue = "") String busca,
      @RequestParam(defaultValue = "PENDENTE") String status,
      @RequestParam(defaultValue = "false") boolean meus,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "10") int tamanho) {
    if (!Set.of("PENDENTE", "CONCLUIDO", "CANCELADO", "TODOS").contains(status)
        || busca.length() > 160
        || pagina < 0
        || pagina > 100000
        || tamanho < 1
        || tamanho > 100) throw ApiException.invalid("Filtros inválidos.");
    String where =
        " where r.oficina_id=? and r.operacao=? and (?='TODOS' or r.status=?) and lower(c.nome || ' ' || r.motivo || ' ' || coalesce(v.placa,'')) like ? and (?=false or r.responsavel_id=?)";
    var params =
        new ArrayList<Object>(
            List.of(
                TenantContext.current(),
                operacao(),
                status,
                status,
                "%" + busca.trim().toLowerCase(Locale.ROOT) + "%",
                meus,
                UsuarioAutenticado.id()));
    Long total =
        jdbc.queryForObject(
            "select count(*) from (" + SELECT + where + ") agenda", Long.class, params.toArray());
    params.add(tamanho);
    params.add(pagina * tamanho);
    var itens =
        jdbc.query(
            SELECT
                + where
                + " order by case when r.prioridade='ALTA' then 0 else 1 end,r.agendado_em,r.id limit ? offset ?",
            RetornoController::map,
            params.toArray());
    return new Lista(itens, total == null ? 0 : total, pagina, tamanho);
  }

  @GetMapping("/{id}")
  @Transactional(readOnly = true)
  public Retorno obter(@PathVariable UUID id) {
    var rows =
        jdbc.query(
            SELECT + " where r.oficina_id=? and r.operacao=? and r.id=?",
            RetornoController::map,
            TenantContext.current(),
            operacao(),
            id);
    if (rows.isEmpty()) throw ApiException.missing();
    return rows.getFirst();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Retorno criar(@Valid @RequestBody Entrada e) {
    validar(e);
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into retorno(id,oficina_id,operacao,cliente_id,veiculo_id,responsavel_id,motivo,agendado_em,prioridade,observacoes) values(?,?,?,?,?,?,?,?,?,?)",
        id,
        TenantContext.current(),
        operacao(),
        e.clienteId(),
        e.veiculoId(),
        e.responsavelId(),
        e.motivo().trim(),
        Timestamp.from(e.agendadoEm()),
        e.prioridade(),
        e.observacoes());
    evento(id, "Retorno agendado.");
    return obter(id);
  }

  @PutMapping("/{id}")
  public Retorno editar(@PathVariable UUID id, @Valid @RequestBody Entrada e) {
    var anterior = obter(id);
    validar(e);
    if (jdbc.update(
            "update retorno set cliente_id=?,veiculo_id=?,responsavel_id=?,motivo=?,agendado_em=?,prioridade=?,observacoes=?,revisao=revisao+1 where oficina_id=? and operacao=? and id=? and revisao=? and status='PENDENTE'",
            e.clienteId(),
            e.veiculoId(),
            e.responsavelId(),
            e.motivo().trim(),
            Timestamp.from(e.agendadoEm()),
            e.prioridade(),
            e.observacoes(),
            TenantContext.current(),
            operacao(),
            id,
            e.revisao())
        != 1) throw ApiException.conflict("O retorno mudou ou foi encerrado. Atualize a lista.");
    evento(
        id,
        anterior.agendadoEm().equals(e.agendadoEm())
            ? "Retorno editado."
            : "Retorno reagendado de " + anterior.agendadoEm() + " para " + e.agendadoEm() + ".");
    return obter(id);
  }

  @PutMapping("/{id}/situacao")
  public Retorno encerrar(@PathVariable UUID id, @Valid @RequestBody Encerramento e) {
    obter(id);
    if (jdbc.update(
            "update retorno set status=?,resultado=?,encerrado_em=now(),revisao=revisao+1 where oficina_id=? and operacao=? and id=? and revisao=? and status='PENDENTE'",
            e.status(),
            e.resultado().trim(),
            TenantContext.current(),
            operacao(),
            id,
            e.revisao())
        != 1) throw ApiException.conflict("O retorno mudou ou foi encerrado. Atualize a lista.");
    evento(id, e.status().equals("CONCLUIDO") ? "Retorno concluído." : "Retorno cancelado.");
    return obter(id);
  }

  @GetMapping("/{id}/historico")
  @Transactional(readOnly = true)
  public List<Map<String, Object>> historico(
      @PathVariable UUID id, @RequestParam(defaultValue = "0") int pagina) {
    obter(id);
    if (pagina < 0 || pagina > 100000) throw ApiException.invalid("Página inválida.");
    return jdbc.queryForList(
        "select e.id,e.descricao,e.criado_em,u.nome autor from retorno_evento e join usuario u on u.id=e.autor_id and u.oficina_id=e.oficina_id where e.oficina_id=? and e.retorno_id=? order by e.id desc limit 20 offset ?",
        TenantContext.current(),
        id,
        pagina * 20);
  }

  private void validar(Entrada e) {
    UUID tenant = TenantContext.current();
    if (!Objects.equals(
        jdbc.queryForObject(
            "select count(*) from cliente where oficina_id=? and id=?",
            Integer.class,
            tenant,
            e.clienteId()),
        1)) throw ApiException.missing();
    if (e.veiculoId() != null
        && !Objects.equals(
            jdbc.queryForObject(
                "select count(*) from veiculo where oficina_id=? and id=?",
                Integer.class,
                tenant,
                e.veiculoId()),
            1)) throw ApiException.missing();
    if (!Objects.equals(
        jdbc.queryForObject(
            "select count(*) from usuario where oficina_id=? and id=? and ativo and papel in ('OWNER','ATENDENTE')",
            Integer.class,
            tenant,
            e.responsavelId()),
        1)) throw ApiException.invalid("Escolha um responsável com acesso comercial ativo.");
  }

  private void evento(UUID id, String texto) {
    jdbc.update(
        "insert into retorno_evento(oficina_id,retorno_id,autor_id,descricao) values(?,?,?,?)",
        TenantContext.current(),
        id,
        UsuarioAutenticado.id(),
        texto);
  }
}
