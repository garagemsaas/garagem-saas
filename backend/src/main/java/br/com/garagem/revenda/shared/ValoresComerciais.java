package br.com.garagem.revenda.shared;

import br.com.garagem.shared.error.ApiException;
import java.math.*;
import java.util.UUID;

public final class ValoresComerciais {
  private ValoresComerciais() {}

  public static BigDecimal dinheiro(BigDecimal valor) {
    return valor.setScale(2, RoundingMode.HALF_UP);
  }

  public static void validar(
      BigDecimal anunciado,
      BigDecimal minimo,
      BigDecimal negociado,
      BigDecimal entrada,
      UUID troca,
      BigDecimal valorTroca) {
    if (negociado.compareTo(anunciado) > 0 || negociado.compareTo(minimo) < 0)
      throw ApiException.invalid("O valor negociado deve respeitar os preços mínimo e anunciado.");
    if (entrada.add(valorTroca).compareTo(negociado) > 0
        || troca == null && valorTroca.signum() != 0)
      throw ApiException.invalid("Confira entrada e valor do veículo em troca.");
  }
}
