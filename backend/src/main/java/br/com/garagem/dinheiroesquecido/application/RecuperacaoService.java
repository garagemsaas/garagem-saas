package br.com.garagem.dinheiroesquecido.application;

import br.com.garagem.dinheiroesquecido.api.RecuperacaoDtos.*;
import br.com.garagem.dinheiroesquecido.domain.*;
import br.com.garagem.dinheiroesquecido.repository.*;
import br.com.garagem.ordemservico.acessopublico.aprovacao.repository.AprovacaoOrcamentoRepository;
import br.com.garagem.ordemservico.application.OsService;
import br.com.garagem.ordemservico.domain.StatusOs;
import br.com.garagem.ordemservico.orcamento.repository.OrcamentoRepository;
import br.com.garagem.ordemservico.orcamento.versao.repository.OrcamentoVersaoRepository;
import br.com.garagem.ordemservico.repository.OrdemServicoRepository;
import br.com.garagem.shared.error.ApiException;
import br.com.garagem.shared.persistence.Pagina;
import br.com.garagem.tenancy.TenantContext;
import br.com.garagem.usuario.domain.Papel;
import br.com.garagem.usuario.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RecuperacaoService {
  private final OportunidadeRecuperacaoRepository oportunidades;
  private final ContatoOportunidadeRepository contatos;
  private final ResultadoOportunidadeRepository resultados;
  private final EventoOportunidadeRepository eventos;
  private final AcompanhamentoOrcamentoRepository acompanhamentos;
  private final ConsultaRecuperacao consultas;
  private final OrdemServicoRepository ordens;
  private final OrcamentoVersaoRepository versoes;
  private final OrcamentoRepository orcamentos;
  private final AprovacaoOrcamentoRepository decisoes;
  private final UsuarioRepository usuarios;
  private final OsService osService;
  private final EntityManager em;
  private final Clock clock;

  public RecuperacaoService(
      OportunidadeRecuperacaoRepository oportunidades,
      ContatoOportunidadeRepository contatos,
      ResultadoOportunidadeRepository resultados,
      EventoOportunidadeRepository eventos,
      AcompanhamentoOrcamentoRepository acompanhamentos,
      ConsultaRecuperacao consultas,
      OrdemServicoRepository ordens,
      OrcamentoVersaoRepository versoes,
      OrcamentoRepository orcamentos,
      AprovacaoOrcamentoRepository decisoes,
      UsuarioRepository usuarios,
      OsService osService,
      EntityManager em,
      Clock clock) {
    this.oportunidades = oportunidades;
    this.contatos = contatos;
    this.resultados = resultados;
    this.eventos = eventos;
    this.acompanhamentos = acompanhamentos;
    this.consultas = consultas;
    this.ordens = ordens;
    this.versoes = versoes;
    this.orcamentos = orcamentos;
    this.decisoes = decisoes;
    this.usuarios = usuarios;
    this.osService = osService;
    this.em = em;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public Pagina<OportunidadeSaida> listar(Filtro filtro, int pagina, int tamanho, String ordem) {
    return consultas.listar(filtro, pagina, tamanho, ordem, clock.instant());
  }

  @Transactional(readOnly = true)
  public Resumo resumo(Instant de, Instant ate) {
    return consultas.resumo(de, ate, clock.instant());
  }

  @Transactional(readOnly = true)
  public Detalhe detalhe(UUID id) {
    var o = consultas.obter(id, clock.instant());
    UUID t = TenantContext.current();
    return new Detalhe(
        o,
        contatos.findByOportunidadeIdAndOficinaIdOrderByCriadoEmAscIdAsc(id, t).stream()
            .map(
                c ->
                    new ContatoSaida(
                        c.id,
                        c.usuarioId,
                        c.criadoEm,
                        c.canal,
                        c.resultado,
                        c.observacao,
                        c.proximoContatoEm))
            .toList(),
        resultados.findByOportunidadeIdAndOficinaIdOrderByCriadoEmAscIdAsc(id, t).stream()
            .map(
                r ->
                    new ResultadoSaida(
                        r.id,
                        r.usuarioId,
                        r.criadoEm,
                        r.valorRecuperado,
                        r.ordemServicoId,
                        r.observacao))
            .findFirst()
            .orElse(null),
        eventos.findByOportunidadeIdAndOficinaIdOrderByCriadoEmAscIdAsc(id, t).stream()
            .map(
                e ->
                    new EventoSaida(
                        e.id, e.usuarioId, e.criadoEm, e.tipo, e.anterior, e.novo, e.observacao))
            .toList());
  }

  private OportunidadeRecuperacao bloquear(UUID id, long revisao) {
    var o = oportunidades.lock(id, TenantContext.current()).orElseThrow(ApiException::missing);
    if (o.revisao != revisao)
      throw ApiException.conflict("A oportunidade mudou. Atualize a página.");
    if (o.status.encerrada()) throw ApiException.conflict("A oportunidade já está encerrada.");
    return o;
  }

  private OportunidadeSaida resposta(OportunidadeRecuperacao o) {
    em.flush();
    return consultas.obter(o.id, clock.instant());
  }

  private void evento(
      OportunidadeRecuperacao o, String tipo, Object anterior, Object novo, String obs) {
    var e = new EventoOportunidade();
    e.oportunidadeId = o.id;
    e.usuarioId = OsService.autor();
    e.tipo = tipo;
    e.anterior = anterior == null ? null : anterior.toString();
    e.novo = novo == null ? null : novo.toString();
    e.observacao = obs;
    e.criadoEm = clock.instant();
    eventos.save(e);
  }

  private void transicao(OportunidadeRecuperacao o, StatusOportunidade destino, String obs) {
    if (o.status == destino) return;
    evento(o, "STATUS_ALTERADO", o.status, destino, obs);
    o.status = destino;
    if (destino.encerrada()) {
      o.encerradaEm = clock.instant();
      proximo(o, null);
      evento(o, "ENCERRAMENTO", null, destino, obs);
    }
  }

  private void proximo(OportunidadeRecuperacao o, Instant data) {
    if (!Objects.equals(data, o.proximoContatoEm)) {
      evento(o, "PROXIMO_CONTATO_ALTERADO", o.proximoContatoEm, data, null);
      o.proximoContatoEm = data;
    }
  }

  private void validarProximo(Instant data) {
    if (data != null && !data.isAfter(clock.instant()))
      throw ApiException.invalid("Próximo contato deve estar no futuro.");
  }

  public OportunidadeSaida contato(UUID id, ContatoEntrada input) {
    var o = bloquear(id, input.revisao());
    validarProximo(input.proximoContatoEm());
    if (input.resultado() == ResultadoContato.AGENDADO && input.proximoContatoEm() == null)
      throw ApiException.invalid("Informe a data do próximo contato para agendar.");
    var c = new ContatoOportunidade();
    c.oportunidadeId = id;
    c.usuarioId = OsService.autor();
    c.criadoEm = clock.instant();
    c.canal = input.canal();
    c.resultado = input.resultado();
    c.observacao = input.observacao();
    c.proximoContatoEm = input.proximoContatoEm();
    contatos.save(c);
    o.ultimoContatoEm = c.criadoEm;
    evento(o, "CONTATO_REGISTRADO", null, c.id, input.resultado().name());
    proximo(o, input.proximoContatoEm());
    transicao(
        o,
        input.resultado() == ResultadoContato.AGENDADO
            ? StatusOportunidade.AGENDADA
            : StatusOportunidade.EM_CONTATO,
        null);
    return resposta(o);
  }

  public OportunidadeSaida resultado(UUID id, ResultadoEntrada input) {
    var o = bloquear(id, input.revisao());
    if (input.ordemServicoId() != null)
      ordens
          .findByIdAndOficinaId(input.ordemServicoId(), TenantContext.current())
          .orElseThrow(ApiException::missing);
    var r = new ResultadoOportunidade();
    r.oportunidadeId = id;
    r.usuarioId = OsService.autor();
    r.criadoEm = clock.instant();
    r.valorRecuperado = input.valorRecuperado().setScale(2);
    r.ordemServicoId = input.ordemServicoId();
    r.observacao = input.observacao();
    resultados.save(r);
    evento(o, "RESULTADO_REGISTRADO", null, r.id, input.observacao());
    evento(o, "VALOR_RECUPERADO", null, r.valorRecuperado, null);
    transicao(o, StatusOportunidade.RECUPERADA, input.observacao());
    return resposta(o);
  }

  public OportunidadeSaida status(UUID id, StatusEntrada input) {
    var o = bloquear(id, input.revisao());
    if (input.status() == StatusOportunidade.RECUPERADA
        || input.status() == StatusOportunidade.ABERTA)
      throw ApiException.conflict(
          "Recuperação exige registro de resultado; reabertura não é permitida.");
    if (input.status() == StatusOportunidade.AGENDADA && o.proximoContatoEm == null)
      throw ApiException.conflict("Defina o próximo contato antes de agendar.");
    if (input.status() == StatusOportunidade.PERDIDA && o.ultimoContatoEm == null)
      throw ApiException.conflict(
          "Registre uma tentativa de contato antes de encerrar como perdida.");
    if (input.status().encerrada() && (input.observacao() == null || input.observacao().isBlank()))
      throw ApiException.invalid("Informe o motivo do encerramento.");
    transicao(o, input.status(), input.observacao());
    return resposta(o);
  }

  public OportunidadeSaida responsavel(UUID id, ResponsavelEntrada input) {
    var o = bloquear(id, input.revisao());
    if (input.responsavelId() != null) {
      var u =
          usuarios
              .findByIdAndOficinaId(input.responsavelId(), TenantContext.current())
              .orElseThrow(ApiException::missing);
      if (!u.ativo || (u.papel != Papel.OWNER && u.papel != Papel.ATENDENTE))
        throw ApiException.invalid("Responsável deve ser OWNER ou ATENDENTE ativo.");
    }
    if (!Objects.equals(o.responsavelId, input.responsavelId())) {
      evento(o, "RESPONSAVEL_ALTERADO", o.responsavelId, input.responsavelId(), null);
      o.responsavelId = input.responsavelId();
    }
    return resposta(o);
  }

  public OportunidadeSaida proximoContato(UUID id, ProximoContatoEntrada input) {
    var o = bloquear(id, input.revisao());
    validarProximo(input.proximoContatoEm());
    proximo(o, input.proximoContatoEm());
    if (input.proximoContatoEm() == null && o.status == StatusOportunidade.AGENDADA)
      transicao(o, StatusOportunidade.EM_CONTATO, null);
    return resposta(o);
  }

  /** Uma transação por OS; mesmo lock usado por publicação, versão e decisão. */
  public IdentificacaoSaida reconciliar(UUID os, Instant agora) {
    ordens.lock(os, TenantContext.current()).orElseThrow(ApiException::missing);
    var fontes = consultas.origens(os, agora);
    var existentes = oportunidades.findByOrdemServicoIdAndOficinaId(os, TenantContext.current());
    long criadas = 0, descartadas = 0;
    for (var anterior : existentes) {
      var o =
          oportunidades
              .lock(anterior.id, TenantContext.current())
              .orElseThrow(ApiException::missing);
      // Recarrega: a consulta anterior pode anteceder contato/resultado concorrente.
      em.refresh(o);
      if (!o.status.encerrada() && fontes.stream().noneMatch(f -> mesmaOrigem(o, f))) {
        transicao(
            o, StatusOportunidade.DESCARTADA, "Origem deixou de ser elegível na identificação.");
        descartadas++;
      }
    }
    em.flush();
    for (var f : fontes)
      if (existentes.stream().noneMatch(o -> mesmaOrigem(o, f))) {
        var o = new OportunidadeRecuperacao();
        o.criadoEm = agora;
        o.tipo = f.tipo();
        o.ordemServicoId = f.os();
        o.orcamentoVersaoId = f.versao();
        o.clienteId = f.cliente();
        o.veiculoId = f.veiculo();
        o.valorPotencial = f.valor();
        o.elegivelDesde = f.elegivel();
        oportunidades.save(o);
        evento(o, "OPORTUNIDADE_CRIADA", null, StatusOportunidade.ABERTA, null);
        criadas++;
      }
    return new IdentificacaoSaida(criadas, descartadas);
  }

  private boolean mesmaOrigem(OportunidadeRecuperacao o, ConsultaRecuperacao.Origem f) {
    return o.tipo == f.tipo() && Objects.equals(o.orcamentoVersaoId, f.versao());
  }

  @Transactional(readOnly = true)
  public ProgramacaoSaida revisao(UUID id) {
    var os =
        ordens.findByIdAndOficinaId(id, TenantContext.current()).orElseThrow(ApiException::missing);
    return new ProgramacaoSaida(os.proximaRevisaoEm, os.revisao);
  }

  public ProgramacaoSaida revisao(UUID id, ProgramacaoEntrada input) {
    var os = ordens.lock(id, TenantContext.current()).orElseThrow(ApiException::missing);
    if (os.revisao != input.revisao())
      throw ApiException.conflict("A OS mudou. Atualize a página.");
    if (os.status != StatusOs.PRONTO)
      throw ApiException.conflict("Programe a próxima revisão após concluir a OS.");
    if (input.data() != null
        && input
            .data()
            .isBefore(os.concluidaEm.atZone(ZoneId.of("America/Sao_Paulo")).toLocalDate()))
      throw ApiException.invalid("Revisão não pode anteceder a conclusão da OS.");
    if (Objects.equals(os.proximaRevisaoEm, input.data()))
      return new ProgramacaoSaida(os.proximaRevisaoEm, os.revisao);
    if (oportunidades.findByOrdemServicoIdAndOficinaId(id, TenantContext.current()).stream()
        .anyMatch(o -> o.tipo == TipoOportunidade.REVISAO_ATRASADA))
      throw ApiException.conflict(
          "Esta programação já originou uma oportunidade. Registre o novo ciclo em outra OS.");
    osService.evento(
        id,
        "PROXIMA_REVISAO_ALTERADA",
        os.proximaRevisaoEm + " → " + input.data(),
        OsService.autor());
    os.proximaRevisaoEm = input.data();
    em.flush();
    return new ProgramacaoSaida(os.proximaRevisaoEm, os.revisao);
  }

  private UUID osDaVersao(UUID id) {
    var v =
        versoes
            .findByIdAndOficinaId(id, TenantContext.current())
            .orElseThrow(ApiException::missing);
    return orcamentos
        .findByIdAndOficinaId(v.orcamentoId, TenantContext.current())
        .orElseThrow(ApiException::missing)
        .ordemServicoId;
  }

  @Transactional(readOnly = true)
  public ProgramacaoSaida reavaliacao(UUID id) {
    osDaVersao(id);
    return acompanhamentos
        .findByOrcamentoVersaoIdAndOficinaId(id, TenantContext.current())
        .map(a -> new ProgramacaoSaida(a.reavaliarEm, a.revisao))
        .orElse(new ProgramacaoSaida(null, 0));
  }

  public ProgramacaoSaida reavaliacao(UUID id, ProgramacaoEntrada input) {
    UUID os = osDaVersao(id);
    ordens.lock(os, TenantContext.current()).orElseThrow(ApiException::missing);
    var d =
        decisoes
            .findByOrcamentoVersaoIdAndOficinaId(id, TenantContext.current())
            .orElseThrow(() -> ApiException.conflict("Orçamento ainda não foi recusado."));
    if (d.aprovado) throw ApiException.conflict("Orçamento aprovado não pode ser reavaliado.");
    if (oportunidades.findByOrdemServicoIdAndOficinaId(os, TenantContext.current()).stream()
        .anyMatch(
            o -> o.tipo == TipoOportunidade.REAVALIACAO_PENDENTE && id.equals(o.orcamentoVersaoId)))
      throw ApiException.conflict(
          "Reavaliação já identificada. Use o próximo contato da oportunidade.");
    var a =
        acompanhamentos
            .findByOrcamentoVersaoIdAndOficinaId(id, TenantContext.current())
            .orElseGet(
                () -> {
                  var novo = new AcompanhamentoOrcamento();
                  novo.orcamentoVersaoId = id;
                  return novo;
                });
    if (a.revisao != input.revisao())
      throw ApiException.conflict("A programação mudou. Atualize a página.");
    if (input.data() != null
        && input.data().isBefore(d.criadoEm.atZone(ZoneId.of("America/Sao_Paulo")).toLocalDate()))
      throw ApiException.invalid("Reavaliação não pode anteceder a recusa.");
    osService.evento(
        os,
        "REAVALIACAO_ALTERADA",
        id + ": " + a.reavaliarEm + " → " + input.data(),
        OsService.autor());
    a.reavaliarEm = input.data();
    a = acompanhamentos.save(a);
    em.flush();
    return new ProgramacaoSaida(a.reavaliarEm, a.revisao);
  }
}
