# Fase 2 — parte do Cauã

Escrito para o time do projeto e para quem for revisar a entrega.

Escopo: API e contratos, validação funcional do backend, segurança e infraestrutura. Não inclui os
itens de produto atribuídos ao outro desenvolvedor.

Branch: `feature/fase2-api-contracts-security`. Sem commit, push ou merge.
Última execução completa de `mvn verify` em 15/09/2026: **BUILD SUCCESS**, 12 testes unitários e 39
de integração, zero falhas.

Evidências citadas como `Fase2IT#metodo` estão em
`backend/src/test/java/br/com/garagem/Fase2IT.java`.

---

## CONCLUÍDO

### API e contratos

| Área | Item | Evidência / teste | Observação |
|---|---|---|---|
| API | Documentar endpoints existentes | `docs/api-v1.md`; `Fase2IT#openApiRepresentaAApiReal` | 33 operações em 23 caminhos, conferidas contra o `/v3/api-docs` da aplicação em execução. |
| API | Estabilizar DTOs | Revisão dos DTOs; `Fase2IT#filtrosDeUsuario` | Nenhuma entidade JPA é devolvida; todos os DTOs já eram `record`. Nada removido nem renomeado. |
| API | Padronizar respostas de validação | `docs/api-errors.md`; `Fase2IT#validacaoDevolveCamposECodigo` | RFC 7807 com `code`, `timestamp`, `requestId` e `errors[]`. `detail` preservado como estava. |
| API | Padronizar erros em geral | `Fase2IT#statusECodigoPorSituacao`, `#erroSempreUsaProblemJson` | Um formato só, inclusive nos 401/403 do filtro de segurança e nos 404 do filtro de tenancy. |
| API | Definir paginação | `Fase2IT#paginacaoTemPadraoETeto`, `#envelopeDePaginaEIgualEmTodaListagem` | Padrão da Fase 1 preservado. Página 0, tamanho 20, teto 100. `totalPaginas` acrescentado. |
| API | Definir filtros | `Fase2IT#filtrosDeCliente`, `#filtrosDeVeiculo`, `#filtrosDeOrdemServico`, `#filtrosDeUsuario` | Filtros nomeados em quatro listagens, sempre presos ao tenant. Coringa do cliente tratado como literal. |
| API | Definir ordenação | `Fase2IT#ordenacaoRespeitaAllowlist`, `#allowlistDeOrdenacaoPorRecurso`, `#ordenacaoDeOsPorNumero` | `ordenacao=campo,asc\|desc` com allowlist por recurso, reaplicada no repositório. |
| API | Resposta de dashboard | `Fase2IT#dashboardResumeAOperacao`, `#dashboardIgnoraOrcamentoJaDecidido`, `#dashboardEIsoladoPorOficina` | `GET /api/v1/dashboard` com DTO próprio, agregado no banco. |
| API | Atualizar Swagger | `Fase2IT#openApiRepresentaAApiReal` | Bearer JWT, endpoints públicos sem token, multipart, parâmetros e respostas 400/401/403/404/409 compartilhadas. |

### Validação funcional

| Área | Item | Evidência / teste | Observação |
|---|---|---|---|
| Sessão | Login | `Fase2IT#loginValidaOficinaUsuarioESenha` | Oficina válida e inexistente, senha certa e errada, conta inativa, credenciais incompletas, tenant e papel no token. Senha e hash nunca saem. |
| Sessão | Refresh token | `Fase2IT#refreshRespeitaValidadeERotacao` | Válido, inventado, expirado, revogado, de outra oficina e reutilizado após rotação. |
| Sessão | Logout | `Fase2IT#logoutRevogaEEhIdempotente` | Revoga, bloqueia reutilização e responde 204 mesmo repetido ou com token inexistente. |
| Sessão | Revogação imediata | `Fase2IT#usuarioDesativadoPerdeAcessoImediatamente` | Conta desativada perde acesso sem esperar o access token expirar. |
| Cadastros | Clientes | `Fase2IT#filtrosDeCliente`, `#paginacaoTemPadraoETeto`, `#ordenacaoRespeitaAllowlist`, `#statusECodigoPorSituacao` | Criar, consultar, listar, atualizar, filtros, paginação, ordenação, validações e conflito de revisão. |
| Cadastros | Veículos | `Fase2IT#filtrosDeVeiculo`, `#isolamentoEntreOficinas` | Vínculo com cliente, placa normalizada, filtros, paginação. Cliente de outra oficina responde 404. |
| OS | Ordens de serviço | `Fase2IT#filtrosDeOrdemServico`, `#ordenacaoDeOsPorNumero`; `Fase1IT` para o fluxo | Abertura, consulta, listagem, status, revisão, filtros, ordenação e isolamento. |
| OS | Responsáveis | `Fase2IT#filtrosDeOrdemServico`, `#isolamentoEntreOficinas`; `Fase1IT` | Atribuição e troca. Mecânico de outra oficina responde 404; inativo ou papel incompatível, 400. |
| OS | Checklist | `Fase2IT#matrizDePermissoes`, `#isolamentoEntreOficinas`; `Fase1IT` | Criação, leitura, validação de itens, vínculo com a OS e unicidade. |
| OS | Diagnóstico | `Fase2IT#matrizDePermissoes`, `#isolamentoEntreOficinas`; `Fase1IT` | Criação, classificações válidas e inválidas, listagem, vínculo e isolamento. |
| OS | Fotos | `Fase2IT#uploadRecusaConteudoQueNaoEImagem`, `#downloadDeFotoEhPrivadoEComCabecalhosSeguros` | Multipart, tipo pelo conteúdo, tamanho, storage, acesso autenticado, acesso cruzado bloqueado e cabeçalhos. |
| OS | Orçamentos | `Fase1IT`; `Fase2IT#dashboardIgnoraOrcamentoJaDecidido` | Versão, itens, total no servidor, imutabilidade por trigger, nova versão, versão substituída, aprovação, recusa e conflito. |
| OS | Aprovação pública | `Fase1IT`; `Fase2IT#dashboardIgnoraOrcamentoJaDecidido` | Link, token, expiração, revogação, visualização, decisão, segunda decisão e versão antiga. |
| OS | Timeline | `Fase1IT`; `Fase2IT#isolamentoEntreOficinas` | Eventos persistidos e imutáveis, em ordem, com origem e autor. |

### Segurança e infraestrutura

| Área | Item | Evidência / teste | Observação |
|---|---|---|---|
| Tenancy | Isolamento entre oficinas | `Fase2IT#isolamentoEntreOficinas`, `#isolamentoNoSentidoInverso` | Duas oficinas com dados próprios. Leitura por id, escrita, associação, fotos, orçamento, filtros e dashboard, nos dois sentidos. Sempre 404, nunca 403. |
| Permissões | Matriz por papel | `docs/permissoes.md`; `Fase2IT#matrizDePermissoes` | 16 operações × 3 papéis, mais usuário de outra oficina e sem sessão. A tabela do documento é a lista do teste. |
| Tokens | Expiração revisada | `Fase2IT#loginValidaOficinaUsuarioESenha` | `JWT_ACCESS_TTL`, `JWT_REFRESH_TTL` e `PUBLIC_LINK_TTL` por variável de ambiente, com padrões seguros. O teste roda com TTL diferente do padrão para provar que vem da configuração. |
| Uploads | Revisão do upload privado | `Fase2IT#uploadRecusaConteudoQueNaoEImagem`, `#downloadDeFotoEhPrivadoEComCabecalhosSeguros` | Tipo pelo conteúdo decodificado, reescrita da imagem, chave só com UUID, bucket privado, `no-store` e `nosniff`. |
| CORS | Configurável por ambiente | `CorsPropertiesTest`; verificado no stack em execução | `CORS_ALLOWED_ORIGINS`, vazio por padrão. Curinga e origem malformada derrubam a subida. |
| Secrets | Nada versionado | `git check-ignore .env`; revisão de `.env.example` | Tudo por variável de ambiente. `.env` ignorado; exemplos só com marcadores. |
| Logs | Configuração | `RequestLoggingFilterTest`; `docs/staging.md` | JSON estruturado com `request_id`, `oficina_id` e `usuario_id`; `X-Request-Id` na resposta. Token do link público mascarado. |
| Health | Checks | `Fase2IT#healthCobreBancoEStorage`; stack em execução | `/actuator/health` agrega banco e storage. Sondas liveness/readiness. Demais endpoints fechados. Healthcheck no Dockerfile e no Compose. |
| Staging | Preparado e documentado | `docs/staging.md`, `.env.staging.example` | Requisitos, variáveis, subida, validação, logs, backup, atualização e rollback. Sem deploy externo. |
| Build | `mvn verify` | Execução de 15/09/2026 | BUILD SUCCESS. 12 unitários + 39 de integração. Spotless e Flyway incluídos. |
| Docker | Stack sobe | `docker compose up -d --build` verificado | Postgres healthy, MinIO no ar, API healthy, `/actuator/health` 200, Swagger 200, CORS conferido. |

---

## PENDENTE

| Área | Item | Observação |
|---|---|---|
| Frontend | Consumir `GET /api/v1/dashboard` | O contrato está pronto e o tipo `Dashboard` existe em `frontend/src/api/types.ts`. **Não existe método `api.getDashboard()`** — a linha anterior desta tabela afirmava o contrário; corrigido na Fase 3. O cliente HTTP é a função genérica `api<T>(path)`, então a chamada é `api<Dashboard>('/dashboard')`. Trocar o cálculo local de `dashboard-model.ts` pelos números do backend é trabalho de produto, do outro desenvolvedor. |
| Frontend | Usar filtros e ordenação novos | Os parâmetros existem e estão documentados; as telas ainda usam só `busca`. |
| Frontend | Usar `errors[]` para marcar campos | Hoje a tela exibe `detail`. O campo novo permite destacar cada input. |
| Staging | Subida efetiva | Depende de servidor e autorização. Nenhum deploy externo foi feito, conforme a instrução. |
| Filtros | Consultas de cliente, veículo e usuário | Continuam no formato estático `(:param is null or ...)`. Funcionam e estão cobertas por teste; padronizá-las com o repositório dinâmico da OS é melhoria, não correção. |

## BLOQUEADO

| Área | Item | Motivo |
|---|---|---|
| — | Nenhum | Nenhum item do escopo ficou bloqueado. |

---

## Correções feitas durante a fase

| Problema | Onde | Correção |
|---|---|---|
| `GET /ordens-servico` respondia 500 (`SQLState 42P18`, "could not determine data type of parameter $22") em toda listagem, mesmo sem filtro de período | `ordemservico/repository` | A consulta era estática com `(:param is null or ...)`. Com `Instant` nulo o parâmetro chega ao PostgreSQL sem tipo e `$22 IS NULL` não dá contexto para inferir, então a instrução falhava no parse. A listagem passou a ser montada em tempo de execução: filtro não informado não vira predicado. |
| Upload de arquivo que não é imagem respondia 400 | `foto/application/FotoService` | Passou a responder 415, como pede o critério de aceite. Única mudança de status da fase; a asserção correspondente em `Fase1IT` foi ajustada. |
| JSON enviado a endpoint multipart respondia 500 | `shared/error/ApiErrors` | `HttpMediaTypeNotSupportedException` passou a ser tratada, respondendo 415. |
| 401 e 403 saíam em formato próprio, diferente do resto da API | `config/SecurityConfig`, `tenancy/TenantRequestFilter` | Passaram a usar o mesmo corpo RFC 7807, via `ProblemJson`. |
| API sem configuração de CORS | `config/SecurityConfig`, `config/CorsProperties` | Origens explícitas por variável de ambiente, fechado por padrão, curinga recusado na subida. |
| TTL de token e de link fixos no código | `auth/application/AuthService`, `ordemservico/application/OsService` | Passaram a vir da configuração. |

## Como verificar

```bash
cd backend && ./mvnw verify          # unitários, integração, Spotless, Flyway
docker compose up -d --build         # exige .env preenchido
curl -i http://127.0.0.1:8080/actuator/health
```

Validação manual completa em [staging.md](../operations/staging.md#validação-pós-subida).
