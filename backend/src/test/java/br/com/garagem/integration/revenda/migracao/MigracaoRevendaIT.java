package br.com.garagem.integration.revenda.migracao;

import static org.assertj.core.api.Assertions.*;

import br.com.garagem.integration.revenda.RevendaIntegrationBase;
import java.sql.Connection;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A V8 solta {@code not null} de {@code veiculo.cliente_id} e de {@code ordem_servico.cliente_id},
 * cria restrições novas e preenche histórico de propriedade. Nada disso aparece num banco vazio: as
 * restrições só são violadas por linhas que já existem, e foi exatamente assim que uma migration
 * anterior derrubou a aplicação na subida.
 *
 * <p>Por isso o banco é levado até a Fase 9, recebe dados de uma empresa real — cliente, veículo,
 * OS, usuário, identidade e módulos — e só então sobe para a Fase 10. O que se verifica aqui não é
 * que a migration roda, é que o que já existia continua de pé depois dela.
 */
class MigracaoRevendaIT extends RevendaIntegrationBase {
  @Test
  void upgradeDaFase9PreservaEmpresaClienteVeiculoEOrdemDeServico() {
    String schema = "fase10_" + UUID.randomUUID().toString().replace("-", "");
    var source = java.util.Objects.requireNonNull(jdbc.getDataSource());
    org.flywaydb.core.Flyway.configure()
        .dataSource(source)
        .schemas(schema)
        .defaultSchema(schema)
        .target("7")
        .load()
        .migrate();

    UUID empresa = UUID.randomUUID(),
        usuario = UUID.randomUUID(),
        cliente = UUID.randomUUID(),
        veiculo = UUID.randomUUID(),
        os = UUID.randomUUID();
    noSchema(
        schema,
        c -> {
          exec(
              c,
              "insert into oficina(id,nome,slug,nome_exibicao,cor_primaria) values(?,'Oficina existente','existente','Fantasia','#abcdef')",
              empresa);
          exec(
              c,
              "insert into usuario(id,oficina_id,nome,email,senha_hash,papel) values(?,?,'Dono','dono@test.local','x','OWNER')",
              usuario,
              empresa);
          exec(
              c,
              "insert into cliente(id,oficina_id,nome,telefone) values(?,?,'Cliente antigo','11999999999')",
              cliente,
              empresa);
          exec(
              c,
              "insert into veiculo(id,oficina_id,cliente_id,placa,marca,modelo,ano,km,cor) values(?,?,?,'ABC1D23','Fiat','Uno',2019,50000,'Prata')",
              veiculo,
              empresa,
              cliente);
          exec(
              c,
              "insert into ordem_servico(id,oficina_id,cliente_id,veiculo_id,numero,status,km_entrada,relato) values(?,?,?,?,1,'RECEBIDO',50000,'Barulho')",
              os,
              empresa,
              cliente,
              veiculo);
        });

    org.flywaydb.core.Flyway.configure()
        .dataSource(source)
        .schemas(schema)
        .defaultSchema(schema)
        .load()
        .migrate();

    // O veículo do cliente continua sendo do cliente. A revenda não reinterpretou o acervo antigo.
    assertThat(
            jdbc.queryForMap(
                "select cliente_id,propriedade,placa,ano,ano_modelo from "
                    + schema
                    + ".veiculo where id=?",
                veiculo))
        .containsEntry("cliente_id", cliente)
        .containsEntry("propriedade", "CLIENTE")
        .containsEntry("placa", "ABC1D23")
        .containsEntry("ano", 2019)
        .containsEntry("ano_modelo", 2019);
    // O histórico nasce com a propriedade vigente, senão a primeira transferência pareceria a
    // origem do veículo.
    assertThat(
            jdbc.queryForMap(
                "select propriedade_anterior,propriedade_atual,cliente_atual_id from "
                    + schema
                    + ".veiculo_propriedade_historico where veiculo_id=?",
                veiculo))
        .containsEntry("propriedade_anterior", null)
        .containsEntry("propriedade_atual", "CLIENTE")
        .containsEntry("cliente_atual_id", cliente);
    assertThat(
            jdbc.queryForMap(
                "select cliente_id,tipo,status from " + schema + ".ordem_servico where id=?", os))
        .containsEntry("cliente_id", cliente)
        .containsEntry("tipo", "CLIENTE")
        .containsEntry("status", "RECEBIDO");
    assertThat(
            jdbc.queryForMap(
                "select nome_exibicao,cor_primaria from " + schema + ".oficina where id=?",
                empresa))
        .containsEntry("nome_exibicao", "Fantasia")
        .containsEntry("cor_primaria", "#abcdef");
    assertThat(
            jdbc.queryForObject(
                "select modulo from " + schema + ".empresa_modulo where oficina_id=?",
                String.class,
                empresa))
        .isEqualTo("OFICINA");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from " + schema + ".usuario where id=?", Integer.class, usuario))
        .isEqualTo(1);
    // Nenhuma tabela nova nasce com lixo herdado.
    for (String tabela :
        java.util.List.of("revenda_estoque", "revenda_avaliacao", "revenda_lead", "revenda_venda"))
      assertThat(
              jdbc.queryForObject("select count(*) from " + schema + "." + tabela, Integer.class))
          .as(tabela)
          .isZero();
  }

  private void noSchema(String schema, java.util.function.Consumer<Connection> bloco) {
    jdbc.execute(
        (Connection c) -> {
          String anterior = c.getSchema();
          try {
            c.setSchema(schema);
            bloco.accept(c);
          } finally {
            c.setSchema(anterior);
          }
          return null;
        });
  }

  private static void exec(Connection c, String sql, Object... args) {
    try (var s = c.prepareStatement(sql)) {
      for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
      s.executeUpdate();
    } catch (java.sql.SQLException e) {
      throw new IllegalStateException(sql, e);
    }
  }
}
