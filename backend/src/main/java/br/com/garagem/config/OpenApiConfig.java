package br.com.garagem.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.*;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentação OpenAPI. Além do esquema Bearer, registra as respostas de erro compartilhadas e as
 * anexa a toda operação, para que o Swagger mostre a API como ela realmente responde.
 */
@Configuration
public class OpenApiConfig {

  @Bean
  OpenAPI openApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Garagem SaaS — API v1")
                .version("v1")
                .description(
                    """
                    API REST multi-tenant da oficina. Todo endpoint de negócio vive sob `/api/v1` \
                    e opera exclusivamente na oficina do token: não há parâmetro para escolher \
                    outra. Envie `Authorization: Bearer <accessToken>`.

                    Listagens usam `pagina` (base 0), `tamanho` (padrão 20, máximo 100) e \
                    `ordenacao` no formato `campo,asc|desc`, restrito a uma allowlist por \
                    endpoint. Erros seguem RFC 7807 com um campo `code` estável — veja \
                    `docs/api-errors.md`.

                    Os endpoints sob `/api/v1/publico/{token}` não usam JWT: o token do link é a \
                    própria credencial, escopada a uma única OS.\
                    """))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearer",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"))
                .addSchemas("Problema", problema())
                .addResponses("Invalido", resposta("Request inválido ou validação recusada", true))
                .addResponses("NaoAutenticado", resposta("Sem sessão válida", false))
                .addResponses("SemPermissao", resposta("Papel não autorizado", false))
                .addResponses(
                    "NaoEncontrado", resposta("Recurso inexistente ou de outra oficina", false))
                .addResponses(
                    "Conflito", resposta("Conflito de estado ou revisão desatualizada", false)))
        .addSecurityItem(new SecurityRequirement().addList("bearer"));
  }

  /** Anexa as respostas de erro comuns a cada operação, sem repetir anotação em cada método. */
  @Bean
  OpenApiCustomizer respostasDeErro() {
    return api ->
        api.getPaths()
            .forEach(
                (caminho, item) ->
                    item.readOperations()
                        .forEach(
                            operacao -> {
                              var respostas = operacao.getResponses();
                              referenciar(respostas, "400", "Invalido");
                              if (caminho.startsWith("/api/v1/auth")) {
                                // Sessão não endereça recurso: 404 e 409 não têm como acontecer
                                // aqui, e declará-los faria o Swagger contradizer api-v1.md.
                                referenciar(respostas, "401", "NaoAutenticado");
                                return;
                              }
                              referenciar(respostas, "404", "NaoEncontrado");
                              referenciar(respostas, "409", "Conflito");
                              if (!caminho.startsWith("/api/v1/publico")) {
                                referenciar(respostas, "401", "NaoAutenticado");
                                referenciar(respostas, "403", "SemPermissao");
                              }
                            }));
  }

  private static void referenciar(
      io.swagger.v3.oas.models.responses.ApiResponses respostas, String status, String componente) {
    if (respostas.containsKey(status)) return;
    respostas.addApiResponse(
        status, new ApiResponse().$ref("#/components/responses/" + componente));
  }

  private static ApiResponse resposta(String descricao, boolean comCampos) {
    var exemplo =
        comCampos
            ? """
              {"type":"https://garagem.com.br/erros/validation_error","title":"Bad Request",\
              "status":400,"detail":"Dados inválidos.","code":"VALIDATION_ERROR",\
              "timestamp":"2026-09-15T12:00:00Z",\
              "errors":[{"field":"email","message":"deve ser um endereço de e-mail bem formado"}]}"""
            : """
              {"type":"https://garagem.com.br/erros/not_found","title":"Not Found","status":404,\
              "detail":"Registro não encontrado.","code":"NOT_FOUND",\
              "timestamp":"2026-09-15T12:00:00Z"}""";
    return new ApiResponse()
        .description(descricao)
        .content(
            new Content()
                .addMediaType(
                    "application/problem+json",
                    new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/Problema"))
                        .addExamples("exemplo", new Example().value(exemplo))));
  }

  private static Schema<?> problema() {
    var campo =
        new ObjectSchema()
            .description("Campo recusado pela validação")
            .addProperty("field", new StringSchema().example("email"))
            .addProperty("message", new StringSchema().example("E-mail inválido"));
    return new ObjectSchema()
        .description("Erro no formato RFC 7807 com código estável de contrato")
        .addProperty("type", new StringSchema())
        .addProperty("title", new StringSchema())
        .addProperty("status", new IntegerSchema().example(400))
        .addProperty(
            "detail", new StringSchema().description("Texto para pessoas; pode mudar sem aviso"))
        .addProperty(
            "code",
            new StringSchema()
                .description("Código estável para o frontend decidir")
                .example("VALIDATION_ERROR"))
        .addProperty("timestamp", new StringSchema().format("date-time"))
        .addProperty("requestId", new StringSchema().description("Mesmo valor de X-Request-Id"))
        .addProperty(
            "errors",
            new ArraySchema().items(campo).description("Presente apenas em VALIDATION_ERROR"))
        .required(List.of("status", "detail", "code"));
  }
}
