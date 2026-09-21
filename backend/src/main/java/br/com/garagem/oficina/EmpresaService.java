package br.com.garagem.oficina;

import br.com.garagem.shared.error.ApiException;
import br.com.garagem.tenancy.TenantContext;
import jakarta.validation.constraints.*;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class EmpresaService {
  public record Branding(
      String nomeEmpresarial,
      String nomeExibicao,
      String telefone,
      String email,
      String contato,
      String corPrimaria,
      String corSecundaria,
      UUID logoId,
      UUID faviconId,
      long revisao) {}

  public record Empresa(
      Branding branding,
      List<ModuloEmpresa> modulos,
      String status,
      Site site,
      UUID capaId,
      // O dono precisa saber o endereço do próprio site para divulgá-lo.
      String slug) {}

  /**
   * O que a empresa mostra ao público. Separado da identidade porque são decisões diferentes: cores
   * e logo valem dentro do sistema mesmo sem site nenhum, e publicar é uma escolha à parte.
   */
  public record Site(
      @Size(max = 160) String frase,
      @Size(max = 2000) String sobre,
      @Size(max = 1000) String servicos,
      @Size(max = 300) String endereco,
      @Size(max = 300) String horario,
      @Pattern(regexp = "|[0-9]{10,15}") String whatsapp,
      @Pattern(regexp = "|[A-Za-z0-9._]{1,30}") String instagram,
      boolean publicado,
      @NotNull @PositiveOrZero Long revisao) {}

  public record Edicao(
      @NotBlank @Size(max = 160) String nomeExibicao,
      @Size(max = 40) String telefone,
      @Email @Size(max = 254) String email,
      @Size(max = 500) String contato,
      @NotNull @Pattern(regexp = "#[0-9a-fA-F]{6}") String corPrimaria,
      @NotNull @Pattern(regexp = "#[0-9a-fA-F]{6}") String corSecundaria,
      @NotNull @PositiveOrZero Long revisao) {}

  private final JdbcTemplate jdbc;

  public EmpresaService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  public Branding branding() {
    var rows =
        jdbc.query(
            """
      select o.*,l.id logo_id,f.id favicon_id from oficina o
      left join empresa_imagem l on l.oficina_id=o.id and l.tipo='logo'
      left join empresa_imagem f on f.oficina_id=o.id and f.tipo='favicon'
      where o.id=?
      """,
            (r, n) ->
                new Branding(
                    r.getString("nome"),
                    Optional.ofNullable(r.getString("nome_exibicao")).orElse(r.getString("nome")),
                    r.getString("telefone"),
                    r.getString("email"),
                    r.getString("contato"),
                    r.getString("cor_primaria"),
                    r.getString("cor_secundaria"),
                    r.getObject("logo_id", UUID.class),
                    r.getObject("favicon_id", UUID.class),
                    r.getLong("revisao_branding")),
            TenantContext.current());
    if (rows.isEmpty()) throw ApiException.missing();
    return rows.getFirst();
  }

  @Transactional(readOnly = true)
  public Empresa obter() {
    return new Empresa(
        branding(),
        jdbc.query(
            "select modulo from empresa_modulo where oficina_id=? order by modulo",
            (r, n) -> ModuloEmpresa.valueOf(r.getString(1)),
            TenantContext.current()),
        jdbc.queryForObject(
            "select situacao from oficina where id=?", String.class, TenantContext.current()),
        site(),
        jdbc
            .query(
                "select id from empresa_imagem where oficina_id=? and tipo='capa'",
                (r, n) -> r.getObject(1, UUID.class),
                TenantContext.current())
            .stream()
            .findFirst()
            .orElse(null),
        jdbc.queryForObject(
            "select slug from oficina where id=?", String.class, TenantContext.current()));
  }

  private Site site() {
    return jdbc.queryForObject(
        """
      select site_frase,site_sobre,site_servicos,site_endereco,site_horario,site_whatsapp,
             site_instagram,site_publicado,revisao_branding from oficina where id=?
      """,
        (r, n) ->
            new Site(
                r.getString("site_frase"),
                r.getString("site_sobre"),
                r.getString("site_servicos"),
                r.getString("site_endereco"),
                r.getString("site_horario"),
                r.getString("site_whatsapp"),
                r.getString("site_instagram"),
                r.getBoolean("site_publicado"),
                r.getLong("revisao_branding")),
        TenantContext.current());
  }

  /**
   * Publicar exige ter o que mostrar, e o banco recusa o contrário. A mensagem aqui existe para que
   * o dono leia o que falta em vez de um erro de restrição — a regra continua sendo do banco.
   */
  public Empresa editarSite(Site s) {
    if (jdbc.update(
            """
      update oficina set site_frase=?,site_sobre=?,site_servicos=?,site_endereco=?,site_horario=?,
      site_whatsapp=?,site_instagram=?,site_publicado=?,revisao_branding=revisao_branding+1
      where id=? and revisao_branding=?
      """,
            vazioComoNulo(s.frase()),
            vazioComoNulo(s.sobre()),
            vazioComoNulo(s.servicos()),
            vazioComoNulo(s.endereco()),
            vazioComoNulo(s.horario()),
            vazioComoNulo(s.whatsapp()),
            vazioComoNulo(s.instagram()),
            s.publicado(),
            TenantContext.current(),
            s.revisao())
        != 1) throw ApiException.conflict("A identidade mudou. Recarregue antes de salvar.");
    return obter();
  }

  /**
   * Campo em branco vindo de formulário é ausência, não string vazia — as restrições contam com
   * isso.
   */
  private static String vazioComoNulo(String valor) {
    return valor == null || valor.isBlank() ? null : valor.trim();
  }

  public Empresa editar(Edicao e) {
    if (jdbc.update(
            """
      update oficina set nome_exibicao=?,telefone=?,email=?,contato=?,cor_primaria=?,cor_secundaria=?,
      revisao_branding=revisao_branding+1 where id=? and revisao_branding=?
      """,
            e.nomeExibicao().trim(),
            e.telefone(),
            e.email(),
            e.contato(),
            e.corPrimaria(),
            e.corSecundaria(),
            TenantContext.current(),
            e.revisao())
        != 1) throw ApiException.conflict("A identidade mudou. Recarregue antes de salvar.");
    return obter();
  }

  public Empresa imagem(String tipo, MultipartFile arquivo, long revisao) {
    tipo(tipo);
    byte[] bytes = raster(arquivo);
    if (jdbc.update(
            "update oficina set revisao_branding=revisao_branding+1 where id=? and revisao_branding=?",
            TenantContext.current(),
            revisao)
        != 1) throw ApiException.conflict("A identidade mudou. Recarregue antes de salvar.");
    jdbc.update(
        """
      insert into empresa_imagem(oficina_id,tipo,id,conteudo) values(?,?,?,?)
      on conflict(oficina_id,tipo) do update set id=excluded.id,conteudo=excluded.conteudo
      """,
        TenantContext.current(),
        tipo,
        UUID.randomUUID(),
        bytes);
    return obter();
  }

  @Transactional(readOnly = true)
  public byte[] imagem(String tipo, UUID id) {
    tipo(tipo);
    var rows =
        jdbc.query(
            "select conteudo from empresa_imagem where oficina_id=? and tipo=? and id=?",
            (r, n) -> r.getBytes(1),
            TenantContext.current(),
            tipo,
            id);
    if (rows.isEmpty()) throw ApiException.missing();
    return rows.getFirst();
  }

  private static void tipo(String tipo) {
    // A capa é a imagem de abertura do site e passa pela mesma validação de logo e favicon:
    // 2 MB, PNG ou JPEG de verdade, reescrita para PNG. Não há segundo caminho de upload.
    if (!Set.of("logo", "favicon", "capa").contains(tipo)) throw ApiException.missing();
  }

  private static ApiException invalida() {
    return new ApiException(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Envie uma imagem PNG ou JPEG válida.");
  }

  private static byte[] raster(MultipartFile file) {
    if (file.getSize() > 2 * 1024 * 1024)
      throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "A imagem deve ter até 2 MB.");
    if (file.isEmpty()
        || !Set.of("image/png", "image/jpeg").contains(Objects.toString(file.getContentType(), "")))
      throw invalida();
    String name = Objects.toString(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
    if (!name.matches(".*\\.(png|jpe?g)$")) throw invalida();
    try (var input = ImageIO.createImageInputStream(file.getInputStream())) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw invalida();
      var reader = readers.next();
      try {
        reader.setInput(input);
        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        if (!Set.of("png", "jpeg", "jpg").contains(format)
            || !file.getContentType().equals(format.equals("png") ? "image/png" : "image/jpeg")
            || (long) reader.getWidth(0) * reader.getHeight(0) > 4_000_000) throw invalida();
        var output = new ByteArrayOutputStream();
        if (!ImageIO.write(reader.read(0), "png", output)) throw invalida();
        if (output.size() > 2 * 1024 * 1024)
          throw new ApiException(
              HttpStatus.PAYLOAD_TOO_LARGE, "A imagem decodificada deve ter até 2 MB.");
        return output.toByteArray();
      } finally {
        reader.dispose();
      }
    } catch (IOException e) {
      throw invalida();
    }
  }
}
