package br.com.garagem.ordemservico.repository;

import br.com.garagem.ordemservico.domain.OrdemServico;
import br.com.garagem.ordemservico.domain.StatusOs;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.support.PageableExecutionUtils;

/**
 * Monta a listagem da OS somando apenas os predicados dos filtros que vieram preenchidos.
 *
 * <p>O motivo de não usar o formato estático {@code (:param is null or coluna = :param)} é
 * concreto: com um parâmetro temporal nulo o PostgreSQL recusa a instrução com {@code 42P18},
 * "could not determine data type of parameter". Em {@code $n is null} não existe contexto do qual
 * inferir o tipo, e um {@code Instant} nulo chega ao driver sem OID, então a consulta falhava já no
 * parse — inclusive quando o cliente não tinha informado período nenhum. Montar a consulta com os
 * filtros presentes resolve a causa e ainda entrega ao banco um plano sem predicados inúteis.
 *
 * <p>Nada que venha do cliente é concatenado no JPQL: os filtros entram como parâmetros nomeados e
 * a ordenação passa por uma allowlist antes de virar {@code order by}.
 */
class OrdemServicoRepositoryImpl implements OrdemServicoRepositoryCustom {

  /** Campos que podem virar {@code order by}. Espelha a allowlist exposta pela API. */
  private static final Set<String> ORDENACAO =
      Set.of("numero", "status", "criadoEm", "previsaoEntrega", "concluidaEm", "id");

  private static final String BASE =
      """
      from OrdemServico o, Veiculo v, Cliente c
       where o.oficinaId = :oficinaId
         and v.oficinaId = :oficinaId
         and c.oficinaId = :oficinaId
         and o.veiculoId = v.id
         and o.clienteId = c.id\
      """;

  @PersistenceContext private EntityManager em;

  @Override
  public Page<OrdemServico> filtrar(
      UUID oficinaId,
      String busca,
      Long numero,
      StatusOs status,
      UUID clienteId,
      UUID veiculoId,
      UUID mecanicoId,
      String placa,
      Instant de,
      Instant ate,
      Pageable pageable) {

    var predicados = new StringBuilder();
    var parametros = new LinkedHashMap<String, Object>();
    parametros.put("oficinaId", oficinaId);

    if (busca != null) {
      predicados.append(
          """
           and (lower(v.placa) like :busca escape '!'
                or lower(c.nome) like :busca escape '!'
                or cast(o.numero as string) like :busca escape '!')\
          """);
      parametros.put("busca", busca);
    }
    adicionar(predicados, parametros, "numero", "o.numero = :numero", numero);
    adicionar(predicados, parametros, "status", "o.status = :status", status);
    adicionar(predicados, parametros, "clienteId", "o.clienteId = :clienteId", clienteId);
    adicionar(predicados, parametros, "veiculoId", "o.veiculoId = :veiculoId", veiculoId);
    adicionar(predicados, parametros, "mecanicoId", "o.mecanicoId = :mecanicoId", mecanicoId);
    adicionar(predicados, parametros, "placa", "lower(v.placa) like :placa escape '!'", placa);
    adicionar(predicados, parametros, "de", "o.criadoEm >= :de", de);
    adicionar(predicados, parametros, "ate", "o.criadoEm <= :ate", ate);

    String corpo = BASE + predicados;

    TypedQuery<OrdemServico> consulta =
        em.createQuery("select o " + corpo + ordenacao(pageable.getSort()), OrdemServico.class);
    parametros.forEach(consulta::setParameter);
    consulta.setFirstResult((int) pageable.getOffset());
    consulta.setMaxResults(pageable.getPageSize());

    List<OrdemServico> conteudo = consulta.getResultList();
    return PageableExecutionUtils.getPage(
        conteudo,
        pageable,
        () -> {
          Query total = em.createQuery("select count(o) " + corpo);
          parametros.forEach(total::setParameter);
          return (Long) total.getSingleResult();
        });
  }

  /** Acrescenta o predicado e o parâmetro apenas quando o filtro foi informado. */
  private static void adicionar(
      StringBuilder predicados,
      Map<String, Object> parametros,
      String nome,
      String jpql,
      Object valor) {
    if (valor == null) return;
    predicados.append("\n   and ").append(jpql);
    parametros.put(nome, valor);
  }

  /**
   * Traduz a ordenação já validada pela camada de API. A allowlist é reaplicada aqui para que
   * nenhum caminho novo consiga transformar texto do cliente em cláusula {@code order by}.
   */
  private static String ordenacao(Sort sort) {
    if (sort.isUnsorted()) return " order by o.criadoEm desc, o.id asc";
    var termos = new ArrayList<String>();
    for (Sort.Order order : sort) {
      if (!ORDENACAO.contains(order.getProperty()))
        throw new IllegalArgumentException("Campo de ordenação não permitido");
      termos.add("o." + order.getProperty() + (order.isAscending() ? " asc" : " desc"));
    }
    return " order by " + String.join(", ", termos);
  }
}
