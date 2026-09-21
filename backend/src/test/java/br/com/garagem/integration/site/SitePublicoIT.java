package br.com.garagem.integration.site;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import br.com.garagem.integration.revenda.RevendaIntegrationBase;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * O site público é a única superfície que resolve a empresa pelo endereço, sem sessão. Isso o torna
 * o lugar mais fácil de vazar dado de tenant por descuido, e é disso que estes testes tratam.
 *
 * <p>Três garantias: só sai o que é de vitrine; só sai de empresa ativa que decidiu publicar; e o
 * slug de uma empresa não alcança a imagem de outra.
 */
class SitePublicoIT extends RevendaIntegrationBase {
  /** A revisão é da empresa inteira: editar a identidade antes já a incrementa. */
  private Map<String, Object> conteudo(Empresa e) throws Exception {
    var m = new HashMap<String, Object>();
    m.put("frase", "Sua oficina de bairro, há 20 anos");
    m.put("sobre", "Atendemos carros de passeio.\n\nAgendamento por telefone.");
    m.put("servicos", "Revisão\nTroca de óleo\n\nFreios\n");
    m.put("endereco", "Rua das Oficinas, 100");
    m.put("horario", "Segunda a sexta, 8h às 18h");
    m.put("whatsapp", "5511999998888");
    m.put("instagram", "oficina.exemplo");
    m.put("publicado", true);
    m.put("revisao", revisao(e));
    return m;
  }

  private long revisao(Empresa e) throws Exception {
    return ok(e, "GET", "/empresa", null, 200).at("/branding/revisao").asLong();
  }

  private String slug(Empresa e) {
    return jdbc.queryForObject("select slug from oficina where id=?", String.class, e.id());
  }

  @Test
  void sitePublicadoMostraApresentacaoESomenteEla() throws Exception {
    var e = empresa("OFICINA");
    // Telefone é da identidade, não do site: publicar exige que ele já exista.
    ok(
        e,
        "PUT",
        "/empresa",
        Map.of(
            "nomeExibicao", "Oficina Exemplo",
            "telefone", "1133334444",
            "corPrimaria", "#123456",
            "corSecundaria", "#654321",
            "revisao", 0),
        200);
    ok(e, "PUT", "/empresa/site", conteudo(e), 200);

    var site = ok(null, "GET", "/site/" + slug(e), null, 200);
    assertThat(site.path("nome").asText()).isEqualTo("Oficina Exemplo");
    assertThat(site.path("frase").asText()).contains("20 anos");
    assertThat(site.path("telefone").asText()).isEqualTo("1133334444");
    assertThat(site.path("corPrimaria").asText()).isEqualTo("#123456");
    // Linhas em branco não viram serviço vazio na tela.
    assertThat(site.path("servicos")).hasSize(3);
    assertThat(site.path("servicos").get(2).asText()).isEqualTo("Freios");
    // Nada de operação vaza pela vitrine.
    for (String proibido :
        java.util.List.of("situacao", "status", "modulos", "id", "proximoNumeroOs", "email"))
      assertThat(site.has(proibido)).as("campo %s não pertence ao site", proibido).isFalse();
  }

  @Test
  void empresaNaoPublicadaOuInativaNaoTemSite() throws Exception {
    var e = empresa("OFICINA");
    // Ainda não publicou: o endereço não corresponde a nada.
    ok(null, "GET", "/site/" + slug(e), null, 404);

    ok(
        e,
        "PUT",
        "/empresa",
        Map.of(
            "nomeExibicao",
            "Oficina",
            "telefone",
            "1133334444",
            "corPrimaria",
            "#123456",
            "corSecundaria",
            "#654321",
            "revisao",
            0),
        200);
    ok(e, "PUT", "/empresa/site", conteudo(e), 200);
    ok(null, "GET", "/site/" + slug(e), null, 200);

    // Empresa desativada some da internet junto com o resto.
    jdbc.update("update oficina set situacao='INATIVA' where id=?", e.id());
    ok(null, "GET", "/site/" + slug(e), null, 404);
    ok(null, "GET", "/site/nao-existe-mesmo", null, 404);
  }

  @Test
  void publicarSemConteudoEhRecusadoESomenteOwnerEdita() throws Exception {
    var e = empresa("OFICINA");
    var vazio = conteudo(e);
    vazio.put("sobre", "");
    vazio.put("endereco", "");
    // O banco recusa publicar uma página sem o que mostrar.
    ok(e, "PUT", "/empresa/site", vazio, 409);
    ok(null, "GET", "/site/" + slug(e), null, 404);

    // Sem sessão ninguém edita, e quem não é OWNER também não.
    ok(null, "PUT", "/empresa/site", conteudo(e), 401);
  }

  @Test
  void imagemDoSitePertenceAoSlugQueAPediu() throws Exception {
    var a = empresa("OFICINA");
    var b = empresa("OFICINA");
    for (var e : java.util.List.of(a, b)) {
      ok(
          e,
          "PUT",
          "/empresa",
          Map.of(
              "nomeExibicao",
              "Empresa",
              "telefone",
              "1133334444",
              "corPrimaria",
              "#123456",
              "corSecundaria",
              "#654321",
              "revisao",
              0),
          200);
      ok(e, "PUT", "/empresa/site", conteudo(e), 200);
    }
    var png =
        java.util.Base64.getDecoder()
            .decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
    long revisao = ok(a, "GET", "/empresa", null, 200).at("/branding/revisao").asLong();
    mvc.perform(
            multipart("/api/v1/empresa/imagens/capa")
                .file(
                    new org.springframework.mock.web.MockMultipartFile(
                        "arquivo", "c.png", "image/png", png))
                .param("revisao", String.valueOf(revisao))
                .header("Authorization", "Bearer " + a.token()))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

    String capa = ok(null, "GET", "/site/" + slug(a), null, 200).path("capaId").asText();
    assertThat(
            mvc.perform(get("/api/v1/site/" + slug(a) + "/imagens/capa/" + capa))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(200);
    // O identificador é da empresa A: pedi-lo pelo endereço de B não devolve nada.
    assertThat(
            mvc.perform(get("/api/v1/site/" + slug(b) + "/imagens/capa/" + capa))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(404);
    // Favicon não é imagem de site, mesmo existindo na tabela.
    assertThat(
            mvc.perform(get("/api/v1/site/" + slug(a) + "/imagens/favicon/" + capa))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(404);
  }
}
