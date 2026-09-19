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

  public record Empresa(Branding branding, List<ModuloEmpresa> modulos, String status) {}

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
            "select situacao from oficina where id=?", String.class, TenantContext.current()));
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
    if (!Set.of("logo", "favicon").contains(tipo)) throw ApiException.missing();
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
