# Padrão de erros — API v1

Escrito para quem consome a API (frontend) e para quem mexe no backend.

Fonte de verdade: `shared/error/ApiErrors.java`, `shared/error/ErrorCodes.java`,
`shared/error/ProblemJson.java`, `config/SecurityConfig.java` e `tenancy/TenantRequestFilter.java`.
Revisado em 15/09/2026, com o comportamento conferido por `Fase2IT`.

## Formato

Toda resposta de erro é RFC 7807, com `Content-Type: application/problem+json`. O corpo padrão do
Spring (`type`, `title`, `status`, `detail`) ganhou três campos do projeto: `code`, `timestamp` e
`requestId`. A lista `errors` aparece só na validação.

```json
{
  "type": "https://garagem.com.br/erros/validation_error",
  "title": "Bad Request",
  "status": 400,
  "detail": "email: deve ser um endereço de e-mail bem formado; nome: não deve estar em branco",
  "instance": "/api/v1/clientes",
  "code": "VALIDATION_ERROR",
  "timestamp": "2026-09-15T19:31:16.745Z",
  "requestId": "48eb85c4-15eb-457d-89c7-d726a6ec4ef1",
  "errors": [
    { "field": "email", "message": "deve ser um endereço de e-mail bem formado" },
    { "field": "nome", "message": "não deve estar em branco" }
  ]
}
```

### Como o frontend deve ler

- **Decida por `code`.** É o contrato estável. `detail` é texto para pessoas e pode ser reescrito a
  qualquer momento sem aviso.
- **`detail` pode ser exibido** ao usuário como está. Na validação ele traz o resumo campo a campo,
  comportamento que já existia na Fase 1 e foi preservado.
- **`errors` marca campo a campo** no formulário. `field` é o nome do campo do DTO de entrada.
- **`requestId`** é o mesmo valor do cabeçalho `X-Request-Id` da resposta e aparece no log do
  servidor. Peça esse número ao usuário quando for investigar um erro.
- **`instance`** é o caminho que produziu o erro. Vem do corpo padrão do Spring; é útil no log, não
  na tela.

## Códigos

| Status | `code`                   | Quando acontece |
|---|---|---|
| 400 | `VALIDATION_ERROR`       | Bean Validation recusou campos do corpo, ou faltou campo/parte obrigatória. Traz `errors`. |
| 400 | `INVALID_REQUEST`        | Corpo ilegível, tipo de parâmetro errado, enum inexistente, regra de negócio que depende do valor enviado (ordenação fora da allowlist, fuso inválido, KM menor que a cadastrada). |
| 401 | `UNAUTHORIZED`           | Sem token, token expirado ou inválido, usuário desativado, oficina inativa, credenciais de login erradas, refresh revogado/expirado/reutilizado. |
| 403 | `FORBIDDEN`              | Autenticado, mas o papel não permite a operação. |
| 404 | `NOT_FOUND`              | Recurso inexistente **ou de outra oficina**, e link público inválido, expirado ou revogado. |
| 405 | `METHOD_ALLOWED`\*       | Método HTTP não previsto para o recurso. |
| 409 | `CONFLICT`               | Conflito de estado: revisão desatualizada, transição de status não permitida, checklist já registrado, decisão já tomada, OS concluída. |
| 409 | `DUPLICATE`              | Violação de unicidade no banco (e-mail repetido na oficina, placa repetida na oficina). |
| 409 | `STALE_REVISION`         | Conflito detectado pelo controle de concorrência do JPA, não pela comparação explícita de revisão. |
| 413 | `PAYLOAD_TOO_LARGE`      | Upload acima do limite do multipart (10 MB). |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Conteúdo enviado não é PNG/JPEG válido, ou JSON enviado a endpoint multipart. |
| 500 | `INTERNAL_ERROR`         | Falha não prevista. Mensagem genérica. |
| 503 | `STORAGE_UNAVAILABLE`    | MinIO/S3 fora do ar durante upload ou leitura de foto. |

\* O valor literal da constante é `METHOD_NOT_ALLOWED`; veja `ErrorCodes`.

### Não existe 402

Havia três códigos `402` para limite de plano, armazenamento contratado e assinatura suspensa. A
cobrança saiu do produto — a venda é direta e a mensalidade é negociada em contrato, fora do
sistema —, e com ela saíram os três. Nenhuma resposta da API usa 402 hoje. Os limites que restam
são técnicos, não comerciais: tamanho de upload (413), paginação e rate limiting (429).

## Garantias

- **Nenhum stack trace, SQL, nome de classe ou nome de tabela** chega ao cliente. `server.error`
  está com `include-message: never` e `include-stacktrace: never`, e o handler de `Exception`
  registra só o nome simples da exceção no log, nunca na resposta.
- **404 em vez de 403 para recurso de outra oficina.** Responder 403 confirmaria que o id existe.
  Por isso todo acesso cruzado devolve 404, indistinguível de um id inventado.
- **401 de login não diz o que falhou.** Oficina inexistente, e-mail desconhecido, senha errada e
  usuário inativo devolvem exatamente o mesmo corpo. O login ainda compara a senha contra um hash
  descartável quando a conta não existe, para o tempo de resposta não denunciar a diferença.
- **Texto sempre em português, em qualquer ambiente.** As mensagens do Bean Validation eram
  resolvidas pelo locale da requisição: no contêiner, sem locale definido, `detail` saía "must not
  be blank", e um navegador com `Accept-Language: en-US` recebia inglês mesmo em servidor pt-BR.
  Como a tela mostra esse texto ao usuário final, o locale passou a ser fixo em `pt_BR`
  (`spring.web.locale` e `locale-resolver: fixed`) e não é negociado por cabeçalho.
- **Formato único, inclusive fora do Spring MVC.** Os 401/403 escritos pelo filtro de segurança e os
  404 do filtro de tenancy usam `ProblemJson`, que produz o mesmo corpo do `@RestControllerAdvice`.
  Não existe resposta de erro em outro formato na API.

## Ao adicionar um erro novo

1. Use `ApiException` com um `code` de `ErrorCodes`. Só crie constante nova se nenhuma servir.
2. Escreva `detail` para o usuário final, em português, sem jargão e sem dado interno.
3. Cubra o caso em `Fase2IT`, afirmando **status e `code`** — nunca o texto de `detail`.
4. Atualize a tabela acima.
