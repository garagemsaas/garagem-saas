package br.com.garagem;

import static org.assertj.core.api.Assertions.*;

import br.com.garagem.integration.revenda.RevendaIntegrationBase;
import java.util.*;
import org.junit.jupiter.api.Test;

class MigracaoPolimentoIT extends RevendaIntegrationBase {
  @Test
  void arquivaApresentacaoEPreservaIdentidadeEOperacao() {
    String schema = "polimento_" + UUID.randomUUID().toString().replace("-", "");
    var source = Objects.requireNonNull(jdbc.getDataSource());
    org.flywaydb.core.Flyway.configure()
        .dataSource(source)
        .schemas(schema)
        .defaultSchema(schema)
        .target("9")
        .load()
        .migrate();
    UUID id = UUID.randomUUID(), logo = UUID.randomUUID();
    jdbc.execute(
        (java.sql.Connection c) -> {
          String old = c.getSchema();
          try {
            c.setSchema(schema);
            try (var s =
                c.prepareStatement(
                    "insert into oficina(id,nome,slug,nome_exibicao,cor_primaria,site_frase) values(?,'Original','original','Identidade preservada','#123456','Texto arquivado')")) {
              s.setObject(1, id);
              s.executeUpdate();
            }
            try (var s = c.prepareStatement("insert into empresa_modulo values(?,'REVENDA')")) {
              s.setObject(1, id);
              s.executeUpdate();
            }
            try (var s =
                c.prepareStatement(
                    "insert into empresa_imagem(oficina_id,tipo,id,conteudo) values(?,'logo',?,?)")) {
              s.setObject(1, id);
              s.setObject(2, logo);
              s.setBytes(3, new byte[] {1, 2});
              s.executeUpdate();
            }
          } finally {
            c.setSchema(old);
          }
          return null;
        });
    org.flywaydb.core.Flyway.configure()
        .dataSource(source)
        .schemas(schema)
        .defaultSchema(schema)
        .load()
        .migrate();
    assertThat(
            jdbc.queryForObject(
                "select nome_exibicao from " + schema + ".oficina where id=?", String.class, id))
        .isEqualTo("Identidade preservada");
    assertThat(
            jdbc.queryForObject(
                "select configuracao->>'frase' from "
                    + schema
                    + ".empresa_configuracao_arquivada where oficina_id=?",
                String.class,
                id))
        .isEqualTo("Texto arquivado");
    assertThat(
            jdbc.queryForObject(
                "select modulo from " + schema + ".empresa_modulo where oficina_id=?",
                String.class,
                id))
        .isEqualTo("OFICINA");
    assertThat(
            jdbc.queryForObject(
                "select id from " + schema + ".empresa_imagem where oficina_id=?", UUID.class, id))
        .isEqualTo(logo);
    assertThatThrownBy(
            () -> jdbc.update("insert into " + schema + ".empresa_modulo values(?,'REVENDA')", id))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }
}
