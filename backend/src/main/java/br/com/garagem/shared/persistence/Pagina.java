package br.com.garagem.shared.persistence;

import java.util.List;
import org.springframework.data.domain.*;

public record Pagina<T>(List<T> itens, int pagina, int tamanho, long total) {
  public static <T> Pagina<T> de(Page<T> page) {
    return new Pagina<>(
        page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
  }

  public static Pageable request(int pagina, int tamanho) {
    return PageRequest.of(
        Math.max(0, pagina),
        Math.min(100, Math.max(1, tamanho)),
        Sort.by(Sort.Direction.DESC, "criadoEm").and(Sort.by("id")));
  }
}
