package br.com.garagem.ordemservico.port;

import br.com.garagem.ordemservico.acessopublico.aprovacao.repository.AprovacaoOrcamentoRepository;
import br.com.garagem.ordemservico.application.OsService;
import br.com.garagem.ordemservico.domain.OrdemServico;
import br.com.garagem.ordemservico.domain.StatusOs;
import br.com.garagem.ordemservico.orcamento.repository.OrcamentoRepository;
import br.com.garagem.ordemservico.orcamento.versao.repository.OrcamentoVersaoRepository;
import br.com.garagem.ordemservico.repository.OrdemServicoRepository;
import br.com.garagem.shared.error.ApiException;
import br.com.garagem.shared.seguranca.UsuarioAutenticado;
import br.com.garagem.tenancy.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementação de {@link OrdemServicoPort}: os repositórios internos ficam deste lado da porta.
 */
@Service
@Transactional
public class OrdemServicoAdapter implements OrdemServicoPort {
  @PersistenceContext private EntityManager em;
  private final OrdemServicoRepository ordens;
  private final OrcamentoVersaoRepository versoes;
  private final OrcamentoRepository orcamentos;
  private final AprovacaoOrcamentoRepository decisoes;
  private final OsService osService;

  public OrdemServicoAdapter(
      OrdemServicoRepository ordens,
      OrcamentoVersaoRepository versoes,
      OrcamentoRepository orcamentos,
      AprovacaoOrcamentoRepository decisoes,
      OsService osService) {
    this.ordens = ordens;
    this.versoes = versoes;
    this.orcamentos = orcamentos;
    this.decisoes = decisoes;
    this.osService = osService;
  }

  private static ResumoOs resumoDe(OrdemServico o) {
    return new ResumoOs(
        o.id,
        o.status == StatusOs.PRONTO ? StatusPublico.PRONTO : StatusPublico.EM_ANDAMENTO,
        o.concluidaEm,
        o.proximaRevisaoEm,
        o.revisao);
  }

  @Override
  public UUID iniciarPreparacao(UUID veiculoId, UUID mecanicoId, long km, String relato) {
    UUID id =
        osService
            .criarInterna(
                new br.com.garagem.ordemservico.api.OsDtos.NovaOs(
                    veiculoId, mecanicoId, km, relato, null))
            .id();
    // Quem chama esta porta grava o identificador numa chave estrangeira em seguida, e faz isso por
    // JDBC. A OS existe no contexto de persistência, mas o insert só sairia no fim da transação —
    // a restrição então olhava para uma tabela onde a linha ainda não estava e derrubava a
    // preparação inteira. Devolver um id é prometer que ele já é encontrável no banco.
    em.flush();
    return id;
  }

  @Override
  public Preparacao preparacao(UUID id, UUID veiculoId) {
    var o = ordens.lock(id, TenantContext.current()).orElseThrow(ApiException::missing);
    if (!"INTERNA".equals(o.tipo) || !o.veiculoId.equals(veiculoId)) throw ApiException.missing();
    var versoesDaOs = osService.versoes(id);
    var ultima =
        versoesDaOs.stream()
            .max(
                java.util.Comparator.comparingInt(
                    br.com.garagem.ordemservico.api.OsDtos.VersaoSaida::numero))
            .orElse(null);
    return new Preparacao(
        id,
        veiculoId,
        ultima == null ? null : ultima.id(),
        ultima == null ? java.math.BigDecimal.ZERO : ultima.total(),
        o.status == StatusOs.PRONTO);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ResumoOs> resumo(UUID ordemServicoId) {
    return ordens
        .findByIdAndOficinaId(ordemServicoId, TenantContext.current())
        .map(OrdemServicoAdapter::resumoDe);
  }

  @Override
  public ResumoOs bloquear(UUID ordemServicoId) {
    return resumoDe(
        ordens.lock(ordemServicoId, TenantContext.current()).orElseThrow(ApiException::missing));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UUID> ordemServicoDaVersao(UUID orcamentoVersaoId) {
    return versoes
        .findByIdAndOficinaId(orcamentoVersaoId, TenantContext.current())
        .flatMap(v -> orcamentos.findByIdAndOficinaId(v.orcamentoId, TenantContext.current()))
        .map(o -> o.ordemServicoId);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Decisao> decisaoDaVersao(UUID orcamentoVersaoId) {
    return decisoes
        .findByOrcamentoVersaoIdAndOficinaId(orcamentoVersaoId, TenantContext.current())
        .map(d -> new Decisao(d.aprovado, d.criadoEm));
  }

  /**
   * Exceção limitada à programação pós-serviço: altera a data e nada mais da OS, com conferência de
   * revisão e de estado feitas aqui, onde o dado mora.
   */
  @Override
  public void programarProximaRevisao(UUID ordemServicoId, LocalDate data, long revisaoEsperada) {
    var os =
        ordens.lock(ordemServicoId, TenantContext.current()).orElseThrow(ApiException::missing);
    if (os.revisao != revisaoEsperada)
      throw ApiException.conflict("A OS mudou. Atualize a página.");
    if (os.status != StatusOs.PRONTO)
      throw ApiException.conflict("Programe a próxima revisão após concluir a OS.");
    osService.evento(
        ordemServicoId,
        "PROXIMA_REVISAO_ALTERADA",
        os.proximaRevisaoEm + " → " + data,
        UsuarioAutenticado.id());
    os.proximaRevisaoEm = data;
  }

  @Override
  public void registrarEvento(UUID ordemServicoId, String tipo, String descricao) {
    osService.evento(ordemServicoId, tipo, descricao, UsuarioAutenticado.id());
  }
}
