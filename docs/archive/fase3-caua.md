# Fase 3 — parte do Cauã

Escrito para o time do projeto e para quem for revisar a entrega.

Escopo: publicar os contratos da **primeira integração** do frontend com a API e corrigir os
problemas reais encontrados durante essa integração. Não inclui redesenho de interface nem os itens
de produto atribuídos ao outro desenvolvedor.

Branch: `feature/fase3-api-integration`, criada a partir da `main` atualizada (`95d7e7b`). Sem
commit, push ou merge.

Última execução completa de `mvn verify` em 15/09/2026, com JDK 25: **BUILD SUCCESS**, 12 testes
unitários e 54 de integração, zero falhas.

Evidências citadas como `Fase3IT#metodo` estão em
`backend/src/test/java/br/com/garagem/Fase3IT.java`.

---

## O que foi publicado

Os seis contratos da primeira integração estão em
[api-v1.md § Contratos da primeira integração](../api/api-v1.md#contratos-da-primeira-integração), cada um
com método, endpoint, finalidade, autenticação, papéis, headers, parâmetros, paginação, filtros,
ordenação, DTOs de entrada e saída com campos obrigatórios e opcionais, códigos HTTP, códigos
estáveis de erro, conflitos possíveis, comportamento multi-tenant e exemplos de requisição e
resposta. Os exemplos foram capturados da aplicação em execução e depois anonimizados.

| Contrato | Estado antes | O que foi feito |
|---|---|---|
| Login | Inventariado: tabela de uma linha e lista de campos | Complementado. Campos um a um, os quatro motivos de 401 e por que são indistinguíveis, rotação do refresh, exemplos de sessão e de recusa. |
| Clientes | Inventariado: linha por operação e DTO em uma linha | Complementado. Regras de cada campo, semântica da revisão, o que fazer no 409, exemplos de POST, listagem e conflito. |
| Veículos | Inventariado | Complementado. Normalização da placa, vínculo com cliente, `DUPLICATE` por oficina, KM que não retrocede, exemplos. |
| Listagem de OS | Inventariado: filtros listados em uma célula | Complementado. Tabela parâmetro a parâmetro, envelope de paginação, `OsSaida` campo a campo, seis exemplos de consulta. |
| Detalhe de OS | **Não existia como contrato** | Escrito. As nove chamadas que compõem a tela, em que ordem, quais podem ser paralelas, por que o 404 do checklist é esperado e qual revisão vale para escrever. |
| Alteração de status | Inventariado: uma linha com os 409 possíveis | Complementado. Tabela de destinos aceitos por status atual, as duas proibições que parecem válidas, os cinco motivos de 409 e como tratar cada um. |

A arquitetura do detalhe foi **documentada como está**, não alterada: o backend usa endpoints
separados e a resposta da OS continua devolvendo referências por id. Nada foi agregado numa resposta
única por conveniência.

---

## Divergências encontradas

| Onde | Divergência | Decisão |
|---|---|---|
| OpenAPI × código | `ClienteDtos.Entrada`, `VeiculoDtos.Entrada` e `UsuarioController.Entrada` colidiam num único schema `Entrada`; o mesmo com quatro `Saida`. O Swagger descrevia o corpo do cliente **com os campos do veículo**. | Corrigido o OpenAPI: `@Schema(name = …)` dá um nome por DTO, com os mesmos nomes que `api-v1.md` já usava. O contrato na rede não mudou. |
| OpenAPI × `api-v1.md` | `POST /auth/login` declarava 404 e 409, impossíveis em sessão. | Corrigido o OpenAPI. |
| Implementação × `api-v1.md` | `busca` de `/veiculos` normalizava marca e modelo como se fossem placa. | Corrigida a implementação: a documentação estava certa. |
| Implementação × `api-v1.md` | `busca` de `/ordens-servico` não normalizava a placa, embora o filtro `placa` normalizasse. | Corrigida a implementação. |
| `api-errors.md` × execução | O documento publicava `detail` em português; a aplicação no contêiner devolvia inglês. | Corrigida a implementação e o documento: o texto era o contrato certo. |
| `api-errors.md` × execução | O exemplo de erro omitia `instance`, que a API sempre envia. | Corrigido o documento. |
| `fase2-caua.md` × frontend | A tabela de pendências citava um método `api.getDashboard()` que **não existe**. | Corrigido o documento: o cliente é a função genérica `api<T>(path)`. |

---

## Bugs encontrados e corrigidos

Cada um foi reproduzido antes de corrigir, teve a causa raiz tratada e ganhou teste de regressão.

### 1. Pesquisa de veículo não encontrava marca nem modelo com espaço ou hífen

**Reprodução.** Cadastrar `Land Rover Evoque` e `Honda CR-V`; pesquisar `Land Rover` ou `CR-V` no
campo único de busca. Zero resultados.

**Causa raiz.** `VeiculoService.listar` passava o texto por `normalizarBusca`, que remove hífen e
espaço, antes de compará-lo com os três campos. A normalização existe para a placa, que é gravada
sem separadores — mas marca e modelo são gravados como digitados, então o texto normalizado nunca
casava com eles.

**Correção.** A pesquisa passou a viajar em duas formas: `buscaPlaca`, normalizada, só contra a
placa; e `busca`, o texto original, contra marca e modelo. Quando a pesquisa só tem separadores a
forma normalizada fica vazia e vale o texto original, para os dois parâmetros nunca chegarem nulos
separadamente e anularem o predicado — o que devolveria a base inteira.

**Arquivos.** `veiculo/repository/VeiculoRepository.java`, `veiculo/application/VeiculoService.java`.
**Teste.** `Fase3IT#buscaDeVeiculoNaoNormalizaMarcaEModelo`.

### 2. Pesquisa da listagem de OS não encontrava a placa digitada com hífen

**Reprodução.** Abrir uma OS para a placa `XYZ9Z88` e pesquisar `xyz-9z88` na listagem. Zero
resultados, embora `?placa=xyz-9z88` encontrasse.

**Causa raiz.** O espelho do bug anterior. `OsService.listar` normalizava o filtro dedicado `placa`,
mas mandava `busca` cru contra `v.placa`, que está gravada sem separadores.

**Correção.** Mesmo desenho: `buscaPlaca` normalizada contra a placa, `busca` original contra o nome
do cliente e o número da OS.

**Arquivos.** `ordemservico/application/OsService.java`,
`ordemservico/repository/OrdemServicoRepositoryCustom.java`,
`ordemservico/repository/OrdemServicoRepositoryImpl.java`.
**Teste.** `Fase3IT#buscaDeOsNormalizaAPlaca`.

A construção dinâmica da consulta foi preservada: os filtros continuam entrando como parâmetros
nomeados só quando informados. **O padrão `(:param is null or …)` não foi reintroduzido** para
filtro temporal, e `Fase3IT#periodoDeAberturaEmQualquerCombinacao` cobre `de` sozinho, `ate`
sozinho, os dois juntos e nenhum dos dois, que era o caso do `SQLState 42P18`.

### 3. Mensagem de validação em inglês para o usuário final

**Reprodução.** Com a stack no Docker, `POST /api/v1/clientes` com `nome` em branco devolvia
`"detail": "email: must be a well-formed email address; nome: must not be blank"`. Em uma máquina
com locale pt-BR o mesmo request devolvia português — o que explica o problema ter passado pela
Fase 2.

**Causa raiz.** O texto vem do Bean Validation, que resolve a mensagem pelo locale da requisição.
Sem locale configurado, o contêiner cai no default da JVM, e um navegador que envie
`Accept-Language: en-US` força inglês mesmo em servidor pt-BR. Como a tela exibe `detail` e
`errors[].message` diretamente, o usuário final via inglês.

**Correção.** `spring.web.locale: pt_BR` com `locale-resolver: fixed`. O produto é pt-BR e o texto
publicado em `api-errors.md` passou a ser o que a API realmente devolve, em qualquer ambiente.

**Arquivo.** `backend/src/main/resources/application.yml`.
**Teste.** `Fase3IT#validacaoFalaPortuguesIndependenteDoAmbiente`, que envia `Accept-Language:
en-US` de propósito.

### 4. O registro de login era descartado do log

**Reprodução.** Qualquer `mvn verify` imprimia
`Appender [CONSOLE] failed to append. java.lang.IllegalStateException: The name 'oficina_id' has
already been written`, e nenhum evento `sessao_emitida` chegava ao log.

**Causa raiz.** `RequestLoggingFilter` publica `oficina_id` e `usuario_id` no MDC de **toda**
requisição. `AuthService.issue` acrescentava os mesmos dois nomes como key-value do evento. O
escritor de log estruturado do Spring Boot recusa nome repetido e descarta **a linha inteira**, não
só o campo — então a auditoria de login e de refresh sumia silenciosamente.

**Correção.** Os dois identificadores passaram para o MDC. Além de o `sessao_emitida` voltar a ser
gravado, o `request_concluido` da requisição de login deixa de sair como `"anonimo"` e fica
atribuível.

**Arquivo.** `auth/application/AuthService.java`.
**Teste.** `Fase3IT#sessaoEmitidaChegaAoLog`, que afirma a invariante violada: o evento não pode ter
key-value com nome já presente no MDC.

### 5. OpenAPI descrevia o corpo de cliente e de usuário com os campos do veículo

Tratado acima, em Divergências. **Teste.** `Fase3IT#openApiNaoMisturaOsDtosDosContratos`.

---

## Testes adicionados

`Fase3IT`, 15 testes contra PostgreSQL real via Testcontainers. Não repete o que `Fase1IT` e
`Fase2IT` já afirmam — fluxo completo da OS, matriz de permissões operação a operação, inventário do
OpenAPI e filtros nomeados continuam sendo verificados lá.

| Teste | O que afirma |
|---|---|
| `contratoDeLogin` | Os sete campos de `Sessao`, e nada além deles. Nenhum vestígio de senha ou hash. |
| `errosDeLogin` | Os cinco caminhos de 401 devolvem corpo idêntico; validação é 400 com `errors[]`. |
| `sessaoEmitidaChegaAoLog` | Regressão do bug 4. |
| `contratoDeCadastroDevolveRevisaoUtilizavel` | POST nasce com revisão 0; PUT responde com o estado gravado e a revisão seguinte; placa volta normalizada. |
| `buscaDeVeiculoNaoNormalizaMarcaEModelo` | Regressão do bug 1. |
| `buscaDeOsNormalizaAPlaca` | Regressão do bug 2. |
| `periodoDeAberturaEmQualquerCombinacao` | Regressão do `42P18`: `de`, `ate`, ambos e nenhum. |
| `envelopeDaListagemDeOs` | Os cinco campos do envelope, e página além do fim como 200 vazio. |
| `contratoDeDetalheDeOs` | As chamadas do detalhe respondem 200, `OsSaida` tem exatamente doze campos, coleções vazias são `[]` e só o checklist responde 404. |
| `contratoDeAlteracaoDeStatus` | Seis saltos proibidos, status fora do enum, transição válida, revisão vencida e `EM_MANUTENCAO` sem aprovação. |
| `autorizacaoNosContratosDaPrimeiraIntegracao` | Papéis nos seis contratos, mais responsável inativo, de papel errado e de outra oficina. |
| `isolamentoNosContratosDaPrimeiraIntegracao` | Leitura e escrita cruzadas nos seis contratos, vínculo cruzado e filtro com id alheio. |
| `conflitoDeRevisaoNosTresCadastros` | 409 em cliente, veículo e OS, com o valor perdido **não** gravado. |
| `openApiNaoMisturaOsDtosDosContratos` | Regressão do bug 5. |
| `validacaoFalaPortuguesIndependenteDoAmbiente` | Regressão do bug 3. |

**Autorização por papel.** `MECANICO` lê os seis contratos e muda status; recebe 403 ao cadastrar ou
editar cliente e veículo, abrir OS e atribuir responsável. Responsável precisa ser mecânico **ativo**
da própria oficina: inativo e `ATENDENTE` respondem 400, mecânico de outra oficina responde 404.
Nada foi enfraquecido; a matriz de `permissoes.md` continua batendo com o código.

**Isolamento por oficina.** Sete leituras por id e cinco escritas cruzadas respondem 404, nunca 403.
Vínculo cruzado — veículo no cliente alheio, OS no veículo alheio — responde 404. Filtro com
`clienteId` de outra oficina devolve página vazia. Validado nos dois sentidos, somando com
`Fase2IT#isolamentoEntreOficinas` e `#isolamentoNoSentidoInverso`.

**Conflitos de revisão.** Cliente, veículo e OS recusam escrita com revisão vencida com 409
`CONFLICT` e **não gravam** o valor perdido. O comportamento esperado do frontend está documentado
em cada contrato: recarregar, mostrar o estado atual e deixar a pessoa decidir — nunca reenviar
automaticamente com a revisão nova.

---

## Frontend

A camada `frontend/src/api.ts` e `frontend/src/api/` foi auditada endpoint por endpoint contra os
Controllers. **Nenhuma incompatibilidade de contrato foi encontrada, e nenhum arquivo do frontend
foi alterado nesta branch.** Em particular:

- caminhos, nomes de parâmetro (`pagina`, `tamanho`, `ordenacao`, `busca`) e corpos batem;
- `transitions` em `model.ts` espelha a máquina de estados do backend e já omite
  `AGUARDANDO_APROVACAO → EM_MANUTENCAO`, que só a aprovação do cliente produz;
- o campo `ativo` do link público é marcação local da tela, não um campo que a API devolva —
  `OrderDetail.tsx` o define ao criar o link;
- `ApiError` já preserva `code`, `requestId` e `errors[]`.

`frontend/src/OrderDetail.tsx` foi verificado quanto a encoding **antes** de qualquer decisão: o
arquivo está íntegro em UTF-8 na `main`, sem mojibake. **Foi preservado como está.** Nenhuma versão
de stash ou de branch antiga foi usada como fonte.

---

## Pendências do outro desenvolvedor

Registradas, não assumidas:

| Área | Item |
|---|---|
| Frontend | Consumir `GET /api/v1/dashboard` em vez de calcular os indicadores em `dashboard-model.ts`. |
| Frontend | `loadData()` baixa clientes, veículos, usuários e OS por inteiro com `allPages` para montar a tela. Com a paginação e os filtros do backend prontos e agora documentados, isso deveria virar consulta paginada por tela. |
| Frontend | Usar os filtros e a ordenação da listagem de OS; hoje a tela filtra em memória, no cliente. |
| Frontend | Usar `errors[]` para marcar campo a campo nos formulários; hoje exibe só `detail`. |

---

## Riscos técnicos restantes

| Risco | Observação |
|---|---|
| Carga inicial do frontend | Enquanto `loadData()` baixar a base inteira, uma oficina com muitas OS terá primeira carga lenta e várias requisições. É o item mais relevante da lista acima. |
| `CONFLICT` genérico na mudança de status | Cinco situações diferentes compartilham o mesmo `code`. Para a primeira integração basta recarregar a OS; se a tela precisar reagir de forma distinta a cada uma, será preciso um código estável por situação — mudança de contrato, fora do escopo desta fase. |
| Filtros de cliente, veículo e usuário | Continuam no formato estático `(:param is null or …)`. Funcionam, estão cobertos por teste e não têm parâmetro temporal, que era a causa do `42P18`. Padronizá-los com o repositório dinâmico da OS é melhoria, não correção. |
| Deploy de staging | Continua pendente, como na Fase 2. Depende de servidor, domínio e autorização. |

---

## Como verificar

```bash
cd backend && ./mvnw verify            # ou o Maven portátil de .tools, com JDK 25
cd frontend && npm run lint && npm run typecheck && npm test && npm run build
```

Com a stack no ar:

```bash
curl -i http://127.0.0.1:8080/actuator/health     # 200 {"status":"UP"}
curl -s http://127.0.0.1:8080/v3/api-docs         # 23 caminhos, 33 operações, 36 schemas
open http://127.0.0.1:8080/swagger-ui.html
```

A validação em Docker desta fase rodou em projeto isolado (`-p garagem-fase3`), com arquivo de
ambiente fora do repositório. **Os volumes `garagem-saas_postgres-data` e `garagem-saas_fotos-data`
do desenvolvedor não foram tocados.**
