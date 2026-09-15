package br.com.garagem.config;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.*;

class CorsPropertiesTest {

  @Test
  @DisplayName("Sem configuração, nenhuma origem cruzada é liberada")
  void padraoEhFechado() {
    assertThat(new CorsProperties(null).allowedOrigins()).isEmpty();
    assertThat(new CorsProperties(List.of()).allowedOrigins()).isEmpty();
  }

  @Test
  @DisplayName("Origens explícitas são aceitas com esquema, host e porta")
  void aceitaOrigensExplicitas() {
    var props = new CorsProperties(List.of("https://app.garagem.com.br", "http://localhost:5173"));
    assertThat(props.allowedOrigins())
        .containsExactly("https://app.garagem.com.br", "http://localhost:5173");
  }

  @Test
  @DisplayName("Curinga é recusado na subida, não silenciosamente aceito em produção")
  void recusaCuringa() {
    assertThatThrownBy(() -> new CorsProperties(List.of("*")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("*");
    assertThatThrownBy(() -> new CorsProperties(List.of("https://*.garagem.com.br")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Origem malformada é recusada na subida")
  void recusaOrigemMalformada() {
    for (String invalida :
        List.of("app.garagem.com.br", "https://app.garagem.com.br/painel", "ftp://arquivo", ""))
      assertThatThrownBy(() -> new CorsProperties(List.of(invalida)))
          .describedAs(invalida)
          .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("A lista fica imutável depois de construída")
  void listaEhImutavel() {
    var props = new CorsProperties(List.of("https://app.garagem.com.br"));
    assertThatThrownBy(() -> props.allowedOrigins().add("https://outro"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
