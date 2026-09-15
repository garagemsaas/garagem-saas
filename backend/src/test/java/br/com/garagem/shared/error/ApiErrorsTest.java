package br.com.garagem.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ApiErrorsTest {
  @Test
  void errosInternosEDoBancoNaoExpoemDetalhesSensiveis() {
    var handler = new ApiErrors();
    var unexpected = handler.unexpected(new IllegalStateException("senha=NAO_EXIBIR; SQL interno"));
    assertThat(unexpected.getStatusCode().value()).isEqualTo(500);
    assertThat(unexpected.getBody().getDetail()).isEqualTo("Não foi possível concluir a operação.");
    assertThat(unexpected.getBody().toString()).doesNotContain("NAO_EXIBIR", "SQL interno");
    var integrity =
        handler.integrity(new DataIntegrityViolationException("SQL interno; token=NAO_EXIBIR"));
    assertThat(integrity.getStatusCode().value()).isEqualTo(409);
    assertThat(integrity.getBody().toString()).doesNotContain("NAO_EXIBIR", "SQL interno");
  }
}
