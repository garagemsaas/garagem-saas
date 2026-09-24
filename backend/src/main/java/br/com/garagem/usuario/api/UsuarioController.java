package br.com.garagem.usuario.api;

import br.com.garagem.shared.error.ApiException;
import br.com.garagem.shared.persistence.Pagina;
import br.com.garagem.shared.seguranca.UsuarioAutenticado;
import br.com.garagem.tenancy.SemModulo;
import br.com.garagem.tenancy.TenantContext;
import br.com.garagem.usuario.domain.*;
import br.com.garagem.usuario.repository.UsuarioRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@SemModulo
@RequestMapping("/api/v1/usuarios")
public class UsuarioController {
  private final UsuarioRepository repo;
  private final PasswordEncoder encoder;
  private final JdbcTemplate jdbc;

  public UsuarioController(UsuarioRepository repo, PasswordEncoder encoder, JdbcTemplate jdbc) {
    this.repo = repo;
    this.encoder = encoder;
    this.jdbc = jdbc;
  }

  @io.swagger.v3.oas.annotations.media.Schema(name = "UsuarioEntrada")
  public record Entrada(
      @NotBlank @Size(max = 160) String nome,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 12, max = 72) String senha,
      @NotNull Papel papel) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "UsuarioEdicao")
  public record Edicao(
      @NotBlank @Size(max = 160) String nome,
      @NotBlank @Email @Size(max = 254) String email,
      @Size(min = 12, max = 72) String senha,
      @NotNull Papel papel) {}

  @PutMapping("/{id}")
  @Transactional
  @PreAuthorize("hasRole('OWNER')")
  public Saida editar(@PathVariable UUID id, @Valid @RequestBody Edicao input) {
    bloquearEmpresa();
    validarPerfil(input.papel());
    var alvo =
        repo.findByIdAndOficinaId(id, TenantContext.current()).orElseThrow(ApiException::missing);
    if (alvo.id.equals(UsuarioAutenticado.id()) && alvo.papel != input.papel())
      throw ApiException.conflict("Você não pode alterar o próprio perfil de acesso.");
    if (alvo.ativo
        && alvo.papel == Papel.OWNER
        && input.papel() != Papel.OWNER
        && ownersAtivos() <= 1)
      throw ApiException.conflict("Mantenha pelo menos um proprietário ativo.");
    if (input.senha() != null) {
      if (input.senha().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
        throw ApiException.invalid("Escolha uma senha de até 72 bytes.");
      alvo.senhaHash = encoder.encode(input.senha());
    }
    alvo.versaoSessao++;
    alvo.nome = input.nome().trim();
    alvo.email = input.email().trim().toLowerCase(Locale.ROOT);
    alvo.papel = input.papel();
    jdbc.update(
        "update refresh_token set revogado_em=now(),motivo_revogacao='DESATIVACAO' where oficina_id=? and usuario_id=? and revogado_em is null",
        TenantContext.current(),
        id);
    auditar(
        id,
        "USUARIO_EDITADO",
        "Cadastro e perfil atualizados para "
            + input.papel().name()
            + (input.senha() != null ? "; senha redefinida." : "."));
    return Saida.de(alvo);
  }

  private void bloquearEmpresa() {
    jdbc.queryForObject(
        "select id from oficina where id=? for update", UUID.class, TenantContext.current());
  }

  private void validarPerfil(Papel papel) {
    if (papel == Papel.MECANICO
        && "REVENDA"
            .equals(
                jdbc.queryForObject(
                    "select modulo from empresa_modulo where oficina_id=?",
                    String.class,
                    TenantContext.current())))
      throw ApiException.invalid("Escolha um perfil disponível para esta operação.");
  }

  private void auditar(UUID id, String acao, String descricao) {
    jdbc.update(
        "insert into usuario_auditoria(oficina_id,autor_id,usuario_id,acao,descricao) values(?,?,?,?,?)",
        TenantContext.current(),
        UsuarioAutenticado.id(),
        id,
        acao,
        descricao);
  }

  @io.swagger.v3.oas.annotations.media.Schema(name = "UsuarioSituacaoEntrada")
  public record SituacaoEntrada(
      @io.swagger.v3.oas.annotations.media.Schema(
              description =
                  "false revoga o acesso imediatamente; true devolve, respeitando o plano")
          @NotNull
          Boolean ativo) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "UsuarioSaida")
  public record Saida(UUID id, String nome, String email, Papel papel, boolean ativo) {
    static Saida de(Usuario u) {
      return new Saida(u.id, u.nome, u.email, u.papel, u.ativo);
    }
  }

  /** Campos que a listagem aceita em {@code ordenacao}; qualquer outro é recusado com 400. */
  public static final Set<String> ORDENACAO = Set.of("nome", "email", "papel", "criadoEm");

  @GetMapping
  @Transactional(readOnly = true)
  @Operation(
      summary = "Listar equipe da oficina",
      description = "Somente dados públicos da equipe. Nenhuma senha ou hash é retornado.")
  public Pagina<Saida> listar(
      @RequestParam(defaultValue = "") String busca,
      @Parameter(description = "Restringe a um papel") @RequestParam(required = false) Papel papel,
      @Parameter(description = "true lista apenas quem pode acessar")
          @RequestParam(required = false)
          Boolean ativo,
      @Parameter(description = "Página, começando em 0") @RequestParam(defaultValue = "0")
          int pagina,
      @Parameter(description = "Itens por página, máximo 100") @RequestParam(defaultValue = "20")
          int tamanho,
      @Parameter(
              description = "campo,asc|desc — aceita nome, email, papel ou criadoEm",
              example = "nome,asc")
          @RequestParam(required = false)
          String ordenacao) {
    if (busca.length() > 160) throw ApiException.invalid("A busca deve ter até 160 caracteres.");
    return Pagina.de(
        repo.filtrar(
                TenantContext.current(),
                papel,
                ativo,
                "%" + busca.trim().toLowerCase(Locale.ROOT) + "%",
                Pagina.request(pagina, tamanho, ordenacao, ORDENACAO))
            .map(Saida::de));
  }

  @PostMapping
  @Transactional
  @PreAuthorize("hasRole('OWNER')")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  @Operation(summary = "Cadastrar usuário na oficina atual")
  public Saida criar(@Valid @RequestBody Entrada input) {
    bloquearEmpresa();
    validarPerfil(input.papel());
    // Limite do plano antes de qualquer trabalho: desativar, editar e excluir seguem livres, só a
    // criação consome vaga. A contagem é de usuários ativos, feita no banco.

    if (input.senha().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw ApiException.invalid("Senha excede 72 bytes.");
    Usuario u = new Usuario();
    u.nome = input.nome().trim();
    u.email = input.email().trim().toLowerCase(Locale.ROOT);
    u.senhaHash = encoder.encode(input.senha());
    u.papel = input.papel();
    repo.save(u);
    repo.flush();
    auditar(u.id, "USUARIO_CRIADO", "Usuario cadastrado com perfil " + u.papel.name());
    org.slf4j.LoggerFactory.getLogger(UsuarioController.class)
        .atInfo()
        .addKeyValue("novo_usuario_id", u.id)
        .log("usuario_criado");
    return Saida.de(u);
  }

  @PutMapping("/{id}/situacao")
  @Transactional
  @PreAuthorize("hasRole('OWNER')")
  @Operation(
      summary = "Ativar ou desativar o acesso de um usuário",
      description =
          "Revogação de acesso da oficina. Desativar tem efeito imediato: a sessão é conferida no"
              + " banco a cada requisição, e os refresh tokens do usuário são revogados na hora."
              + " Nenhum dado do usuário é apagado, e a autoria dos registros é preservada.")
  public Saida situacao(@PathVariable UUID id, @Valid @RequestBody SituacaoEntrada input) {
    bloquearEmpresa();
    var alvo =
        repo.findByIdAndOficinaId(id, TenantContext.current()).orElseThrow(ApiException::missing);
    if (alvo.ativo == input.ativo()) return Saida.de(alvo);
    if (!input.ativo()) {
      // Desativar a si mesmo tranca o próprio dono para fora da oficina, e ninguém mais poderia
      // reativá-lo pela aplicação: seria preciso mexer no banco.
      if (alvo.id.equals(UsuarioAutenticado.id()))
        throw ApiException.conflict("Você não pode desativar o próprio acesso.");
      if (alvo.papel == Papel.OWNER && ownersAtivos() <= 1)
        throw ApiException.conflict(
            "Esta é a última pessoa com acesso de proprietário. Promova outra antes de desativar.");
    }
    alvo.versaoSessao++;
    alvo.ativo = input.ativo();
    auditar(id, "ACESSO_ALTERADO", input.ativo() ? "Acesso ativado." : "Acesso desativado.");
    if (!input.ativo())
      jdbc.update(
          "update refresh_token set revogado_em=now(), motivo_revogacao='DESATIVACAO' where oficina_id=? and usuario_id=? and revogado_em is null",
          TenantContext.current(),
          alvo.id);
    org.slf4j.LoggerFactory.getLogger(UsuarioController.class)
        .atInfo()
        .addKeyValue("usuario_alvo_id", alvo.id)
        .addKeyValue("usuario_ativo", alvo.ativo)
        .log("usuario_situacao_alterada");
    return Saida.de(alvo);
  }

  /** Conta proprietários que ainda conseguem entrar, para não deixar a oficina sem dono. */
  private long ownersAtivos() {
    Long total =
        jdbc.queryForObject(
            "select count(*) from usuario where oficina_id=? and papel='OWNER' and ativo=true",
            Long.class,
            TenantContext.current());
    return total == null ? 0 : total;
  }
}
