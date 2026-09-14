package br.com.garagem;

import static org.assertj.core.api.Assertions.*;

import br.com.garagem.ordemservico.application.OsService;
import br.com.garagem.ordemservico.domain.StatusOs;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RegrasOsTest {
  @Test
  void prontoNaoPodeSerReaberto() {
    for (var destino : StatusOs.values()) assertThat(StatusOs.PRONTO.permite(destino)).isFalse();
  }

  @Test
  void recebidoNaoPulaDiagnostico() {
    assertThat(StatusOs.RECEBIDO.permite(StatusOs.DIAGNOSTICO)).isTrue();
    assertThat(StatusOs.RECEBIDO.permite(StatusOs.EM_MANUTENCAO)).isFalse();
  }

  @Test
  void esperaDePecaRetornaParaManutencao() {
    assertThat(StatusOs.EM_MANUTENCAO.permite(StatusOs.AGUARDANDO_PECA)).isTrue();
    assertThat(StatusOs.AGUARDANDO_PECA.permite(StatusOs.EM_MANUTENCAO)).isTrue();
    assertThat(StatusOs.AGUARDANDO_PECA.permite(StatusOs.PRONTO)).isFalse();
  }

  @Test
  void valoresSaoArredondadosPorItem() {
    assertThat(OsService.subtotal(new BigDecimal("1.500"), new BigDecimal("100.01")))
        .isEqualByComparingTo("150.02");
  }
}
