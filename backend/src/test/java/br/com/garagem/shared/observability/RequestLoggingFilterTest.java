package br.com.garagem.shared.observability;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.*;

class RequestLoggingFilterTest {

  @Test
  @DisplayName("O token do link público nunca chega ao log")
  void tokenPublicoEhMascarado() {
    String token = "wZq3Xy7kL0pRt8vN2mJ4hB6cD1fG5sA9eU7iO3yT0xQ";
    assertThat(RequestLoggingFilter.caminhoSeguro("/api/v1/publico/" + token))
        .isEqualTo("/api/v1/publico/{token}");
    assertThat(RequestLoggingFilter.caminhoSeguro("/api/v1/publico/" + token + "/decisao"))
        .isEqualTo("/api/v1/publico/{token}/decisao");
  }

  @Test
  @DisplayName("Caminhos comuns passam intactos, para o log continuar útil")
  void demaisCaminhosSeguemIntactos() {
    assertThat(RequestLoggingFilter.caminhoSeguro("/api/v1/clientes"))
        .isEqualTo("/api/v1/clientes");
    assertThat(RequestLoggingFilter.caminhoSeguro("/actuator/health"))
        .isEqualTo("/actuator/health");
    assertThat(RequestLoggingFilter.caminhoSeguro(null)).isNull();
  }
}
