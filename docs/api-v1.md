# API v1 — inventário de contratos

Escrito para quem consome a API (frontend) e para quem mantém o backend.

Fonte de verdade: os Controllers, DTOs e serviços deste checkout, mais `SecurityConfig`,
`TenantRequestFilter`, `Pagina`, `Filtros` e as migrations V1/V2. Inventário levantado em
15/09/2026 e conferido contra o `/v3/api-docs` da aplicação em execução: **33 operações de negócio
em 23 caminhos**. Exemplos são fictícios; nenhum token aqui é utilizável.

Sucede `docs/api-contracts.md`, da Fase 1, que continua valendo para o que não mudou. As diferenças
da Fase 2 estão reunidas em [O que mudou na Fase 2](#o-que-mudou-na-fase-2) e as da Fase 3 em
[O que mudou na Fase 3](#o-que-mudou-na-fase-3).

Para **integrar o frontend**, comece por
[Contratos da primeira integração](#contratos-da-primeira-integração): os seis contratos da Fase 3
estão publicados lá com papéis, parâmetros, DTOs, erros e exemplos de requisição e resposta. O
inventário abaixo continua sendo a lista completa da API.

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
- Em `busca`, a **placa** é comparada normalizada e os demais campos com o texto original. Por isso
  `rst-1d23` encontra a placa `RST1D23` e, ao mesmo tempo, `Argo Drive` e `CR-V` encontram o modelo.
  Antes da Fase 3 as duas listagens erravam lados opostos disso.
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

## Contratos da primeira integração

Esta seção existe para que a integração do frontend seja feita **sem abrir código Java**. São os seis
contratos que a Fase 3 publica, cada um com papéis, parâmetros, DTOs, erros e exemplos conferidos
contra a aplicação em execução em 15/09/2026. Os ids, tokens, nomes e placas dos exemplos são
fictícios; nenhum deles é utilizável.

Regras que valem para os seis e não se repetem em cada um:

- **Base** `/api/v1`, JSON na entrada e na saída.
- **Sessão** `Authorization: Bearer <accessToken>` em tudo, menos no login.
- **Oficina** vem sempre do token. Não existe parâmetro, header ou campo de corpo para escolher
  outra. Id de outra oficina responde **404**, nunca 403 — [ver o porquê](api-errors.md#garantias).
- **Erros** em RFC 7807 com `code` estável. **Decida por `code`**; `detail` é texto para pessoas,
  já em português, e pode ser reescrito. Detalhe em [api-errors.md](api-errors.md).
- **`X-Request-Id`** vem em toda resposta e repete o `requestId` do corpo de erro.
- **Todo endpoint protegido** pode responder 401 (sem sessão, token expirado, conta desativada) e
  403 (papel insuficiente). As tabelas abaixo listam só o que é específico da operação.

---

### 1. Login

| | |
|---|---|
| **Método e endpoint** | `POST /api/v1/auth/login` |
| **Finalidade** | Abrir sessão e obter o par de tokens mais a identidade do usuário. |
| **Autenticação** | Nenhuma. É o endpoint que cria a sessão. |
| **Papéis** | Qualquer um. O papel é resultado, não requisito. |
| **Headers** | `Content-Type: application/json`. |
| **Path/query params** | Nenhum. |

**Request — `ClienteEntrada` não; aqui é `Login`**

| Campo | Tipo | Obrigatório | Regra |
|---|---|:--:|---|
| `oficina` | string | ✅ | **Slug** da oficina, não o nome nem o id. Até 80 caracteres; comparado sem diferenciar maiúsculas e espaços nas pontas. |
| `email` | string | ✅ | Até 254 caracteres, formato de e-mail. Comparado em minúsculas. |
| `senha` | string | ✅ | Até 72 bytes. |

**Response 200 — `Sessao`**

| Campo | Tipo | Significado |
|---|---|---|
| `accessToken` | string | JWT HS256 para o header `Authorization`. |
| `refreshToken` | string | Token opaco de **uso único**, para `POST /auth/refresh`. |
| `expiresIn` | number | Validade do access token **em segundos**, lida da configuração do ambiente (`JWT_ACCESS_TTL`). Não presuma 900. |
| `oficinaId` | uuid | Necessário no corpo de `/auth/refresh` e `/auth/logout`. |
| `usuarioId` | uuid | Identidade do usuário logado. |
| `nome` | string | Nome de exibição. |
| `papel` | string | `OWNER`, `ATENDENTE` ou `MECANICO`. Use para habilitar a interface, nunca como controle de acesso: quem decide é o backend. |

A resposta tem **exatamente esses sete campos**. Senha e hash nunca saem da API, em nenhuma rota.

**Códigos e erros**

| Status | `code` | Quando |
|---|---|---|
| 200 | — | Sessão emitida. |
| 400 | `VALIDATION_ERROR` | Campo ausente, e-mail malformado, tamanho excedido. Traz `errors[]`. |
| 401 | `UNAUTHORIZED` | Oficina inexistente ou inativa, e-mail desconhecido, senha errada, **conta desativada**. |

**Os quatro motivos de 401 devolvem o mesmo corpo, de propósito.** Nada na resposta diz qual deles
falhou, e o login compara a senha contra um hash descartável quando a conta não existe, para o tempo
de resposta também não denunciar. Na tela, mostre uma única mensagem do tipo "confira a oficina, o
e-mail e a senha". Não tente distinguir os casos.

Não há conflito de revisão nem multi-tenancy a tratar aqui: o `oficina` do corpo **escopa a busca**,
não concede acesso. O mesmo e-mail em duas oficinas são duas contas distintas.

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "oficina": "oficina-exemplo",
  "email": "owner@exemplo.test",
  "senha": "<senha do usuário>"
}
```

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.<payload>.<assinatura>",
  "refreshToken": "oalgFj3QfHRexemploFicticioDeTokenOpaco43",
  "expiresIn": 900,
  "oficinaId": "755b6667-ce2d-4095-af5c-72dfd670fe50",
  "usuarioId": "c0021a81-d285-4379-8894-6135fd578266",
  "nome": "Oficina Exemplo",
  "papel": "OWNER"
}
```

Credenciais recusadas:

```json
{
  "type": "https://garagem.com.br/erros/unauthorized",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Credenciais inválidas ou expiradas.",
  "instance": "/api/v1/auth/login",
  "code": "UNAUTHORIZED",
  "timestamp": "2026-09-15T23:50:12.104Z",
  "requestId": "57cb6e62-e5ea-4cdb-9a77-8a22aafe468e"
}
```

**Renovar e encerrar.** `POST /auth/refresh` e `POST /auth/logout` recebem
`{ "oficinaId": "…", "refreshToken": "…" }`. O refresh **rotaciona**: devolve uma `Sessao` nova e
invalida o token usado, então guarde sempre o último recebido. Reutilizar um token já rotacionado
responde 401. O logout responde **204** e é idempotente — repetir, ou enviar um token inexistente,
continua devolvendo 204.

---

### 2. Clientes

| Operação | Método e endpoint | Papéis |
|---|---|---|
| Listar | `GET /api/v1/clientes` | Todos |
| Consultar | `GET /api/v1/clientes/{id}` | Todos |
| Cadastrar | `POST /api/v1/clientes` | OWNER, ATENDENTE |
| Atualizar | `PUT /api/v1/clientes/{id}` | OWNER, ATENDENTE |

Não existe exclusão de cliente. `{id}` é UUID; valor que não é UUID responde 400, não 404.

**Query da listagem**

| Parâmetro | Padrão | Efeito |
|---|---|---|
| `busca` | vazio | Campo único de pesquisa: casa com **nome, telefone ou e-mail**. |
| `nome`, `telefone`, `email` | — | Filtros de tela, por trecho. |
| `pagina` | `0` | Índice base 0. |
| `tamanho` | `20` | Aparado ao intervalo 1–100. |
| `ordenacao` | `criadoEm,desc` | `nome`, `telefone`, `email` ou `criadoEm`, no formato `campo,asc|desc`. |

Filtros combinam com **E lógico**, ignoram maiúsculas e casam por trecho. `%` e `_` digitados são
texto literal, não coringa de SQL. Fora da allowlist de ordenação: **400 `INVALID_REQUEST`**.

**Request — `ClienteEntrada`** (o mesmo corpo no POST e no PUT)

| Campo | Tipo | Obrigatório | Regra |
|---|---|:--:|---|
| `nome` | string | ✅ | Até 160 caracteres, não em branco. |
| `telefone` | string | ✅ | Até 30 caracteres, não em branco. Gravado como digitado. |
| `email` | string | ❌ | Até 254 caracteres. Pode ser omitido ou `null`; se vier preenchido precisa ser um e-mail válido. |
| `revisao` | number | ✅ no PUT | Revisão que a tela leu. Ignorado no POST, onde a revisão nasce **0**. |

**Response — `ClienteSaida`**: `{ id, nome, telefone, email, revisao }`. `email` pode ser `null`.

**Códigos e erros**

| Status | `code` | Quando |
|---|---|---|
| 200 / 201 | — | Consulta e atualização / cadastro. |
| 400 | `VALIDATION_ERROR` | Campo recusado. Traz `errors[]` com `field` e `message`. |
| 400 | `INVALID_REQUEST` | `{id}` que não é UUID, ordenação fora da allowlist. |
| 403 | `FORBIDDEN` | `MECANICO` tentando cadastrar ou atualizar. |
| 404 | `NOT_FOUND` | Id inexistente **ou de outra oficina**. |
| 409 | `CONFLICT` | `revisao` enviada diferente da atual. |
| 409 | `STALE_REVISION` | Duas escritas simultâneas passaram pela conferência e o banco barrou a segunda. |

**Revisão.** O PUT responde com o registro **já gravado e com a revisão nova**, então a tela pode
encadear edições sem recarregar. No 409 nada foi gravado: recarregue o registro, mostre o valor
atual e deixe a pessoa decidir. Nunca reenvie o mesmo corpo com a revisão nova automaticamente —
isso é exatamente a sobrescrita silenciosa que o controle existe para impedir.

**Multi-tenant.** O cliente sempre nasce na oficina do token. Um `OWNER` da oficina B tem papel de
sobra para `PUT /clientes/{id}` e mesmo assim recebe 404 ao apontar para um cliente da oficina A.

```http
POST /api/v1/clientes
Authorization: Bearer <accessToken>
Content-Type: application/json

{ "nome": "Marina Alves", "telefone": "11987654321", "email": "marina.alves@exemplo.test" }
```

```json
{
  "id": "43688b50-fa08-48c6-8821-ef8690460adc",
  "nome": "Marina Alves",
  "telefone": "11987654321",
  "email": "marina.alves@exemplo.test",
  "revisao": 0
}
```

`GET /api/v1/clientes?busca=marina&ordenacao=nome,asc`:

```json
{
  "itens": [
    {
      "id": "43688b50-fa08-48c6-8821-ef8690460adc",
      "nome": "Marina Alves",
      "telefone": "11987654321",
      "email": "marina.alves@exemplo.test",
      "revisao": 0
    }
  ],
  "pagina": 0,
  "tamanho": 20,
  "total": 1,
  "totalPaginas": 1
}
```

Atualização com revisão vencida:

```json
{
  "type": "https://garagem.com.br/erros/conflict",
  "title": "Conflict",
  "status": 409,
  "detail": "Cliente alterado. Atualize a página.",
  "instance": "/api/v1/clientes/43688b50-fa08-48c6-8821-ef8690460adc",
  "code": "CONFLICT",
  "timestamp": "2026-09-15T23:50:44.036Z",
  "requestId": "e90fe277-6968-4efd-ad7f-3844a6d2d01b"
}
```

---

### 3. Veículos

| Operação | Método e endpoint | Papéis |
|---|---|---|
| Listar | `GET /api/v1/veiculos` | Todos |
| Consultar | `GET /api/v1/veiculos/{id}` | Todos |
| Cadastrar | `POST /api/v1/veiculos` | OWNER, ATENDENTE |
| Atualizar | `PUT /api/v1/veiculos/{id}` | OWNER, ATENDENTE |

**Query da listagem**

| Parâmetro | Padrão | Efeito |
|---|---|---|
| `busca` | vazio | Campo único: casa com **placa, marca ou modelo**. |
| `placa` | — | Por trecho, comparada **sem hífen nem espaço**. |
| `marca`, `modelo` | — | Por trecho, com o texto como foi digitado. |
| `clienteId` | — | Veículos de um cliente **da mesma oficina**. |
| `pagina`, `tamanho` | `0`, `20` | Como em clientes. |
| `ordenacao` | `criadoEm,desc` | `placa`, `marca`, `modelo`, `ano`, `km` ou `criadoEm`. |

**Busca por placa.** A placa é gravada sem separadores e em maiúsculas, e a pesquisa normaliza antes
de comparar: `rst-1d23`, `RST 1D23` e `rst1d23` encontram a mesma placa. Marca e modelo continuam
comparados com o texto original, então `Argo Drive` e `CR-V` também são encontráveis.

**Request — `VeiculoEntrada`**

| Campo | Tipo | Obrigatório | Regra |
|---|---|:--:|---|
| `clienteId` | uuid | ✅ | Precisa ser cliente **da mesma oficina**; caso contrário 404. |
| `placa` | string | ✅ | Padrão `LLLNLNN` com hífen ou espaço opcionais (`ABC1D23`, `abc-1d23`). Formato diferente responde 400. |
| `marca` | string | ✅ | Até 80 caracteres. |
| `modelo` | string | ✅ | Até 100 caracteres. |
| `ano` | number | ✅ | Entre 1886 e 2200. |
| `km` | number | ✅ | Zero ou positivo. **Nunca diminui** num PUT. |
| `cor` | string | ✅ | Até 60 caracteres. |
| `revisao` | number | ✅ no PUT | Como em clientes; nasce 0 no POST. |

**Response — `VeiculoSaida`**: `{ id, clienteId, placa, marca, modelo, ano, km, cor, revisao }`.

**Vínculo com o cliente.** O `clienteId` é obrigatório desde o cadastro e pode ser trocado no PUT,
desde que o novo cliente seja da mesma oficina. Não existe veículo sem dono.

**Códigos e erros**

| Status | `code` | Quando |
|---|---|---|
| 201 / 200 | — | Cadastro / consulta e atualização. |
| 400 | `VALIDATION_ERROR` | Placa fora do padrão, ano fora da faixa, campo em branco. |
| 400 | `INVALID_REQUEST` | KM menor que a cadastrada, ordenação fora da allowlist. |
| 403 | `FORBIDDEN` | `MECANICO` tentando escrever. |
| 404 | `NOT_FOUND` | Veículo inexistente/de outra oficina, **ou `clienteId` de outra oficina**. |
| 409 | `CONFLICT` | `revisao` desatualizada. |
| 409 | `DUPLICATE` | Placa já cadastrada **nesta oficina**. A mesma placa pode existir em outra oficina. |

**Multi-tenant.** `clienteId` de outra oficina responde 404 na escrita e devolve **página vazia** no
filtro — nunca o registro alheio.

```http
POST /api/v1/veiculos
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "clienteId": "43688b50-fa08-48c6-8821-ef8690460adc",
  "placa": "rst-1d23",
  "marca": "Fiat",
  "modelo": "Argo Drive",
  "ano": 2021,
  "km": 48250,
  "cor": "Prata"
}
```

```json
{
  "id": "e6ae57d7-8c4d-4314-a185-fb208b057f36",
  "clienteId": "43688b50-fa08-48c6-8821-ef8690460adc",
  "placa": "RST1D23",
  "marca": "Fiat",
  "modelo": "Argo Drive",
  "ano": 2021,
  "km": 48250,
  "cor": "Prata",
  "revisao": 0
}
```

Repare que a placa voltou normalizada: guarde o valor da resposta, não o que foi digitado.

Placa repetida na oficina:

```json
{
  "type": "https://garagem.com.br/erros/duplicate",
  "title": "Conflict",
  "status": 409,
  "detail": "Dados duplicados ou relacionamento inválido.",
  "instance": "/api/v1/veiculos",
  "code": "DUPLICATE",
  "timestamp": "2026-09-15T23:51:02.881Z",
  "requestId": "8c5b0f4a-2f61-4d0a-9a33-5f5a6b1c77d0"
}
```

---

### 4. Listagem de ordens de serviço

| | |
|---|---|
| **Método e endpoint** | `GET /api/v1/ordens-servico` |
| **Finalidade** | Alimentar a tela de operação. É a chamada mais usada do produto. |
| **Papéis** | Todos. |
| **Response** | 200 `Pagina<OsSaida>` |

Todos os parâmetros abaixo são **opcionais** e combinam com **E lógico**. Só existem estes: qualquer
outro nome é ignorado pelo servidor, então confira a grafia antes de concluir que o filtro "não
funciona".

| Parâmetro | Tipo | Efeito |
|---|---|---|
| `busca` | string | Campo único: casa com **placa, nome do cliente ou número da OS**. A placa é comparada sem hífen nem espaço; nome e número, com o texto original. |
| `numero` | number | Número **exato** da OS. Valor não numérico responde 400. |
| `status` | enum | Um dos oito valores de `StatusOs`. Valor fora do enum responde 400 — não é ignorado. |
| `clienteId` | uuid | OS de um cliente da mesma oficina. |
| `veiculoId` | uuid | OS de um veículo da mesma oficina. |
| `mecanicoId` | uuid | OS de um responsável. **Não existe filtro "sem responsável"** nesta listagem; use `semResponsavel` do `/dashboard`. |
| `placa` | string | Por trecho, normalizada. |
| `de` | ISO-8601 | Abertas **a partir** deste instante, inclusive. Recorta `criadoEm`. |
| `ate` | ISO-8601 | Abertas **até** este instante, inclusive. Recorta `criadoEm`. |
| `pagina` | number | Índice base 0. Padrão `0`. |
| `tamanho` | number | Padrão `20`, aparado a 1–100. **Não existe listagem ilimitada.** |
| `ordenacao` | string | `numero`, `status`, `criadoEm`, `previsaoEntrega` ou `concluidaEm`, no formato `campo,asc|desc`. |

`de` e `ate` podem vir sozinhos ou juntos; nenhuma combinação é obrigatória. Sem `ordenacao`, a
ordem é `criadoEm` decrescente com `id` como desempate estável — ou seja, estável entre páginas.

**Envelope de paginação** — o mesmo de todas as listagens da API:

```json
{ "itens": [], "pagina": 0, "tamanho": 20, "total": 137, "totalPaginas": 7 }
```

`total` é a contagem que atende ao filtro, não o tamanho da página. Página além do fim responde
`itens: []` com **200**, não erro.

**`OsSaida`** — o mesmo objeto na listagem e no detalhe:

| Campo | Tipo | Observação |
|---|---|---|
| `id` | uuid | |
| `numero` | number | Sequencial **por oficina**, legível pelo cliente. |
| `veiculoId`, `clienteId` | uuid | Referências. A listagem **não devolve nome nem placa** — veja o contrato de detalhe. |
| `mecanicoId` | uuid \| null | `null` quando não há responsável. |
| `status` | enum | |
| `kmEntrada` | number | |
| `relato` | string | |
| `criadoEm` | ISO-8601 | Data de abertura; é o campo que `de`/`ate` recortam. |
| `previsaoEntrega` | ISO-8601 \| null | |
| `concluidaEm` | ISO-8601 \| null | Carimbado ao entrar em `PRONTO`. |
| `revisao` | number | Necessária para mudar status e responsável. |

**Erros**: 400 `INVALID_REQUEST` para filtro ou ordenação inválidos; 401/403 como sempre. A listagem
**não responde 404**: sem resultado, devolve página vazia.

**Multi-tenant**: a listagem só enxerga a oficina do token. `clienteId`, `veiculoId` ou `mecanicoId`
de outra oficina devolvem página vazia.

**Exemplos**

```http
GET /api/v1/ordens-servico?pagina=0&tamanho=20
GET /api/v1/ordens-servico?status=AGUARDANDO_APROVACAO
GET /api/v1/ordens-servico?mecanicoId=6b1f2c88-0f2e-4f5b-9f0f-6a1d2e3c4b5a
GET /api/v1/ordens-servico?de=2026-09-01T00:00:00Z&ate=2026-09-30T23:59:59Z
GET /api/v1/ordens-servico?ordenacao=numero,desc
GET /api/v1/ordens-servico?busca=rst-1d23
GET /api/v1/ordens-servico?status=EM_MANUTENCAO&clienteId=43688b50-fa08-48c6-8821-ef8690460adc&pagina=1&tamanho=50
```

Resposta de `GET /api/v1/ordens-servico?ordenacao=numero,desc`:

```json
{
  "itens": [
    {
      "id": "1e41609e-4185-4d88-bf0d-22695120234a",
      "numero": 1,
      "veiculoId": "e6ae57d7-8c4d-4314-a185-fb208b057f36",
      "clienteId": "43688b50-fa08-48c6-8821-ef8690460adc",
      "mecanicoId": null,
      "status": "RECEBIDO",
      "kmEntrada": 48250,
      "relato": "Barulho na suspensão dianteira ao passar em lombada.",
      "criadoEm": "2026-09-15T23:50:31.341467Z",
      "previsaoEntrega": "2026-09-19T18:00:00Z",
      "concluidaEm": null,
      "revisao": 0
    }
  ],
  "pagina": 0,
  "tamanho": 20,
  "total": 1,
  "totalPaginas": 1
}
```

Status inventado:

```json
{
  "type": "https://garagem.com.br/erros/invalid_request",
  "title": "Bad Request",
  "status": 400,
  "detail": "Dados inválidos. Confira os campos enviados.",
  "instance": "/api/v1/ordens-servico",
  "code": "INVALID_REQUEST",
  "timestamp": "2026-09-15T23:52:10.220Z",
  "requestId": "bcc774f7-da0d-4e21-bfd8-b1ed534e7428"
}
```

---

### 5. Detalhe da ordem de serviço

**O detalhe não é uma chamada só, e isto é de propósito.** `GET /api/v1/ordens-servico/{id}` devolve
o `OsSaida` e nada mais: cliente, veículo, checklist, diagnóstico, orçamento, fotos e histórico são
recursos próprios, com autorização e ciclo de vida próprios. A tela monta o detalhe combinando as
chamadas abaixo. Nenhuma delas foi inflada para evitar round-trips.

| # | Chamada | Papéis | Resposta | Quando falta |
|---|---|---|---|---|
| 1 | `GET /ordens-servico/{id}` | Todos | `OsSaida` | 404 se não existir ou for de outra oficina |
| 2 | `GET /veiculos/{veiculoId}` | Todos | `VeiculoSaida` | — |
| 3 | `GET /clientes/{clienteId}` | Todos | `ClienteSaida` | — |
| 4 | `GET /usuarios` | Todos | `Pagina<UsuarioSaida>` | Resolve o nome do `mecanicoId` |
| 5 | `GET /ordens-servico/{id}/checklist` | Todos | `ChecklistSaida` | **404** enquanto não houver checklist |
| 6 | `GET /ordens-servico/{id}/diagnosticos` | Todos | `DiagnosticoSaida[]` | `[]` |
| 7 | `GET /ordens-servico/{id}/orcamento/versoes` | Todos | `VersaoSaida[]` | `[]` |
| 8 | `GET /ordens-servico/{id}/fotos` | Todos | `FotoSaida[]` | `[]` |
| 9 | `GET /ordens-servico/{id}/timeline` | Todos | `EventoSaida[]` | Nunca vazia: a abertura já gera evento |

**Ordem.** A chamada 1 vem primeiro, porque dela saem o `veiculoId`, o `clienteId` e o `mecanicoId`
das chamadas 2 a 4. As chamadas 5 a 9 dependem só do `{id}` e podem sair **em paralelo com a 1**.

**O 404 do checklist é esperado, não é erro.** Só ele responde 404 por ausência; as demais coleções
respondem lista vazia. Trate o 404 de `/checklist` como "ainda não registrado" e siga; qualquer
outro 404 nessa sequência significa OS inexistente ou de outra oficina.

**Revisão.** A revisão que vale para escrever é a do `OsSaida` da chamada 1. Registrar checklist,
diagnóstico, foto ou versão de orçamento **não muda** a revisão da OS; mudar status ou responsável
muda. Depois de mudar status, use a revisão devolvida por aquela própria resposta.

**Fotos.** `FotoSaida` = `{ id, finalidade, descricao, contentType, tamanho, checklistItemId,
diagnosticoItemId }` — **metadados apenas, nenhum endereço de storage**. Os bytes vêm de
`GET /ordens-servico/{osId}/fotos/{fotoId}/conteudo`, que exige a mesma sessão e responde com
`Cache-Control: no-store` e `X-Content-Type-Options: nosniff`. O bucket é privado: não existe URL
que o navegador possa abrir sem passar pela API, então a imagem precisa ser buscada com o header
`Authorization` e exibida a partir do blob.

**Orçamento.** `GET /orcamento/versoes` devolve **todo o histórico**, em ordem crescente de `numero`.
A versão atual é a de maior `numero`; as anteriores continuam ali e são imutáveis. Cada `VersaoSaida`
traz `decisao` (`{ aprovado, criadoEm, canal }`) ou `null` quando ainda não houve decisão do cliente.

**Timeline.** `EventoSaida` = `{ id, tipo, descricao, origem, autorId, criadoEm }`, em ordem
cronológica crescente. `origem` é `USUARIO` ou `LINK_PUBLICO`; `autorId` é `null` quando a ação veio
do cliente pelo link.

Detalhe de uma OS recém-aberta, chamada 1:

```json
{
  "id": "1e41609e-4185-4d88-bf0d-22695120234a",
  "numero": 1,
  "veiculoId": "e6ae57d7-8c4d-4314-a185-fb208b057f36",
  "clienteId": "43688b50-fa08-48c6-8821-ef8690460adc",
  "mecanicoId": null,
  "status": "DIAGNOSTICO",
  "kmEntrada": 48250,
  "relato": "Barulho na suspensão dianteira ao passar em lombada.",
  "criadoEm": "2026-09-15T23:50:31.341467Z",
  "previsaoEntrega": "2026-09-19T18:00:00Z",
  "concluidaEm": null,
  "revisao": 1
}
```

Chamada 9, timeline:

```json
[
  {
    "id": "cafa9cc3-3508-43ca-b48c-e1b5a57387fe",
    "tipo": "OS_ABERTA",
    "descricao": "OS recebida na oficina.",
    "origem": "USUARIO",
    "autorId": "c0021a81-d285-4379-8894-6135fd578266",
    "criadoEm": "2026-09-15T23:50:31.355442Z"
  },
  {
    "id": "fb7a9ca8-e0b3-47f2-8306-630b6333b632",
    "tipo": "STATUS_ALTERADO",
    "descricao": "RECEBIDO → DIAGNOSTICO",
    "origem": "USUARIO",
    "autorId": "c0021a81-d285-4379-8894-6135fd578266",
    "criadoEm": "2026-09-15T23:50:43.841986Z"
  }
]
```

Chamada 5 antes de existir checklist — resposta normal do fluxo:

```json
{
  "type": "https://garagem.com.br/erros/not_found",
  "title": "Not Found",
  "status": 404,
  "detail": "Registro não encontrado.",
  "instance": "/api/v1/ordens-servico/1e41609e-4185-4d88-bf0d-22695120234a/checklist",
  "code": "NOT_FOUND",
  "timestamp": "2026-09-15T23:50:44.230Z",
  "requestId": "9cf273ea-03f1-41f0-92ec-ed100e819469"
}
```

---

### 6. Alteração de status

| | |
|---|---|
| **Método e endpoint** | `POST /api/v1/ordens-servico/{id}/status` |
| **Finalidade** | Avançar a OS pela máquina de estados. |
| **Papéis** | **OWNER, ATENDENTE e MECANICO.** É a única escrita da OS que o mecânico faz. |
| **Headers** | `Authorization`, `Content-Type: application/json`. |

**Request — `StatusEntrada`**

| Campo | Tipo | Obrigatório | Regra |
|---|---|:--:|---|
| `status` | enum | ✅ | Status de **destino**. Fora do enum: 400. |
| `revisao` | number | ✅ na prática | Revisão lida no `OsSaida`. Omitir equivale a enviar `0` e conflita assim que a OS tiver qualquer alteração. |

**Response 200** — o `OsSaida` já no novo status e **com a revisão nova**. Use essa revisão na
próxima escrita; não é preciso reconsultar a OS.

**A escolha do destino não é livre.** O backend tem máquina de estados e recusa qualquer outro
caminho:

```
RECEBIDO → DIAGNOSTICO → ORCAMENTO → AGUARDANDO_APROVACAO
                             ↑              │
                             └── recusa ────┤
                                            │ aprovação pelo link do cliente
                                            ↓
                         EM_MANUTENCAO ⇄ AGUARDANDO_PECA
                                │
                                ↓
                              TESTE → PRONTO
```

| Status atual | Destinos aceitos pela API |
|---|---|
| `RECEBIDO` | `DIAGNOSTICO` |
| `DIAGNOSTICO` | `ORCAMENTO` |
| `ORCAMENTO` | `AGUARDANDO_APROVACAO` |
| `AGUARDANDO_APROVACAO` | `ORCAMENTO` |
| `EM_MANUTENCAO` | `AGUARDANDO_PECA`, `TESTE` |
| `AGUARDANDO_PECA` | `EM_MANUTENCAO` |
| `TESTE` | `PRONTO` |
| `PRONTO` | **nenhum** — é terminal |

**Duas proibições que valem destacar**, porque parecem transições válidas no desenho:

- **`AGUARDANDO_APROVACAO` → `EM_MANUTENCAO` é recusado por este endpoint.** Entrar em manutenção é
  consequência da **aprovação do cliente** em `POST /api/v1/publico/{token}/decisao`. Não ofereça
  esse botão na interface interna.
- **`PRONTO` não volta.** Entrar em `PRONTO` carimba `concluidaEm` e encerra a OS para escrita.

A interface deve oferecer **apenas os destinos da linha correspondente ao status atual**. Espelhar a
tabela na tela é conveniência; a decisão continua sendo do backend, que recusa o resto.

**Códigos e erros**

| Status | `code` | Quando |
|---|---|---|
| 200 | — | Transição aplicada. |
| 400 | `VALIDATION_ERROR` | `status` ausente. |
| 400 | `INVALID_REQUEST` | `status` fora do enum, `{id}` que não é UUID. |
| 401 | `UNAUTHORIZED` | Sem sessão, token expirado, conta desativada. |
| 403 | `FORBIDDEN` | Não se aplica aqui: os três papéis podem. Continua possível se a conta perder o papel entre o login e a chamada. |
| 404 | `NOT_FOUND` | OS inexistente **ou de outra oficina**. |
| 409 | `CONFLICT` | Transição não permitida; `revisao` desatualizada; pedir `AGUARDANDO_APROVACAO` sem orçamento criado, ou com a versão atual já decidida; pedir `EM_MANUTENCAO` sem a aprovação do cliente. |

Os cinco motivos de 409 compartilham o `code` `CONFLICT` — **`detail` é o que os distingue**, e é
texto para pessoas. Se a tela precisar reagir de forma diferente a cada um, confronte o status atual
da OS recarregada em vez de interpretar a frase.

**Conflito de revisão.** O 409 significa que **nada foi alterado**. Recarregue a OS (chamada 1 do
detalhe), mostre o status atual e deixe a pessoa decidir de novo. Reenviar automaticamente com a
revisão nova apaga, sem aviso, a decisão de quem chegou primeiro.

```http
POST /api/v1/ordens-servico/1e41609e-4185-4d88-bf0d-22695120234a/status
Authorization: Bearer <accessToken>
Content-Type: application/json

{ "status": "DIAGNOSTICO", "revisao": 0 }
```

```json
{
  "id": "1e41609e-4185-4d88-bf0d-22695120234a",
  "numero": 1,
  "veiculoId": "e6ae57d7-8c4d-4314-a185-fb208b057f36",
  "clienteId": "43688b50-fa08-48c6-8821-ef8690460adc",
  "mecanicoId": null,
  "status": "DIAGNOSTICO",
  "kmEntrada": 48250,
  "relato": "Barulho na suspensão dianteira ao passar em lombada.",
  "criadoEm": "2026-09-15T23:50:31.341467Z",
  "previsaoEntrega": "2026-09-19T18:00:00Z",
  "concluidaEm": null,
  "revisao": 1
}
```

Salto proibido — `{ "status": "PRONTO", "revisao": 0 }` a partir de `RECEBIDO`:

```json
{
  "type": "https://garagem.com.br/erros/conflict",
  "title": "Conflict",
  "status": 409,
  "detail": "Transição de status não permitida.",
  "instance": "/api/v1/ordens-servico/1e41609e-4185-4d88-bf0d-22695120234a/status",
  "code": "CONFLICT",
  "timestamp": "2026-09-15T23:50:43.752Z",
  "requestId": "bcc774f7-da0d-4e21-bfd8-b1ed534e7428"
}
```

Revisão vencida — transição válida, revisão antiga:

```json
{
  "type": "https://garagem.com.br/erros/conflict",
  "title": "Conflict",
  "status": 409,
  "detail": "A OS mudou. Atualize a página.",
  "instance": "/api/v1/ordens-servico/1e41609e-4185-4d88-bf0d-22695120234a/status",
  "code": "CONFLICT",
  "timestamp": "2026-09-15T23:50:43.940Z",
  "requestId": "13c4508c-6441-40f0-abbe-727c25167005"
}
```

**Atribuir responsável** é o contrato vizinho: `PUT /api/v1/ordens-servico/{id}/responsavel`, corpo
`{ mecanicoId, revisao }`, restrito a **OWNER e ATENDENTE**. O responsável precisa ser um usuário
**ativo, com papel `MECANICO`, da mesma oficina**: um `ATENDENTE` ou um mecânico inativo respondem
400 `INVALID_REQUEST`; um mecânico de outra oficina responde 404.

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

## O que mudou na Fase 3

A Fase 3 é **primeira integração**: publicar os contratos e corrigir o que impedia ou atrapalhava o
consumo real. **Nenhum campo foi removido, renomeado ou teve o tipo alterado**, e nenhum status HTTP
mudou. Quem já integrava contra a Fase 2 não precisa alterar nada.

| Mudança | Impacto no frontend |
|---|---|
| Seção [Contratos da primeira integração](#contratos-da-primeira-integração) | Documentação nova, com exemplos de requisição e resposta para os seis contratos. |
| `busca` de `/veiculos` deixou de normalizar marca e modelo | **Correção.** Antes o campo único de pesquisa apagava espaço e hífen de tudo, então `Argo Drive` e `CR-V` não encontravam nada. A placa continua encontrável com ou sem separador. |
| `busca` de `/ordens-servico` passou a normalizar a placa | **Correção.** Antes só o filtro dedicado `placa` normalizava, então a mesma placa digitada com hífen achava por um caminho e não pelo outro. Nome do cliente e número seguem casando com o texto original. |
| Mensagem de validação fixada em português | **Correção.** `detail` e `errors[].message` saíam em inglês no contêiner e para navegador com `Accept-Language: en-US`. Se a tela tinha alguma comparação por texto — o que nunca foi o contrato —, ela passa a receber sempre pt-BR. |
| OpenAPI: um schema por DTO | **Correção.** `ClienteEntrada`, `ClienteSaida`, `VeiculoEntrada`, `VeiculoSaida`, `UsuarioEntrada`, `UsuarioSaida` e `FotoSaida` deixaram de colidir em dois schemas chamados `Entrada` e `Saida`; as páginas viraram `PaginaClienteSaida`, `PaginaVeiculoSaida`, `PaginaUsuarioSaida` e `PaginaOsSaida`. Só afeta quem **gera cliente a partir do OpenAPI** — o corpo na rede é o mesmo de sempre. |
| OpenAPI: `/auth/**` não declara mais 404 nem 409 | Sessão não endereça recurso; os dois status eram impossíveis ali e contradiziam este documento. |
| Log `sessao_emitida` voltou a ser gravado | Sem efeito no contrato. O evento colidia com o contexto da requisição e era descartado pelo escritor de log estruturado. |
