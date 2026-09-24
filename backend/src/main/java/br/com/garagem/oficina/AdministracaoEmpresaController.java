package br.com.garagem.oficina;

import br.com.garagem.shared.error.ApiException;
import br.com.garagem.shared.seguranca.UsuarioAutenticado;
import br.com.garagem.tenancy.SemModulo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@SemModulo
@RequestMapping("/api/v1/plataforma/empresas")
@PreAuthorize("hasAnyRole('DESENVOLVEDOR','ADMIN_PLATAFORMA')")
@Transactional
public class AdministracaoEmpresaController {
  private final JdbcTemplate jdbc;
  private final EmpresaService empresas;
  private final PasswordEncoder encoder;

  public AdministracaoEmpresaController(
      JdbcTemplate jdbc, EmpresaService empresas, PasswordEncoder encoder) {
    this.jdbc = jdbc;
    this.empresas = empresas;
    this.encoder = encoder;
  }

  @io.swagger.v3.oas.annotations.media.Schema(name = "AdministracaoEmpresaCriacao")
  public record Criacao(
      @NotBlank @Pattern(regexp = "[a-z0-9-]{3,80}") String slug,
      @NotBlank @Size(max = 160) String nome,
      @NotNull ModuloEmpresa operacao,
      @NotBlank @Size(max = 160) String proprietario,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 12, max = 72) String senha) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "AdministracaoEmpresaEdicao")
  public record Edicao(
      @NotBlank @Size(max = 160) String nome,
      @NotNull ModuloEmpresa operacao,
      @NotBlank @Pattern(regexp = "ATIVA|SUSPENSA|INATIVA") String status,
      @NotNull @PositiveOrZero Long revisao,
      @NotBlank @Size(max = 500) String motivo) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "AdministracaoEmpresaResumo")
  public record Resumo(
      UUID id, String nome, String slug, String status, String operacao, long revisao) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "AdministracaoEmpresaPagina")
  public record Lista(List<Resumo> itens, long total, int pagina, int tamanho) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "AdministracaoEmpresaDetalhe")
  public record Detalhe(Resumo empresa, EmpresaService.Empresa identidade) {}

  private static final String SELECT =
      "select o.id,o.nome,o.slug,o.situacao,m.modulo,o.revisao_administracao from oficina o join empresa_modulo m on m.oficina_id=o.id ";

  private static Resumo resumo(java.sql.ResultSet r, int n) throws java.sql.SQLException {
    return new Resumo(
        r.getObject(1, UUID.class),
        r.getString(2),
        r.getString(3),
        r.getString(4),
        r.getString(5),
        r.getLong(6));
  }

  @GetMapping
  @Transactional(readOnly = true)
  public Lista listar(
      @RequestParam(defaultValue = "") String busca,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "20") int tamanho) {
    if (pagina < 0 || pagina > 100000 || tamanho < 1 || tamanho > 100 || busca.length() > 160)
      throw ApiException.invalid("Filtros inválidos.");
    String filtro = "%" + busca.trim().toLowerCase(Locale.ROOT) + "%";
    var itens =
        jdbc.query(
            SELECT
                + "where lower(o.nome || ' ' || o.slug) like ? order by o.nome,o.id limit ? offset ?",
            AdministracaoEmpresaController::resumo,
            filtro,
            tamanho,
            pagina * tamanho);
    Long total =
        jdbc.queryForObject(
            "select count(*) from oficina where lower(nome || ' ' || slug) like ?",
            Long.class,
            filtro);
    return new Lista(itens, total == null ? 0 : total, pagina, tamanho);
  }

  @GetMapping("/{id}")
  @Transactional(readOnly = true)
  public Detalhe obter(@PathVariable UUID id) {
    var rows = jdbc.query(SELECT + "where o.id=?", AdministracaoEmpresaController::resumo, id);
    if (rows.isEmpty()) throw ApiException.missing();
    return new Detalhe(rows.getFirst(), empresas.obter(id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Detalhe criar(@Valid @RequestBody Criacao e) {
    if (e.senha().getBytes(StandardCharsets.UTF_8).length > 72)
      throw ApiException.invalid("Escolha uma senha de até 72 bytes.");
    UUID id =
        jdbc.queryForObject(
            "select provisionar_empresa(?,?,array[?]::text[],?,?)",
            UUID.class,
            e.slug(),
            e.nome().trim(),
            e.operacao().name(),
            UsuarioAutenticado.id().toString(),
            "Cadastro pela administração");
    if (id == null) throw ApiException.conflict("Este identificador de empresa já está em uso.");
    jdbc.update(
        "insert into usuario(id,oficina_id,nome,email,senha_hash,papel) values(?,?,?,?,?,'OWNER')",
        UUID.randomUUID(),
        id,
        e.proprietario().trim(),
        e.email().trim().toLowerCase(Locale.ROOT),
        encoder.encode(e.senha()));
    auditar(id, "EMPRESA_CRIADA", "Empresa e proprietário cadastrados.");
    return obter(id);
  }

  @PutMapping("/{id}")
  public Detalhe editar(@PathVariable UUID id, @Valid @RequestBody Edicao e) {
    var revisions =
        jdbc.query(
            "select revisao_administracao from oficina where id=? for update",
            (r, n) -> r.getLong(1),
            id);
    if (revisions.isEmpty()) throw ApiException.missing();
    if (!revisions.getFirst().equals(e.revisao()))
      throw ApiException.conflict("A empresa mudou. Recarregue antes de salvar.");
    String slug = jdbc.queryForObject("select slug from oficina where id=?", String.class, id);
    jdbc.queryForObject(
        "select administrar_empresa(?,?,array[?]::text[],?,?)",
        Object.class,
        slug,
        e.status(),
        e.operacao().name(),
        UsuarioAutenticado.id().toString(),
        e.motivo());
    jdbc.update("update oficina set nome=? where id=?", e.nome().trim(), id);
    auditar(id, "EMPRESA_EDITADA", e.motivo());
    return obter(id);
  }

  @PutMapping("/{id}/identidade")
  @PreAuthorize("hasRole('DESENVOLVEDOR')")
  public EmpresaService.Empresa identidade(
      @PathVariable UUID id, @Valid @RequestBody EmpresaService.Edicao e) {
    var result = empresas.editar(id, e);
    auditar(id, "IDENTIDADE_EDITADA", "Nome e contato atualizados.");
    return result;
  }

  @PostMapping(value = "/{id}/imagens/{tipo}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('DESENVOLVEDOR')")
  public EmpresaService.Empresa upload(
      @PathVariable UUID id,
      @PathVariable String tipo,
      @RequestParam MultipartFile arquivo,
      @RequestParam long revisao) {
    var result = empresas.imagem(id, tipo, arquivo, revisao);
    auditar(id, "IMAGEM_EDITADA", tipo);
    return result;
  }

  @DeleteMapping("/{id}/imagens/{tipo}")
  @PreAuthorize("hasRole('DESENVOLVEDOR')")
  public EmpresaService.Empresa remover(
      @PathVariable UUID id, @PathVariable String tipo, @RequestParam long revisao) {
    var result = empresas.removerImagem(id, tipo, revisao);
    auditar(id, "IMAGEM_REMOVIDA", tipo);
    return result;
  }

  @GetMapping("/{id}/imagens/{tipo}/{imagemId}")
  public ResponseEntity<byte[]> imagem(
      @PathVariable UUID id, @PathVariable String tipo, @PathVariable UUID imagemId) {
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .body(empresas.imagem(id, tipo, imagemId));
  }

  @GetMapping("/{id}/auditoria")
  @Transactional(readOnly = true)
  public List<Map<String, Object>> historico(
      @PathVariable UUID id, @RequestParam(defaultValue = "0") int pagina) {
    obter(id);
    if (pagina < 0 || pagina > 100000) throw ApiException.invalid("Página inválida.");
    return jdbc.queryForList(
        "select a.id,a.acao,a.descricao,a.criado_em,u.nome autor from plataforma_auditoria a join plataforma_usuario u on u.id=a.autor_id where a.empresa_id=? order by a.id desc limit 20 offset ?",
        id,
        pagina * 20);
  }

  private void auditar(UUID id, String acao, String descricao) {
    jdbc.update(
        "insert into plataforma_auditoria(autor_id,empresa_id,acao,descricao) values(?,?,?,?)",
        UsuarioAutenticado.id(),
        id,
        acao,
        descricao);
  }
}
