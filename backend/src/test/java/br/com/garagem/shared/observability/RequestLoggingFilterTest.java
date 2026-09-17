package br.com.garagem.shared.observability;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.*;

class RequestLoggingFilterTest {
  @Test
  void limiarInclusivoEConfiguravel() {
    var filtro = new RequestLoggingFilter(1000);
    assertThat(filtro.lento(999)).isFalse();
    assertThat(filtro.lento(1000)).isTrue();
    assertThat(new RequestLoggingFilter(2000).lento(1500)).isFalse();
    assertThatThrownBy(() -> new RequestLoggingFilter(0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void segmentosLivresEParametrosNaoVazam() {
    assertThat(RequestLoggingFilter.caminhoSeguro("/api/v1/publico/credencial/sufixo-secreto"))
        .isEqualTo("/api/v1/publico/{token}/{rota}");
    assertThat(RequestLoggingFilter.caminhoSeguro("/api/v1/clientes/email-secreto"))
        .isEqualTo("/api/v1/clientes/{id}");
  }

  @Test
  void excecaoForaDoMvcRegistra500ELimpaContexto() throws Exception {
    var request =
        new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1/clientes");
    var response = new org.springframework.mock.web.MockHttpServletResponse();
    assertThatThrownBy(
            () ->
                new RequestLoggingFilter(1000)
                    .doFilter(
                        request,
                        response,
                        (req, res) -> {
                          throw new jakarta.servlet.ServletException("Falha sintética");
                        }))
        .isInstanceOf(jakarta.servlet.ServletException.class);
    assertThat(response.getStatus()).isEqualTo(500);
    assertThat(response.getHeader("X-Request-Id")).isNotBlank();
    assertThat(org.slf4j.MDC.getCopyOfContextMap()).isNullOrEmpty();
  }

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
