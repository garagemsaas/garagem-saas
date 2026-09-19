package br.com.garagem.ordemservico.port;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * O que o módulo de ordem de serviço oferece aos outros módulos.
 *
 * <p>Existe porque {@code dinheiroesquecido} importava quatro repositórios de dentro de {@code
 * ordemservico} — um deles quatro níveis fundo, em {@code acessopublico.aprovacao.repository} — e
 * ainda alterava a entidade {@code OrdemServico} diretamente. Na prática não havia fronteira:
 * trocar um repositório interno de OS quebrava um módulo que nem trata de OS.
 *
 * <p>A superfície é deliberadamente estreita. Só entra aqui o que outro módulo de fato precisa, e
 * sempre em termos de negócio — nunca devolvendo entidade gerenciada para alguém de fora mexer.
 */
public interface OrdemServicoPort {
  record Preparacao(
      UUID ordemServicoId,
      UUID veiculoId,
      UUID versaoId,
      java.math.BigDecimal custo,
      boolean concluida) {}

  UUID iniciarPreparacao(UUID veiculoId, UUID mecanicoId, long km, String relato);

  Preparacao preparacao(UUID ordemServicoId, UUID veiculoId);

  /** Dados de OS que interessam a quem está fora do módulo. */
  record ResumoOs(
      UUID id,
      StatusPublico status,
      Instant concluidaEm,
      LocalDate proximaRevisaoEm,
      long revisao) {}

  /** Só o suficiente para as regras externas; o enum completo continua interno. */
  enum StatusPublico {
    PRONTO,
    EM_ANDAMENTO
  }

  /** Decisão do cliente sobre uma versão de orçamento. */
  record Decisao(boolean aprovado, Instant tomadaEm) {}

  /** Resumo da OS na oficina autenticada. Vazio quando não existe ou é de outra oficina. */
  Optional<ResumoOs> resumo(UUID ordemServicoId);

  /** Trava a OS para operações que precisam serializar com o próprio módulo de OS. */
  ResumoOs bloquear(UUID ordemServicoId);

  /** OS à qual uma versão de orçamento pertence. */
  Optional<UUID> ordemServicoDaVersao(UUID orcamentoVersaoId);

  /** Decisão registrada para a versão, se houver. */
  Optional<Decisao> decisaoDaVersao(UUID orcamentoVersaoId);

  /**
   * Programa a próxima revisão da OS concluída. É a única escrita que outro módulo faz sobre uma
   * OS, e passa por aqui justamente para as regras de estado e a auditoria ficarem no dono do dado.
   */
  void programarProximaRevisao(UUID ordemServicoId, LocalDate data, long revisaoEsperada);

  /** Acrescenta um fato à linha do tempo da OS. */
  void registrarEvento(UUID ordemServicoId, String tipo, String descricao);
}
