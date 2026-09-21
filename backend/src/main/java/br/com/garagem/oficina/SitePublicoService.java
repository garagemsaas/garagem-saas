package br.com.garagem.oficina;

import br.com.garagem.shared.error.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O site público da empresa, servido sem autenticação.
 *
 * <p>É a única leitura do sistema que não passa por {@code TenantContext}, e isso é deliberado:
 * aqui a empresa não vem de uma sessão, vem do endereço que ela própria divulga. A contrapartida é
 * que cada consulta declara as colunas uma a uma — nunca {@code select *} — porque a linha de
 * {@code oficina} carrega situação, plano e número da próxima OS, e nada disso é da conta de quem
 * passou pela rua.
 *
 * <p>Empresa inativa ou com o site não publicado responde 404, igual a slug inexistente. Quem está
 * de fora não consegue distinguir "não existe" de "existe e está fechada", e não deve mesmo.
 */
@Service
@Transactional(readOnly = true)
public class SitePublicoService {
  /** Só o que uma pessoa procurando a empresa precisa ver. Nada de situação, plano ou módulos. */
  public record Site(
      String nome,
      String frase,
      String sobre,
      List<String> servicos,
      String endereco,
      String horario,
      String telefone,
      String whatsapp,
      String instagram,
      String corPrimaria,
      String corSecundaria,
      UUID logoId,
      UUID capaId,
      long revisao) {}

  private final JdbcTemplate jdbc;

  public SitePublicoService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Site obter(String slug) {
    var rows =
        jdbc.query(
            """
        select o.id, coalesce(o.nome_exibicao,o.nome) nome, o.site_frase, o.site_sobre,
               o.site_servicos, o.site_endereco, o.site_horario, o.telefone, o.site_whatsapp,
               o.site_instagram, o.cor_primaria, o.cor_secundaria, o.revisao_branding,
               l.id logo_id, c.id capa_id
          from oficina o
          left join empresa_imagem l on l.oficina_id=o.id and l.tipo='logo'
          left join empresa_imagem c on c.oficina_id=o.id and c.tipo='capa'
         where o.slug=? and o.situacao='ATIVA' and o.site_publicado=true
        """,
            (r, n) ->
                new Site(
                    r.getString("nome"),
                    r.getString("site_frase"),
                    r.getString("site_sobre"),
                    servicos(r.getString("site_servicos")),
                    r.getString("site_endereco"),
                    r.getString("site_horario"),
                    r.getString("telefone"),
                    r.getString("site_whatsapp"),
                    r.getString("site_instagram"),
                    r.getString("cor_primaria"),
                    r.getString("cor_secundaria"),
                    r.getObject("logo_id", UUID.class),
                    r.getObject("capa_id", UUID.class),
                    r.getLong("revisao_branding")),
            slug);
    if (rows.isEmpty()) throw ApiException.missing();
    return rows.getFirst();
  }

  /**
   * A imagem é buscada pelo par slug + identificador. O identificador sozinho não basta: sem o
   * slug, conhecer um UUID de outra empresa bastaria para puxar a imagem dela mesmo com o site
   * fechado.
   */
  public byte[] imagem(String slug, String tipo, UUID id) {
    if (!List.of("logo", "capa").contains(tipo)) throw ApiException.missing();
    var rows =
        jdbc.query(
            """
        select i.conteudo from empresa_imagem i join oficina o on o.id=i.oficina_id
         where o.slug=? and o.situacao='ATIVA' and o.site_publicado=true and i.tipo=? and i.id=?
        """,
            (r, n) -> r.getBytes(1),
            slug,
            tipo,
            id);
    if (rows.isEmpty()) throw ApiException.missing();
    return rows.getFirst();
  }

  /**
   * Uma linha por serviço, sem linhas vazias e com um teto — a lista é um cardápio, não um texto.
   */
  private static List<String> servicos(String bruto) {
    if (bruto == null) return List.of();
    return bruto.lines().map(String::trim).filter(s -> !s.isEmpty()).limit(12).toList();
  }
}
