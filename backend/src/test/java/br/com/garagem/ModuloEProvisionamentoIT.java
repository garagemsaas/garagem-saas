package br.com.garagem;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.garagem.oficina.ModuloEmpresa;
import br.com.garagem.tenancy.RequerModulo;
import br.com.garagem.tenancy.SemModulo;
import com.fasterxml.jackson.databind.*;
import java.sql.Connection;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

/**
 * Prova as duas metades da correção: a exigência de módulo vive no endpoint, e a empresa nasce com
 * os módulos que contratou.
 *
 * <p>Os dois controladores abaixo existem só neste teste e são a evidência principal. Eles usam
 * caminhos que nenhuma configuração da aplicação conhece — nenhuma regex, nenhuma lista, nenhum
 * registro de interceptador por padrão de URL foi tocado para que funcionassem. Se a proteção
 * voltasse a depender de caminho, {@code exigenciaAcompanhaEndpointNovoSemListaCentral} falharia
 * justamente por o endpoint ser novo.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ModuloEProvisionamentoIT extends br.com.garagem.suporte.IntegracaoBase {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired PasswordEncoder encoder;
  static final String SENHA = "SenhaSegura123!";

  @RestController
  @RequestMapping("/api/v1/endpoint-inedito-de-oficina")
  @RequerModulo(ModuloEmpresa.OFICINA)
  static class EndpointInedito {
    @GetMapping
    String responder() {
      return "ok";
    }
  }

  @RestController
  @RequestMapping("/api/v1/endpoint-inedito-comum")
  @SemModulo
  static class EndpointComum {
    @GetMapping
    String responder() {
      return "ok";
    }
  }

  @TestConfiguration
  static class Endpoints {
    @Bean
    EndpointInedito endpointInedito() {
      return new EndpointInedito();
    }

    @Bean
    EndpointComum endpointComum() {
      return new EndpointComum();
    }
  }

  @Test
  void exigenciaAcompanhaEndpointNovoSemListaCentral() throws Exception {
    String oficina = token(provisionar("OFICINA"));
    String revenda = token(provisionar("REVENDA"));
    assertThatThrownBy(() -> provisionar("OFICINA", "REVENDA"))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);

    mvc.perform(
            get("/api/v1/endpoint-inedito-de-oficina").header("Authorization", "Bearer " + oficina))
        .andExpect(status().isOk());
    // Empresa de revenda não recebe 403: para ela a funcionalidade não existe.
    mvc.perform(
            get("/api/v1/endpoint-inedito-de-oficina").header("Authorization", "Bearer " + revenda))
        .andExpect(status().isNotFound());
    for (String sessao : List.of(oficina, revenda))
      mvc.perform(get("/api/v1/endpoint-inedito-comum").header("Authorization", "Bearer " + sessao))
          .andExpect(status().isOk());
    // Sem sessão continua sendo 401 da autenticação, não 404 do módulo.
    mvc.perform(get("/api/v1/endpoint-inedito-de-oficina")).andExpect(status().isUnauthorized());
  }

  @Test
  void empresaDeOficinaNasceComOficinaEOperaNormalmente() throws Exception {
    UUID id = provisionar("OFICINA");
    assertThat(modulos(id)).containsExactly("OFICINA");
    mvc.perform(get("/api/v1/ordens-servico").header("Authorization", "Bearer " + token(id)))
        .andExpect(status().isOk());
  }

  @Test
  void empresaDeRevendaNasceSemPassarPorOficina() throws Exception {
    UUID id = provisionar("REVENDA");
    assertThat(modulos(id)).containsExactly("REVENDA");
    // A trilha administrativa registra a criação já com o módulo contratado: não há evento anterior
    // dizendo que um dia ela foi oficina, porque ela nunca foi.
    assertThat(
            jdbc.queryForObject(
                "select depois->>'modulos' from empresa_administracao_evento where oficina_id=?",
                String.class,
                id))
        .isEqualTo("[\"REVENDA\"]");
    String sessao = token(id);
    for (String caminho :
        List.of(
            "/ordens-servico",
            "/dashboard",
            "/dinheiro-esquecido/resumo",
            "/orcamento-versoes/" + UUID.randomUUID() + "/reavaliacao"))
      mvc.perform(get("/api/v1" + caminho).header("Authorization", "Bearer " + sessao))
          .andExpect(status().isNotFound());
    // Identidade, usuários, clientes e veículos são vocabulário comum e continuam disponíveis.
    for (String caminho : List.of("/empresa", "/clientes", "/veiculos", "/usuarios"))
      mvc.perform(get("/api/v1" + caminho).header("Authorization", "Bearer " + sessao))
          .andExpect(status().isOk());
  }

  @Test
  void provisionamentoRejeitaModuloInvalidoEEhIdempotentePorSlug() {
    assertThatThrownBy(() -> provisionar("ESTOQUE"))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(() -> provisionar())
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    UUID id = provisionar("REVENDA");
    String slug = jdbc.queryForObject("select slug from oficina where id=?", String.class, id);
    assertThat(provisionarComSlug(slug, "OFICINA")).isNull();
    assertThat(modulos(id)).containsExactly("REVENDA");
  }

  @Test
  void criacaoSemModuloExplicitoContinuaNascendoOficina() {
    // Caminho legado: quem insere direto na tabela, como fazem as migrações e as suítes antigas,
    // não fica com empresa sem módulo algum. O padrão seguro sobrevive, apenas deixou de atropelar
    // quem declara o que quer.
    UUID id = UUID.randomUUID();
    jdbc.update("insert into oficina(id,nome,slug) values(?,?,?)", id, "Legada", "legada-" + id);
    assertThat(modulos(id)).containsExactly("OFICINA");
  }

  @Test
  void ownerNaoAlteraOsProprioModulos() throws Exception {
    UUID id = provisionar("REVENDA");
    String sessao = token(id);
    mvc.perform(
            put("/api/v1/plataforma/empresas/" + id)
                .header("Authorization", "Bearer " + sessao)
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isForbidden());
    assertThat(modulos(id)).containsExactly("REVENDA");
    // E não existe endpoint algum para tentar: módulo é decisão administrativa, feita no banco.
    mvc.perform(
            put("/api/v1/empresa/modulos")
                .header("Authorization", "Bearer " + sessao)
                .contentType("application/json")
                .content("{\"modulos\":[\"OFICINA\"]}"))
        .andExpect(status().isNotFound());
  }

  UUID provisionar(String... modulos) {
    return provisionarComSlug("e-" + UUID.randomUUID(), modulos);
  }

  UUID provisionarComSlug(String slug, String... modulos) {
    UUID id =
        jdbc.execute(
            (Connection connection) -> {
              try (var statement =
                  connection.prepareStatement("select provisionar_empresa(?, ?, ?, ?, ?)")) {
                statement.setString(1, slug);
                statement.setString(2, "Empresa " + slug);
                statement.setArray(3, connection.createArrayOf("text", modulos));
                statement.setString(4, "operador");
                statement.setString(5, "Venda direta");
                try (var resultado = statement.executeQuery()) {
                  return resultado.next() ? resultado.getObject(1, UUID.class) : null;
                }
              }
            });
    if (id != null)
      jdbc.update(
          "insert into usuario(id,oficina_id,nome,email,senha_hash,papel) values(?,?,?,?,?,'OWNER')",
          UUID.randomUUID(),
          id,
          "Owner",
          "owner@test.local",
          encoder.encode(SENHA));
    return id;
  }

  List<String> modulos(UUID id) {
    return jdbc.queryForList(
        "select modulo from empresa_modulo where oficina_id=? order by modulo", String.class, id);
  }

  String token(UUID id) throws Exception {
    String slug = jdbc.queryForObject("select slug from oficina where id=?", String.class, id);
    var resposta =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType("application/json")
                    .content(
                        json.writeValueAsBytes(
                            Map.of("oficina", slug, "email", "owner@test.local", "senha", SENHA))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    return json.readTree(resposta).path("accessToken").asText();
  }
}
