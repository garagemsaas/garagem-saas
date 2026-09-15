# API v1 — inventário de contratos

Escrito para quem consome a API (frontend) e para quem mantém o backend.

Fonte de verdade: os Controllers, DTOs e serviços deste checkout, mais `SecurityConfig`,
`TenantRequestFilter`, `Pagina`, `Filtros` e as migrations V1/V2. Inventário levantado em
15/09/2026 e conferido contra o `/v3/api-docs` da aplicação em execução: **33 operações de negócio
em 23 caminhos**. Exemplos são fictícios; nenhum token aqui é utilizável.

Sucede `docs/api-contracts.md`, da Fase 1, que continua valendo para o que não mudou. As diferenças
da Fase 2 estão reunidas em [O que mudou na Fase 2](#o-que-mudou-na-fase-2).

## Convenções

- **Base**: `/api/v1`. JSON na entrada e na saída, exceto upload multipart, download binário e
  respostas 204.
- **Sessão**: `Authorization: Bearer <accessToken>`. A oficina vem do JWT validado, confrontado a
  cada requisição com usuário ativo, papel atual e oficina ativa. **Não existe header, query ou
  campo de corpo para escolher outra oficina.**
- **Papéis**: `OWNER`, `ATENDENTE`, `MECANICO`. Nas tabelas, **Todos** = as três roles e
  **Escritório** = `OWNER` e `ATENDENTE`. A matriz completa está em [permissoes.md](permissoes.md).
- **Datas** são ISO-8601 em UTC (`2026-09-15T19:31:16Z`). Ids são UUID. Valores monetários são
  decimais com duas casas, em texto JSON numérico.
- **Campos opcionais** podem vir `null`. Revisão começa em zero.
- **Erros** seguem RFC 7807 com `code` estável — veja [api-errors.md](api-errors.md). Todo endpoint
  protegido pode responder 401 e 403; id inexistente ou de outra oficina responde 404. As tabelas
  listam apenas os erros específicos de cada operação.
- **`X-Request-Id`** vem em toda resposta e é o mesmo valor do campo `requestId` dos erros.

## Paginação

Todas as listagens usam o mesmo envelope e os mesmos parâmetros. O padrão vem da Fase 1 e foi
mantido; a Fase 2 apenas acrescentou `totalPaginas`.

| Parâmetro | Padrão | Regra |
|---|---|---|
| `pagina` | `0` | Índice base 0. Valor negativo é tratado como 0. |
| `tamanho` | `20` | Aparado ao intervalo 1–100. **Não existe listagem ilimitada**: `tamanho=100000` devolve 100. |

```json
{
  "itens": [],
  "pagina": 0,
  "tamanho": 20,
  "total": 137,
  "totalPaginas": 7
}
```

`total` é a contagem de registros que atendem ao filtro, não o tamanho da página. Página além do fim
responde `itens: []` com 200, não erro.

## Ordenação

Parâmetro `ordenacao`, no formato `campo,asc|desc`. Sem direção, assume `asc`. Sem o parâmetro,
vale o padrão do projeto: **`criadoEm` decrescente**, com `id` como desempate estável.

Cada listagem tem a sua allowlist. **Campo fora da lista responde 400 `INVALID_REQUEST`** — nenhum
texto do cliente vira cláusula SQL sem passar por ela, e a allowlist é reaplicada na camada de
repositório.

| Listagem | Campos aceitos |
|---|---|
| `/clientes` | `nome`, `telefone`, `email`, `criadoEm` |
| `/veiculos` | `placa`, `marca`, `modelo`, `ano`, `km`, `criadoEm` |
| `/ordens-servico` | `numero`, `status`, `criadoEm`, `previsaoEntrega`, `concluidaEm` |
| `/usuarios` | `nome`, `email`, `papel`, `criadoEm` |

Exemplo: `GET /api/v1/ordens-servico?ordenacao=numero,desc`.

## Filtros

Filtros são opcionais e combinam entre si com **E lógico**. Os textuais ignoram maiúsculas e
minúsculas e casam por trecho. `%` e `_` enviados pelo cliente são tratados como texto literal, não
como coringa de SQL.

**Todo filtro respeita a oficina do token.** Um `clienteId` ou `mecanicoId` de outra oficina não
casa com nada: a resposta é página vazia, nunca o registro alheio.

| Listagem | Filtros |
|---|---|
| `/clientes` | `busca` (nome, telefone ou e-mail), `nome`, `telefone`, `email` |
| `/veiculos` | `busca` (placa, marca ou modelo), `placa`, `marca`, `modelo`, `clienteId` |
| `/ordens-servico` | `busca` (placa, nome do cliente ou número), `numero`, `status`, `clienteId`, `veiculoId`, `mecanicoId`, `placa`, `de`, `ate` |
| `/usuarios` | `papel`, `ativo` |

- `busca` é o campo único de pesquisa que o frontend já usa; os demais servem a filtros de tela.
- `placa` é comparada sem hífen nem espaço, como é gravada: `bbb-2222` casa com `BBB2222`.
- `de` e `ate` recortam a **data de abertura** da OS, inclusive nas duas pontas, em ISO-8601.
- Valor inválido é recusado com 400, não ignorado: `status=INVENTADO`, `numero=abc`,
  `de=ontem` e `clienteId=nao-e-uuid` respondem `INVALID_REQUEST`.

## Inventário

`O` abrevia `/ordens-servico/{id}`. Nas fotos o Controller chama esse parâmetro de `osId`. As
abreviações encurtam a tabela, não são caminhos alternativos.

### Auth

| Método | Caminho | Sessão | Request | Response | Erros específicos |
|---|---|---|---|---|---|
| POST | `/auth/login` | — | `Login` | 200 `Sessao` | 400 validação; 401 oficina, e-mail, senha ou conta inativa, sem distinguir qual |
| POST | `/auth/refresh` | Refresh válido | `Refresh` | 200 `Sessao` | 400; 401 expirado, revogado, reutilizado ou de outra oficina |
| POST | `/auth/logout` | — | `Refresh` | 204 | 400. Idempotente: repetir ou enviar token inexistente também responde 204 |

`Login` = `{ oficina (slug), email, senha }`. `Refresh` = `{ oficinaId, refreshToken }`.
`Sessao` = `{ accessToken, refreshToken, expiresIn, oficinaId, usuarioId, nome, papel }`.
`expiresIn` é o TTL do access token em segundos, lido da configuração. **Nenhuma resposta devolve
senha ou hash.**

### Usuários

| Método | Caminho | Papéis | Query | Response | Erros específicos |
|---|---|---|---|---|---|
| GET | `/usuarios` | Todos | `papel`, `ativo`, `pagina`, `tamanho`, `ordenacao` | 200 `Pagina<UsuarioSaida>` | Só dados públicos da equipe |
| POST | `/usuarios` | OWNER | — | 201 `UsuarioSaida` | 400 senha com menos de 12 ou mais de 72 bytes; 409 `DUPLICATE` e-mail repetido na oficina |

`UsuarioSaida` = `{ id, nome, email, papel, ativo }`.

### Clientes

| Método | Caminho | Papéis | Request / Query | Response | Erros específicos |
|---|---|---|---|---|---|
| GET | `/clientes` | Todos | `busca`, `nome`, `telefone`, `email`, `pagina`, `tamanho`, `ordenacao` | 200 `Pagina<ClienteSaida>` | 400 ordenação fora da allowlist |
| GET | `/clientes/{id}` | Todos | — | 200 `ClienteSaida` | 400 UUID inválido; 404 |
| POST | `/clientes` | Escritório | `ClienteEntrada` | 201 `ClienteSaida` | 400. Revisão de criação é zero |
| PUT | `/clientes/{id}` | Escritório | `ClienteEntrada` | 200 `ClienteSaida` | 400; 404; 409 revisão desatualizada |

`ClienteEntrada` = `{ nome, telefone, email?, revisao }`.
`ClienteSaida` = `{ id, nome, telefone, email, revisao }`.

### Veículos

| Método | Caminho | Papéis | Request / Query | Response | Erros específicos |
|---|---|---|---|---|---|
| GET | `/veiculos` | Todos | `busca`, `placa`, `marca`, `modelo`, `clienteId`, `pagina`, `tamanho`, `ordenacao` | 200 `Pagina<VeiculoSaida>` | 400 ordenação fora da allowlist |
| GET | `/veiculos/{id}` | Todos | — | 200 `VeiculoSaida` | 400; 404 |
| POST | `/veiculos` | Escritório | `VeiculoEntrada` | 201 `VeiculoSaida` | 400; **404 cliente de outra oficina**; 409 placa repetida na oficina |
| PUT | `/veiculos/{id}` | Escritório | `VeiculoEntrada` | 200 `VeiculoSaida` | 400 KM menor que a cadastrada; 404; 409 revisão ou placa |

`VeiculoEntrada` = `{ clienteId, placa, marca, modelo, ano, km, cor, revisao }`.
`VeiculoSaida` acrescenta `id`. A placa é gravada sem separadores e em maiúsculas. A quilometragem
nunca diminui.

### Ordens de serviço

| Método | Caminho | Papéis | Request / Query | Response | Erros específicos |
|---|---|---|---|---|---|
| GET | `/ordens-servico` | Todos | `busca`, `numero`, `status`, `clienteId`, `veiculoId`, `mecanicoId`, `placa`, `de`, `ate`, `pagina`, `tamanho`, `ordenacao` | 200 `Pagina<OsSaida>` | 400 filtro ou ordenação inválidos |
| GET | `O` | Todos | — | 200 `OsSaida` | 400; 404 |
| POST | `/ordens-servico` | Escritório | `NovaOs` | 201 `OsSaida` | 400 KM de entrada abaixo da do veículo, ou responsável que não é mecânico ativo; 404 veículo ou mecânico de outra oficina |
| PUT | `O/responsavel` | Escritório | `ResponsavelEntrada` | 200 `OsSaida` | 400 responsável inválido; 404; 409 revisão ou OS concluída |
| POST | `O/status` | Todos | `StatusEntrada` | 200 `OsSaida` | 409 revisão, transição não permitida, orçamento ausente ou já decidido, ou tentativa de iniciar manutenção sem aprovação do cliente |

`NovaOs` = `{ veiculoId, mecanicoId?, kmEntrada, relato, previsaoEntrega? }`.
`OsSaida` = `{ id, numero, veiculoId, clienteId, mecanicoId, status, kmEntrada, relato, criadoEm,
previsaoEntrega, concluidaEm, revisao }`.

**Fluxo de status** (`StatusOs`), conforme `StatusOs#permite`:

```
RECEBIDO → DIAGNOSTICO → ORCAMENTO → AGUARDANDO_APROVACAO
                              ↑              ↓
                              └──────── (recusa)
                                             ↓ (aprovação pelo link)
                          EM_MANUTENCAO ⇄ AGUARDANDO_PECA
                                 ↓
                               TESTE → PRONTO
```

`PRONTO` é terminal e carimba `concluidaEm`. A passagem de `AGUARDANDO_APROVACAO` para
`EM_MANUTENCAO` **só acontece pela decisão do cliente no link público**; pedi-la pela API interna
responde 409.

### Checklist

| Método | Caminho | Papéis | Request | Response | Erros específicos |
|---|---|---|---|---|---|
| GET | `O/checklist` | Todos | — | 200 `ChecklistSaida` | 404 OS, ou checklist ainda não registrado |
| POST | `O/checklist` | Todos | `ChecklistEntradaDto` | 201 `ChecklistSaida` | 400 item sem descrição ou condição; 409 já registrado, ou OS concluída |

De 1 a 100 itens, cada um com `descricao`, `condicao` e `observacao?`. O checklist de entrada é
único por OS.

### Diagnóstico

| Método | Caminho | Papéis | Request | Response | Erros específicos |
|---|---|---|---|---|---|
| GET | `O/diagnosticos` | Todos | — | 200 `DiagnosticoSaida[]` | 404 OS. Array vazio quando não há itens |
| POST | `O/diagnosticos` | OWNER, MECANICO | `DiagnosticoEntrada` | 201 `DiagnosticoSaida` | 400 classificação inválida; 409 OS concluída |

`classificacao` ∈ `VERDE`, `AMARELO`, `VERMELHO`. Valor fora disso responde 400.

### Orçamentos

| Método | Caminho | Papéis | Request | Response | Erros específicos |
|---|---|---|---|---|---|
| GET | `O/orcamento/versoes` | Todos | — | 200 `VersaoSaida[]` | 404 OS. Array vazio quando não há orçamento |
| POST | `O/orcamento/versoes` | Escritório | `VersaoEntrada` | 201 `VersaoSaida` | 400 itens inválidos; 409 OS fora de `ORCAMENTO`/`AGUARDANDO_APROVACAO` |

Versões e itens são **imutáveis**: triggers no banco recusam `UPDATE` e `DELETE` em
`orcamento_versao`, `item_orcamento`, `aprovacao_orcamento` e `evento_ordem_servico`. Corrigir um
orçamento significa criar a versão seguinte, que passa a ser a atual; a anterior permanece no
histórico. `total` é somado no servidor a partir dos itens — o valor enviado pelo cliente não é
aceito. `VersaoSaida` traz `decisao` (`{ aprovado, criadoEm, canal }`) ou `null`.

### Fotos

| Método | Caminho | Papéis | Request | Response | Erros específicos |
|---|---|---|---|---|---|
| GET | `/ordens-servico/{osId}/fotos` | Todos | — | 200 `FotoSaida[]` | 404 OS. Nenhum endereço de storage na resposta |
| POST | `/ordens-servico/{osId}/fotos` | Todos | multipart | 201 `FotoSaida` | 400 finalidade/descrição inválida ou acima de 20 MP; 404 vínculo; 409 OS concluída; 413 acima de 10 MB; **415 conteúdo que não é PNG/JPEG**; 503 storage fora |
| GET | `/ordens-servico/{osId}/fotos/{fotoId}/conteudo` | Todos | — | 200 bytes | 404 OS, foto ou vínculo; 503 storage fora |

Multipart: parte `arquivo` mais os parâmetros `finalidade` (`ENTRADA`, `DIAGNOSTICO` ou `SERVICO`),
`descricao?`, `checklistItemId?` e `diagnosticoItemId?` — os dois últimos são mutuamente exclusivos
e precisam pertencer à mesma OS.

O tipo é decidido pelo **conteúdo decodificado**, nunca pela extensão ou pelo `Content-Type`
enviados: um arquivo de texto chamado `foto.png` responde 415. A imagem é reescrita pelo servidor,
o que descarta metadados como GPS. A chave do objeto é montada com UUIDs
(`{oficinaId}/{osId}/{fotoId}`), então não há caminho para path traversal, e o bucket é privado —
os bytes sempre passam pela API. O download responde com `Cache-Control: no-store`,
`X-Content-Type-Options: nosniff` e `Content-Disposition: inline` com nome derivado do id.

### Link público e aprovação

| Método | Caminho | Papéis | Request | Response | Erros específicos |
|---|---|---|---|---|---|
| POST | `O/links` | Escritório | — | 201 `LinkSaida` | 404 OS. **O token aparece uma única vez** |
| DELETE | `O/links/{linkId}` | Escritório | — | 204 | 404 OS, link ou vínculo |
| GET | `/publico/{token}` | Token público | — | 200 `PublicoSaida` | 404 token malformado, inexistente, expirado ou revogado |
| POST | `/publico/{token}/decisao` | Token público | `DecisaoEntrada` | 200 `PublicoSaida` | 400; 404; 409 versão substituída, decisão contrária já registrada, ou OS fora de `AGUARDANDO_APROVACAO` |

O token tem 43 caracteres `[A-Za-z0-9_-]`, vale por sete dias (configurável) e é armazenado apenas
como hash SHA-256. Ele escopa **uma única OS**. Não dá para enumerar: formato errado, token
inexistente, expirado e revogado respondem 404 idênticos.

`PublicoSaida` = `{ numero, status, veiculo (texto), previsaoEntrega, orcamento }` e nada mais. Sem
dados do cliente, sem fotos, sem timeline, sem ids internos. O orçamento só acompanha a resposta
quando a OS já passou de `AGUARDANDO_APROVACAO`.

`DecisaoEntrada` = `{ versaoId, aprovado }`. Exigir o `versaoId` evita que o cliente aprove sem
querer uma versão diferente da que está vendo. Repetir a **mesma** decisão é idempotente; a decisão
contrária responde 409. Aprovar leva a OS a `EM_MANUTENCAO`; recusar devolve a `ORCAMENTO`.

### Timeline

| Método | Caminho | Papéis | Response | Erros específicos |
|---|---|---|---|---|
| GET | `O/timeline` | Todos | 200 `EventoSaida[]` | 404 OS |

`EventoSaida` = `{ id, tipo, descricao, origem, autorId, criadoEm }`, em ordem cronológica
crescente. `origem` é `USUARIO` ou `LINK_PUBLICO`; `autorId` é `null` quando a ação veio do cliente
pelo link. Os eventos são fatos persistidos e imutáveis: `OS_ABERTA`, `STATUS_ALTERADO`,
`RESPONSAVEL_ALTERADO`, `CHECKLIST_REGISTRADO`, `DIAGNOSTICO_REGISTRADO`, `ORCAMENTO_VERSIONADO`,
`ORCAMENTO_APROVADO`, `ORCAMENTO_RECUSADO`, `FOTO_ADICIONADA`, `LINK_CRIADO`, `LINK_REVOGADO`.

### Dashboard

| Método | Caminho | Papéis | Query | Response |
|---|---|---|---|---|
| GET | `/dashboard` | Todos | `fuso` (IANA, padrão `America/Sao_Paulo`) | 200 `DashboardSaida` |

Existe para que a tela inicial pare de baixar a listagem de OS só para contar. Tudo é agregado no
banco, restrito à oficina do token. `fuso` inválido responde 400.

```json
{
  "geradoEm": "2026-09-15T19:31:16Z",
  "porStatus": {
    "RECEBIDO": 2, "DIAGNOSTICO": 1, "ORCAMENTO": 0, "AGUARDANDO_APROVACAO": 1,
    "EM_MANUTENCAO": 3, "AGUARDANDO_PECA": 0, "TESTE": 1, "PRONTO": 4
  },
  "emAndamento": 8,
  "prontas": 4,
  "concluidasSeteDias": 6,
  "entradasHoje": 2,
  "atrasadas": 1,
  "semResponsavel": 3,
  "orcamentosAguardandoDecisao": { "quantidade": 1, "total": 250.00 }
}
```

| Campo | Significado |
|---|---|
| `porStatus` | Contagem por status. **Todos os oito status aparecem**, inclusive zerados, para a tela não precisar adivinhar chaves. |
| `emAndamento` | OS que ainda não chegaram a `PRONTO`. |
| `prontas` | OS em `PRONTO`, aguardando retirada. |
| `concluidasSeteDias` | `concluidaEm` dentro dos últimos sete dias. |
| `entradasHoje` | OS abertas hoje, no fuso consultado. |
| `atrasadas` | Não concluídas cuja `previsaoEntrega` já passou. |
| `semResponsavel` | Não concluídas sem mecânico atribuído. |
| `orcamentosAguardandoDecisao` | Versão atual do orçamento de OS em `AGUARDANDO_APROVACAO` ainda sem decisão. `total` é a soma dessas versões e **não representa receita**. |

Mapeamento para os rótulos da tela: "aguardando diagnóstico" é `porStatus.RECEBIDO`; "aguardando
aprovação" é `porStatus.AGUARDANDO_APROVACAO`; "em manutenção" é `porStatus.EM_MANUTENCAO`.

Fora de escopo aqui, por decisão da Fase 2: Dinheiro Esquecido, BI, métricas financeiras e
relatórios.

### Infraestrutura

Sem sessão. Não são módulos de negócio.

| Caminho | Resposta |
|---|---|
| `GET /actuator/health` | 200 `{"status":"UP","groups":["liveness","readiness"]}`, ou 503 quando banco ou storage estão fora. `show-details: never`. |
| `GET /actuator/health/liveness` e `/readiness` | Sondas para orquestrador e healthcheck do contêiner. |
| `GET /v3/api-docs` | OpenAPI 3 em JSON. |
| `GET /swagger-ui.html` | Redireciona para `/swagger-ui/index.html`. |

Nenhum outro endpoint do Actuator está exposto: `/actuator/env`, `/beans` e `/loggers` respondem
erro 4xx.

## O que não existe

Para evitar que alguém programe contra um endpoint imaginado: não há `/auth/me`, `/sessao`, GET de
usuário individual, PUT/DELETE de usuário, recurso de orçamento separado das versões, inserção
avulsa de item de orçamento, edição ou exclusão de checklist, diagnóstico ou foto, decisão interna
de orçamento (a decisão é sempre do cliente, pelo link) nem listagem de links emitidos. Os dados do
usuário autenticado vêm do `Sessao` devolvido no login e no refresh.

## O que mudou na Fase 2

Tudo aqui é aditivo. **Nenhum campo foi removido ou renomeado**, e os parâmetros da Fase 1
(`busca`, `pagina`, `tamanho`) continuam funcionando como antes.

| Mudança | Impacto no frontend |
|---|---|
| `GET /api/v1/dashboard` | Endpoint novo. |
| `totalPaginas` no envelope de página | Campo novo; ignorá-lo não quebra nada. |
| `ordenacao` em quatro listagens | Parâmetro novo, opcional. Sem ele, a ordem é a da Fase 1. |
| Filtros nomeados em quatro listagens | Parâmetros novos, opcionais. |
| `code`, `timestamp`, `requestId` e `errors` nos erros | Campos novos. `detail` e `status` seguem iguais, inclusive o resumo campo a campo da validação. |
| Upload de conteúdo que não é imagem passou de 400 para **415** | Única mudança de status da Fase 2. Feita para atender ao critério de aceite, que exige distinguir tipo de arquivo não suportado. O cliente continua vendo a recusa e o `detail`. |
| TTL de token e de link agora são configuráveis | `expiresIn` passa a refletir a configuração do ambiente em vez de 900 fixo. |
| `X-Request-Id` em toda resposta | Cabeçalho novo. |
