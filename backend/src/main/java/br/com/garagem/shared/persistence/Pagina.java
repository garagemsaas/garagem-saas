package br.com.garagem.shared.persistence;

import br.com.garagem.shared.error.ApiException;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.*;
import org.springframework.data.domain.*;

/**
 * Envelope único de listagem. Página inicial 0, tamanho padrão 20 e teto de {@value
 * #TAMANHO_MAXIMO} itens: não existe listagem ilimitada nesta API.
 */
@Schema(description = "Página de resultados")
public record Pagina<T>(
    @Schema(description = "Itens da página atual") List<T> itens,
    @Schema(description = "Índice da página, começando em 0", example = "0") int pagina,
    @Schema(description = "Itens por página efetivamente aplicados", example = "20") int tamanho,
    @Schema(description = "Total de registros que atendem ao filtro", example = "137") long total,
    @Schema(description = "Total de páginas para o tamanho aplicado", example = "7")
        int totalPaginas) {

  public static final int TAMANHO_PADRAO = 20;
  public static final int TAMANHO_MAXIMO = 100;

  public static <T> Pagina<T> de(Page<T> page) {
    return new Pagina<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }

  /** Paginação com a ordenação padrão do projeto: mais recentes primeiro. */
  public static Pageable request(int pagina, int tamanho) {
    return PageRequest.of(indice(pagina), limite(tamanho), padrao());
  }

  /**
   * Paginação com ordenação escolhida pelo cliente. {@code ordenacao} tem a forma {@code
   * campo,asc|desc}; o campo precisa constar em {@code camposPermitidos}, caso contrário a
   * requisição é recusada com 400. Nenhum texto do cliente chega ao JPA sem passar por esta
   * allowlist.
   */
  public static Pageable request(
      int pagina, int tamanho, String ordenacao, Set<String> camposPermitidos) {
    return PageRequest.of(indice(pagina), limite(tamanho), ordem(ordenacao, camposPermitidos));
  }

  private static int indice(int pagina) {
    return Math.max(0, pagina);
  }

  private static int limite(int tamanho) {
    return Math.min(TAMANHO_MAXIMO, Math.max(1, tamanho));
  }

  private static Sort padrao() {
    return Sort.by(Sort.Direction.DESC, "criadoEm").and(Sort.by("id"));
  }

  private static Sort ordem(String ordenacao, Set<String> camposPermitidos) {
    if (ordenacao == null || ordenacao.isBlank()) return padrao();
    String[] partes = ordenacao.split(",", 2);
    String campo = partes[0].trim();
    if (!camposPermitidos.contains(campo))
      throw ApiException.invalid(
          "Ordenação inválida. Campos aceitos: "
              + String.join(", ", new TreeSet<>(camposPermitidos))
              + ".");
    String direcao = partes.length > 1 ? partes[1].trim().toLowerCase(Locale.ROOT) : "asc";
    if (!direcao.equals("asc") && !direcao.equals("desc"))
      throw ApiException.invalid("Direção de ordenação inválida. Use asc ou desc.");
    return Sort.by(direcao.equals("desc") ? Sort.Direction.DESC : Sort.Direction.ASC, campo)
        .and(Sort.by("id"));
  }
}
