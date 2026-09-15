# Contratos REST — Fase 1

Fonte de verdade: Controllers, DTOs, serviços, `SecurityConfig`, `TenantRequestFilter`, `Pagina` e migrations V1/V2 deste checkout. Inventário revisado em 15/09/2026. Exemplos inteiramente fictícios; os marcadores de tokens não são credenciais utilizáveis.

## Convenções

- Base dos 32 endpoints de negócio: `/api/v1`. JSON em requests e responses, exceto upload multipart, download binário e respostas 204.
- **JWT**: `Authorization: Bearer <accessToken>`. **Todos** = OWNER, ATENDENTE e MECANICO. **Escritório** = OWNER e ATENDENTE. A oficina vem do JWT validado, confrontado com usuário ativo, papel atual e oficina ativa. Não há header/query/body para escolher outro tenant.
- **Público**: token de capacidade de 43 caracteres no path, escopado a uma OS, válido por sete dias e revogável. Não usa JWT. Possuir o link permite consultar o resumo e decidir; deve ser compartilhado apenas com o cliente. A URL do frontend é `/acompanhar#<token>`. Fotos não são expostas por esse resumo.
- **Sem sessão**: login, refresh e logout. O `oficinaId` de refresh/logout é conferido junto ao hash do refresh; conhecer um UUID não concede acesso.
- `UUID` e datas ISO 8601 UTC nos DTOs. Campos opcionais podem retornar `null`. Revisão começa em zero. GET não tem corpo; parâmetros aparecem na tabela de paginação.
- As respostas dos Controllers usam 200 por padrão; POST de criação usa 201 conforme anotação, sem promessa de header Location. Logout e revogação retornam 204 sem corpo.
- Todos os endpoints protegidos podem retornar 401 (sem sessão/expirada/inativa) e 403 (papel insuficiente). IDs inexistentes ou de outra oficina retornam 404. Erros imprevistos retornam 500 genérico. As tabelas destacam erros específicos adicionais; tipos de corpo estão definidos abaixo.

## Inventário completo

`O` nas linhas de sub-recursos significa `/ordens-servico/{id}`; para fotos o controller chama esse parâmetro `osId`. As abreviações só encurtam esta tabela, não são caminhos alternativos.

| Módulo | Método | Path relativo à base | Autenticação / papel | Request | Response | Erros específicos / observações |
|---|---|---|---|---|---|---|
| Auth | POST | `/auth/login` | Sem sessão | Login | 200 Sessao | 400 validação; 401 credenciais/oficina inválidas |
| Auth | POST | `/auth/refresh` | Refresh válido | Refresh | 200 Sessao | 400; 401 expirado, revogado, oficina diferente ou reutilizado; rotação de uso único |
| Auth | POST | `/auth/logout` | Sem sessão; hash + oficina | Refresh | 204 | 400; revogação idempotente, não invalida imediatamente JWT já emitido |
| Equipe | GET | `/usuarios` | JWT / Todos | pagina, tamanho | 200 Pagina<UsuarioSaida> | Sem busca; retorna somente dados públicos da equipe |
| Equipe | POST | `/usuarios` | JWT / OWNER | UsuarioEntrada | 201 UsuarioSaida | 400; 409 e-mail duplicado na oficina; senha nunca retornada |
| Clientes | GET | `/clientes` | JWT / Todos | busca, pagina, tamanho | 200 Pagina<ClienteSaida> | Busca por nome |
| Clientes | GET | `/clientes/{id}` | JWT / Todos | UUID no path | 200 ClienteSaida | 400 UUID inválido; 404 |
| Clientes | POST | `/clientes` | JWT / Escritório | ClienteEntrada | 201 ClienteSaida | 400; revisão de criação é zero |
| Clientes | PUT | `/clientes/{id}` | JWT / Escritório | ClienteEntrada | 200 ClienteSaida | 400; 404; 409 revisão desatualizada/concorrência |
| Veículos | GET | `/veiculos` | JWT / Todos | busca, pagina, tamanho | 200 Pagina<VeiculoSaida> | Busca por placa normalizada |
| Veículos | GET | `/veiculos/{id}` | JWT / Todos | UUID no path | 200 VeiculoSaida | 400; 404 |
| Veículos | POST | `/veiculos` | JWT / Escritório | VeiculoEntrada | 201 VeiculoSaida | 400; 404 cliente; 409 placa duplicada na oficina |
| Veículos | PUT | `/veiculos/{id}` | JWT / Escritório | VeiculoEntrada | 200 VeiculoSaida | 400 km diminuído; 404 veículo/cliente; 409 revisão/placa |
| OS | GET | `/ordens-servico` | JWT / Todos | busca, pagina, tamanho | 200 Pagina<OsSaida> | Busca por placa, nome do cliente ou número |
| OS | GET | `O` | JWT / Todos | UUID no path | 200 OsSaida | 400; 404 |
| OS | POST | `/ordens-servico` | JWT / Escritório | NovaOs | 201 OsSaida | 400 km inferior ao veículo ou responsável não mecânico ativo; 404 referências |
| Responsável | PUT | `O/responsavel` | JWT / Escritório | ResponsavelEntrada | 200 OsSaida | 400; 404; 409 revisão ou OS concluída; não aceita nulo para remover responsável |
| Status | POST | `O/status` | JWT / Todos | StatusEntrada | 200 OsSaida | 400; 404; 409 revisão, salto inválido, orçamento ausente/já decidido ou tentativa de iniciar manutenção sem aprovação |
| Checklist | GET | `O/checklist` | JWT / Todos | — | 200 ChecklistSaida | 404 OS ou checklist ainda não registrado |
| Checklist | POST | `O/checklist` | JWT / Todos | ChecklistEntrada | 201 ChecklistSaida | 400; 404; 409 já registrado ou OS concluída |
| Diagnóstico | GET | `O/diagnosticos` | JWT / Todos | — | 200 DiagnosticoSaida[] | 404 OS; array vazio se nenhum item |
| Diagnóstico | POST | `O/diagnosticos` | JWT / OWNER, MECANICO | DiagnosticoEntrada | 201 DiagnosticoSaida | 400 classificação/conteúdo; 404; 409 OS concluída |
| Orçamento | GET | `O/orcamento/versoes` | JWT / Todos | — | 200 VersaoSaida[] | 404 OS; array vazio se orçamento ausente |
| Orçamento | POST | `O/orcamento/versoes` | JWT / Escritório | VersaoEntrada com itens | 201 VersaoSaida | 400; 404; 409 fora de ORCAMENTO/AGUARDANDO_APROVACAO. Cria orçamento implicitamente, sela versão e itens na mesma transação |
| Timeline | GET | `O/timeline` | JWT / Todos | — | 200 EventoSaida[] | 404 OS; histórico imutável |
| Links | POST | `O/links` | JWT / Escritório | Sem corpo obrigatório | 201 LinkSaida | 404; token exibido uma única vez. Pode emitir vários links |
| Links | DELETE | `O/links/{linkId}` | JWT / Escritório | UUIDs no path | 204 | 404 OS/link/vínculo; revoga apenas o link indicado |
| Fotos | GET | `/ordens-servico/{osId}/fotos` | JWT / Todos | — | 200 FotoSaida[] | 404 OS; nenhum endereço de storage na resposta |
| Fotos | POST | `/ordens-servico/{osId}/fotos` | JWT / Todos | Multipart descrito abaixo | 201 FotoSaida | 400; 404 vínculo/OS; 409 OS concluída; 413 limite multipart; 503 storage indisponível |
| Fotos | GET | `/ordens-servico/{osId}/fotos/{fotoId}/conteudo` | JWT / Todos | UUIDs no path | 200 bytes PNG/JPEG | 404 OS/foto/vínculo; 503 storage. `Cache-Control: no-store` |
| Público | GET | `/publico/{token}` | Token público válido | — | 200 PublicoSaida | 404 token malformado, ausente, expirado ou revogado |
| Público | POST | `/publico/{token}/decisao` | Token público válido | DecisaoEntrada | 200 PublicoSaida | 400; 404; 409 versão substituída, decisão oposta já registrada ou etapa inválida |

Não existem endpoints `/auth/me`, `/sessao`, GET de usuário individual, orçamento separado, inserção avulsa de item, edição/exclusão de checklist/diagnóstico/foto, decisão interna de orçamento ou listagem de links. Dados do usuário autenticado vêm de **Sessao** retornada no login/refresh. O nome da oficina não está nesse DTO; o frontend mostra o slug informado.

### Endpoints de infraestrutura

Sem autenticação: `GET /actuator/health` → 200 `{"status":"UP"}` quando saudável (pode retornar 503 se DOWN); `GET /v3/api-docs` → OpenAPI JSON; `GET /v3/api-docs/swagger-config` → configuração da UI; `GET /swagger-ui.html` → 302 para `/swagger-ui/index.html`, que serve HTML e assets do springdoc. `GET /v3/api-docs.yaml` não está liberado no SecurityConfig e retornou **401 sem JWT** nesta validação. Não são módulos de negócio; nenhum outro actuator é exposto pela configuração atual. O JSON OpenAPI em execução confirmou as 32 operações de negócio acima.

## Filtros, paginação e ordenação

| Listagem | Parâmetros reais | Busca | Ordem |
|---|---|---|---|
| Clientes | busca=`""`, pagina=0, tamanho=20 | Contém nome, ignorando caixa. Serviço não aplica trim à busca | criadoEm DESC, id ASC |
| Veículos | busca=`""`, pagina=0, tamanho=20 | Contém placa, remove espaços/hífens e converte para maiúsculas | criadoEm DESC, id ASC |
| OS | busca=`""`, pagina=0, tamanho=20 | OR entre placa, nome do cliente e número convertido para texto; trim, sem distinguir caixa; `%`, `_` e `!` escapados. Não remove hífen da placa nesta busca | criadoEm DESC, id ASC |
| Equipe | pagina=0, tamanho=20 | Sem busca | criadoEm DESC, id ASC |
| Checklist | Sem paginação | Documento único | itens: criadoEm ASC, id ASC |
| Diagnósticos, fotos, timeline | Sem paginação | Apenas OS do path | criadoEm ASC, id ASC |
| Versões | Sem paginação | Apenas OS do path | numero ASC; itens por criadoEm ASC, id ASC |

`pagina` é zero-based; negativos viram 0. `tamanho` é limitado a 1–100 (zero/negativo vira 1; maior que 100 vira 100). Tipos inválidos retornam 400. Página fora do total retorna `itens: []`. Não há `sort`, filtro de status, data, responsável ou clienteId. Parâmetros desconhecidos não implementam filtros.

```json
{"itens":[],"pagina":0,"tamanho":20,"total":0}
```

O frontend integrado percorre as páginas de 100 para carregar a oficina, busca nas coleções recebidas e apresenta blocos de dez registros; o detalhe e as fotos são carregados separadamente. Busca global também consulta telefone/e-mail/marca/modelo **localmente**, sem atribuir esses filtros à API. GAP de escala: carga integral e uma consulta de versões por OS; paginação remota das telas/agregações do dashboard é evolução futura, sem alteração dos contratos nesta fase. Atualizar dados recarrega o estado do servidor. Não há atualização em tempo real.

## Corpos e exemplos fictícios

Os IDs abaixo são UUIDs de exemplo. `...` nunca representa um campo JSON. Nos exemplos de criação, os campos `id`, `criadoEm`, `numero`, `total`, `subtotal` e decisões vêm exclusivamente do backend quando aplicável.

### Login, sessão, refresh e logout

**Login**:

```json
{"oficina":"oficina-exemplo","email":"owner@example.test","senha":"SenhaFicticia123!"}
```

**Sessao** (login/refresh):

```json
{"accessToken":"ACCESS_TOKEN_FICTICIO","refreshToken":"REFRESH_TOKEN_FICTICIO","expiresIn":900,"oficinaId":"00000000-0000-4000-8000-000000000001","usuarioId":"00000000-0000-4000-8000-000000000002","nome":"Pessoa Exemplo","papel":"OWNER"}
```

**Refresh**, também usado em logout:

```json
{"oficinaId":"00000000-0000-4000-8000-000000000001","refreshToken":"REFRESH_TOKEN_FICTICIO"}
```

Login: oficina até 80, e-mail válido até 254, senha não vazia até 72 caracteres. Cadastro de usuário exige senha de 12–72 caracteres e até 72 bytes UTF-8. Access token HS256 dura 900 segundos; refresh opaco dura 604800 segundos, persistido como SHA-256 e rotacionado sob lock. Logout revoga refresh; JWT emitido pode continuar válido até expirar. O frontend mantém tokens somente em memória; recarregar exige login. Não guarda senhas nem tokens em localStorage/sessionStorage.

### Equipe

**UsuarioEntrada**:

```json
{"nome":"Pessoa Mecânica","email":"mecanico@example.test","senha":"SenhaFicticia123!","papel":"MECANICO"}
```

**UsuarioSaida**:

```json
{"id":"00000000-0000-4000-8000-000000000003","nome":"Pessoa Mecânica","email":"mecanico@example.test","papel":"MECANICO","ativo":true}
```

Nome obrigatório até 160; e-mail normalizado, obrigatório e único por oficina. Papéis: OWNER, ATENDENTE, MECANICO. Não há GERENTE.

### Clientes

**ClienteEntrada** (POST/PUT; em PUT enviar a revisão lida):

```json
{"nome":"Cliente Exemplo","telefone":"11000000000","email":"cliente@example.test","revisao":0}
```

**ClienteSaida**:

```json
{"id":"00000000-0000-4000-8000-000000000004","nome":"Cliente Exemplo","telefone":"11000000000","email":"cliente@example.test","revisao":0}
```

Nome obrigatório até 160; telefone obrigatório até 30; e-mail opcional válido até 254; revisão não negativa. PUT altera a revisão quando os dados mudam.

### Veículos

**VeiculoEntrada**:

```json
{"clienteId":"00000000-0000-4000-8000-000000000004","placa":"ABC1D23","marca":"Fiat","modelo":"Uno","ano":2020,"km":1000,"cor":"Prata","revisao":0}
```

**VeiculoSaida**:

```json
{"id":"00000000-0000-4000-8000-000000000005","clienteId":"00000000-0000-4000-8000-000000000004","placa":"ABC1D23","marca":"Fiat","modelo":"Uno","ano":2020,"km":1000,"cor":"Prata","revisao":0}
```

Placa: `[A-Za-z]{3}[- ]?[0-9][A-Za-z0-9][0-9]{2}`, normalizada ao salvar. Marca até 80, modelo até 100, cor até 60, todos obrigatórios. Ano 1886–2200. KM/revisão não negativos; atualização não permite diminuir KM. Mesma placa é permitida em oficinas diferentes.

### OS e entrada do veículo

**NovaOs**:

```json
{"veiculoId":"00000000-0000-4000-8000-000000000005","mecanicoId":"00000000-0000-4000-8000-000000000003","kmEntrada":1001,"relato":"Cliente relata ruído nos freios.","previsaoEntrega":"2026-09-16T20:00:00Z"}
```

**OsSaida**:

```json
{"id":"00000000-0000-4000-8000-000000000006","numero":1,"veiculoId":"00000000-0000-4000-8000-000000000005","clienteId":"00000000-0000-4000-8000-000000000004","mecanicoId":"00000000-0000-4000-8000-000000000003","status":"RECEBIDO","kmEntrada":1001,"relato":"Cliente relata ruído nos freios.","criadoEm":"2026-09-15T12:00:00Z","previsaoEntrega":"2026-09-16T20:00:00Z","concluidaEm":null,"revisao":0}
```

Veículo obrigatório; mecânico/previsão opcionais (`null`). Cliente é obtido do veículo, número alocado por oficina. Relato obrigatório até 4000; KM não pode ser inferior ao veículo, cujo KM também é atualizado. Dados de entrada são registrados na abertura e no checklist; não existe PUT geral da OS. OsSaida retorna IDs, sem nomes/placa; relações são resolvidas na mesma oficina.

**ResponsavelEntrada**:

```json
{"mecanicoId":"00000000-0000-4000-8000-000000000003","revisao":0}
```

**StatusEntrada**:

```json
{"status":"DIAGNOSTICO","revisao":0}
```

Ambas retornam OsSaida atualizado. Sempre usar a revisão mais recente; não incrementá-la no frontend.

### Checklist

**ChecklistEntrada**:

```json
{"observacoes":"Chave principal recebida.","itens":[{"descricao":"Pneus","condicao":"Bom estado","observacao":"Sem avarias aparentes."}]}
```

**ChecklistSaida**:

```json
{"id":"00000000-0000-4000-8000-000000000007","observacoes":"Chave principal recebida.","itens":[{"id":"00000000-0000-4000-8000-000000000008","descricao":"Pneus","condicao":"Bom estado","observacao":"Sem avarias aparentes."}]}
```

1–100 itens, descrição obrigatória até 200, condição livre obrigatória até 100, observação do item até 1000, observações gerais até 4000. Registro único por OS, sem edição.

### Diagnóstico

**DiagnosticoEntrada**:

```json
{"descricao":"Pastilhas gastas.","classificacao":"VERMELHO"}
```

**DiagnosticoSaida**:

```json
{"id":"00000000-0000-4000-8000-000000000009","descricao":"Pastilhas gastas.","classificacao":"VERMELHO","criadoEm":"2026-09-15T13:00:00Z"}
```

Descrição obrigatória até 4000; classificações reais VERDE, AMARELO, VERMELHO. A interface traduz para OK, Acompanhar, Trocar. Acréscimo permitido enquanto OS não estiver PRONTO.

### Orçamento, itens e versão

**VersaoEntrada** (uma única requisição cria versão e todos os itens):

```json
{"observacoes":"Peças e mão de obra.","itens":[{"tipo":"PECA","descricao":"Pastilhas","quantidade":1,"valorUnitario":120.50},{"tipo":"SERVICO","descricao":"Substituição","quantidade":1.5,"valorUnitario":100.01}]}
```

**VersaoSaida**:

```json
{"id":"00000000-0000-4000-8000-000000000010","numero":1,"observacoes":"Peças e mão de obra.","total":270.52,"criadoEm":"2026-09-15T14:00:00Z","itens":[{"id":"00000000-0000-4000-8000-000000000011","tipo":"PECA","descricao":"Pastilhas","quantidade":1,"valorUnitario":120.50,"subtotal":120.50},{"id":"00000000-0000-4000-8000-000000000012","tipo":"SERVICO","descricao":"Substituição","quantidade":1.5,"valorUnitario":100.01,"subtotal":150.02}],"decisao":null}
```

Observações até 4000; 1–100 itens. Tipo PECA ou SERVICO; descrição obrigatória até 500. Quantidade ≥ 0.001, até seis dígitos inteiros e três decimais; valor unitário ≥ 0, até oito dígitos inteiros e dois decimais. Subtotal usa BigDecimal e HALF_UP em duas casas **por item**; total é a soma. Versões são imutáveis e numeradas sequencialmente dentro do orçamento. Uma nova versão retorna a OS para ORCAMENTO e exige disponibilização novamente. “Aditivo” nesta fase significa nova versão integral antes da aprovação/manutenção, não um módulo adicional nem alteração da versão aprovada durante execução.

### Link, aprovação/recusa e resumo público

**LinkSaida** (token mostrado somente na emissão):

```json
{"id":"00000000-0000-4000-8000-000000000013","url":"http://localhost:5173/acompanhar#TOKEN_PUBLICO_FICTICIO","token":"TOKEN_PUBLICO_FICTICIO","expiraEm":"2026-09-22T14:00:00Z"}
```

**DecisaoEntrada** — aprovação:

```json
{"versaoId":"00000000-0000-4000-8000-000000000010","aprovado":true}
```

Recusa:

```json
{"versaoId":"00000000-0000-4000-8000-000000000010","aprovado":false}
```

**DecisaoSaida**, dentro da versão consultada após decisão:

```json
{"aprovado":true,"criadoEm":"2026-09-15T15:00:00Z","canal":"LINK_PUBLICO"}
```

**PublicoSaida**, antes de disponibilizar ou depois de recusa:

```json
{"numero":1,"status":"ORCAMENTO","veiculo":"Fiat Uno · ABC1D23","previsaoEntrega":"2026-09-16T20:00:00Z","orcamento":null}
```

Em AGUARDANDO_APROVACAO/EM_MANUTENCAO/AGUARDANDO_PECA/TESTE/PRONTO, `orcamento` contém o objeto completo VersaoSaida mostrado acima, com a decisão quando existente. Aprovar muda para EM_MANUTENCAO; recusar muda para ORCAMENTO. IP da conexão, data, canal e link ficam persistidos; o DTO público não expõe IP, clientes, usuários, diagnóstico, checklist, fotos ou timeline. `Cache-Control: no-store`.

### Fotos privadas

Multipart: parte obrigatória **arquivo** (PNG/JPEG), parâmetro obrigatório **finalidade** (`ENTRADA`, `DIAGNOSTICO`, `SERVICO`), **descricao** opcional até 500, **checklistItemId** ou **diagnosticoItemId** opcionais e mutuamente exclusivos. Itens precisam pertencer à mesma OS e oficina.

**FotoSaida**:

```json
{"id":"00000000-0000-4000-8000-000000000014","finalidade":"DIAGNOSTICO","descricao":"Pastilhas antes da substituição.","contentType":"image/png","tamanho":1024,"checklistItemId":null,"diagnosticoItemId":"00000000-0000-4000-8000-000000000009"}
```

Até 10 MB e 20 megapixels; imagem é decodificada e regravada para validar conteúdo e remover metadados. Multipart total tem limite de 11 MB. Não há URL pública/pré-assinada: a interface busca bytes com Bearer e usa Blob URL temporária, revogada ao desmontar. Bucket MinIO privado; acesso anônimo direto deve retornar 403, mesmo conhecendo a chave. Conteúdo não é JSON. A chave interna é oficina/OS/foto e não é retornada pela API.

### Timeline

**EventoSaida[]**:

```json
[{"id":"00000000-0000-4000-8000-000000000015","tipo":"OS_ABERTA","descricao":"OS recebida na oficina.","origem":"USUARIO","autorId":"00000000-0000-4000-8000-000000000002","criadoEm":"2026-09-15T12:00:00Z"},{"id":"00000000-0000-4000-8000-000000000016","tipo":"ORCAMENTO_APROVADO","descricao":"Cliente aprovou o orçamento v1 pelo link.","origem":"LINK_PUBLICO","autorId":null,"criadoEm":"2026-09-15T15:00:00Z"}]
```

Tipos produzidos: OS_ABERTA, RESPONSAVEL_ALTERADO, STATUS_ALTERADO, CHECKLIST_REGISTRADO, DIAGNOSTICO_REGISTRADO, ORCAMENTO_VERSIONADO, LINK_CRIADO, LINK_REVOGADO, FOTO_ADICIONADA, ORCAMENTO_APROVADO, ORCAMENTO_RECUSADO. Não há endpoint de escrita direta da timeline.

## Conflitos, imutabilidade e HTTP

| Situação | Comportamento real |
|---|---|
| Duas alterações com a mesma revisão de OS | Lock pessimista por OS; primeira alteração vence, segunda recebe 409. Clientes/veículos usam `@Version` e comparação da revisão |
| Duas aprovações iguais simultâneas | Ambas 200, uma única decisão persistida; repetição igual idempotente |
| Aprovação e recusa da mesma versão | Primeira decisão vence; decisão oposta recebe 409 |
| Decisão sobre versão substituída | 409; consultar a versão atual antes de confirmar novamente |
| Salto inválido, aprovação via status interno | 409; manutenção só começa pela decisão pública |
| Checklist repetido; escrita em OS PRONTO | 409; conclusão não tem reabertura |
| Duas novas versões | Escritas serializadas pelo lock da OS; recebem números distintos. VersaoEntrada não tem campo revisao |
| Atualizar/apagar versão, item, decisão ou evento no banco | Trigger rejeita com SQLSTATE 23000; nenhuma rota REST oferece essas alterações. Não atribuir HTTP a uma operação SQL direta |
| Inserir item em versão selada; total sem itens/inconsistente | Trigger V2 rejeita; versão e itens precisam estar na mesma transação |
| Recurso de outra oficina | 404, inclusive escrita e download, após validar autenticação/permissão. Papel insuficiente pode retornar 403 antes da consulta |
| FK cruzada entre oficinas | Banco rejeita por FK composta; DataIntegrityViolationException na API é mapeada a 409 genérico |

Fluxo: RECEBIDO → DIAGNOSTICO → ORCAMENTO → AGUARDANDO_APROVACAO → aprovação → EM_MANUTENCAO → TESTE → PRONTO. EM_MANUTENCAO ↔ AGUARDANDO_PECA; AGUARDANDO_APROVACAO → ORCAMENTO para revisão/recusa. Não há cancelamento, entrega separada, reabertura ou aprovação parcial.

## Erros sem vazamento

Os handlers MVC retornam `application/problem+json`, por exemplo:

```json
{"type":"about:blank","title":"Conflict","status":409,"detail":"A OS mudou. Atualize a página.","instance":"/api/v1/ordens-servico/00000000-0000-4000-8000-000000000006/status"}
```

```json
{"type":"about:blank","title":"Bad Request","status":400,"detail":"Dados inválidos. Confira os campos enviados.","instance":"/api/v1/clientes"}
```

Os filtros de segurança usam forma menor, sem prometer `type/title/instance`:

```json
{"status":401,"detail":"Autenticação necessária ou acesso não permitido."}
```

```json
{"status":404,"detail":"Acesso inválido ou expirado."}
```

Demais códigos: 403 papel insuficiente; 404 registro ausente; 405 método inexistente; 413 multipart excessivo; 503 storage temporariamente indisponível. 500 usa `Não foi possível concluir a operação.`. Validação 400 lista nomes de campos e restrições, sem valores submetidos. SQL, stacktrace, senha e tokens não são incluídos. Não presumir um único shape de erro; usar `status` e `detail` quando presentes.

Evidências de execução e limites de aceite: [finalização](fase1-finalizacao.md) e [aceite](aceite-fase-1.md).
