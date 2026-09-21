package br.com.garagem.integration.auth;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import br.com.garagem.integration.revenda.RevendaIntegrationBase;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A sessão precisa sobreviver a um F5 sem entregar o refresh ao JavaScript da página.
 *
 * <p>O access token continua só em memória e morre com a aba. Quem atravessa o recarregamento é o
 * cookie {@code HttpOnly}, e é por isso que ele existe: {@code localStorage} resolveria o
 * recarregamento entregando o token a qualquer XSS, e manter tudo em memória obrigava a pessoa a
 * entrar de novo a cada atualização de página.
 *
 * <p>O que estes testes protegem não é o cookie em si — é que ele não tenha custado nenhuma das
 * defesas da Fase 8: a rotação continua de uso único e o reuso continua derrubando a família.
 */
class SessaoPersistenteIT extends RevendaIntegrationBase {
  private static final String NOME = "garagem_refresh";

  @Test
  void recarregarRetomaSessaoPeloCookieSemCorpoNaRequisicao() throws Exception {
    var e = empresa("OFICINA");
    var login =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType("application/json")
                    .content(
                        json.writeValueAsBytes(
                            Map.of(
                                "oficina", slug(e),
                                "email", "owner@test.local",
                                "senha", "SenhaSegura123!"))))
            .andReturn()
            .getResponse();
    assertThat(login.getStatus()).isEqualTo(200);

    var cookie = login.getCookie(NOME);
    assertThat(cookie).as("cookie de refresh emitido no login").isNotNull();
    assertThat(cookie.isHttpOnly()).as("ilegível por JavaScript").isTrue();
    assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
    assertThat(login.getHeader("Set-Cookie")).contains("SameSite=None").contains("Secure");

    // A aba foi recarregada: não há corpo, não há Authorization, só o cookie.
    var retomada =
        mvc.perform(post("/api/v1/auth/refresh").cookie(cookie)).andReturn().getResponse();
    assertThat(retomada.getStatus()).as(retomada.getContentAsString()).isEqualTo(200);
    var sessao = json.readTree(retomada.getContentAsByteArray());
    assertThat(sessao.path("accessToken").asText()).isNotBlank();
    assertThat(sessao.path("papel").asText()).isEqualTo("OWNER");
    // E o access token restaurado abre a API de verdade.
    assertThat(
            mvc.perform(
                    get("/api/v1/empresa")
                        .header("Authorization", "Bearer " + sessao.path("accessToken").asText()))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(200);
  }

  @Test
  void rotacaoEReusoContinuamValendoAtravesDoCookie() throws Exception {
    var e = empresa("OFICINA");
    var primeiro = cookieDeLogin(e);

    var rotacao =
        mvc.perform(post("/api/v1/auth/refresh").cookie(primeiro)).andReturn().getResponse();
    assertThat(rotacao.getStatus()).isEqualTo(200);
    var segundo = rotacao.getCookie(NOME);
    assertThat(segundo).isNotNull();
    assertThat(segundo.getValue())
        .as("uso único: o cookie é substituído")
        .isNotEqualTo(primeiro.getValue());

    // Reapresentar o cookie já rotacionado é a assinatura de token roubado: a família inteira cai,
    // inclusive o cookie que estava legítimo um instante antes.
    assertThat(
            mvc.perform(post("/api/v1/auth/refresh").cookie(primeiro))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(401);
    assertThat(
            mvc.perform(post("/api/v1/auth/refresh").cookie(segundo))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(401);
  }

  @Test
  void logoutEncerraSessaoEApagaOCookie() throws Exception {
    var e = empresa("OFICINA");
    var cookie = cookieDeLogin(e);
    var saida = mvc.perform(post("/api/v1/auth/logout").cookie(cookie)).andReturn().getResponse();
    assertThat(saida.getStatus()).isEqualTo(204);
    assertThat(saida.getCookie(NOME)).isNotNull();
    assertThat(saida.getCookie(NOME).getMaxAge()).as("cookie expirado no navegador").isZero();
    assertThat(saida.getCookie(NOME).getValue()).isEmpty();
    assertThat(
            mvc.perform(post("/api/v1/auth/refresh").cookie(cookie))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(401);
  }

  @Test
  void semCookieESemCorpoNaoHaSessaoARetomar() throws Exception {
    assertThat(mvc.perform(post("/api/v1/auth/refresh")).andReturn().getResponse().getStatus())
        .isEqualTo(401);
    // Cookie de outra empresa remontado à mão não encontra linha nenhuma: o par vale inteiro.
    var e = empresa("OFICINA");
    var cookie = cookieDeLogin(e);
    var forjado = new Cookie(NOME, java.util.UUID.randomUUID() + cookie.getValue().substring(36));
    assertThat(
            mvc.perform(post("/api/v1/auth/refresh").cookie(forjado))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(401);
  }

  private Cookie cookieDeLogin(Empresa e) throws Exception {
    return mvc.perform(
            post("/api/v1/auth/login")
                .contentType("application/json")
                .content(
                    json.writeValueAsBytes(
                        Map.of(
                            "oficina", slug(e),
                            "email", "owner@test.local",
                            "senha", "SenhaSegura123!"))))
        .andReturn()
        .getResponse()
        .getCookie(NOME);
  }

  private String slug(Empresa e) {
    return jdbc.queryForObject("select slug from oficina where id=?", String.class, e.id());
  }
}
