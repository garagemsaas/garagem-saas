# Fase 5 — Dinheiro Esquecido — Cauã

Branch: `feature/fase5-dinheiro-esquecido`. HEAD inicial `fabe67f`, working tree limpo,
sincronizado com a branch remota. Conferidos `git branch --show-current`, `git status`
e os cinco commits anteriores antes das alterações. Nenhuma branch criada/trocada;
nenhum commit, push, merge ou git init. Alterações locais para revisão.

As Fases 1–4 são a base desta entrega, conforme encerramento informado pelo time.
O README ainda trazia a Fase 4 em andamento; foi atualizado sem reescrever os
relatórios históricos de validação. Frontend, autenticação e domínio anterior preservados.

## Modelo e migration

Módulo `br.com.garagem.dinheiroesquecido`, com controllers, records de DTO,
serviços transacionais, entidades JPA tenant-scoped e repositórios. Sem infraestrutura nova.

`V3__dinheiro_esquecido.sql`, sem editar V1/V2, acrescenta:

- `OportunidadeRecuperacao`: tipo/status, OS e versão de origem, cliente/veículo,
  responsável, potencial, elegibilidade, contatos, encerramento, revisão otimista.
- `ContatoOportunidade`: tentativas imutáveis, usuário, instante, canal, resultado,
  observação e próximo contato informado naquela tentativa.
- `ResultadoOportunidade`: recuperação única e imutável, usuário, instante, valor
  efetivo, OS opcional e observação.
- `EventoOportunidade`: fatos imutáveis com usuário, instante, tipo, anterior/novo e observação.
- `AcompanhamentoOrcamento`: referência à versão existente, primeira publicação,
  `reavaliarEm` opcional e revisão. Não modifica o snapshot do orçamento nem sua decisão.
- `ordem_servico.proxima_revisao_em`: programação por data ligada ao serviço concluído.
- View `origem_recuperacao`: regras relacionais compartilhadas pela identificação e reconciliação.

Todas as FKs de negócio incluem `(id, oficina_id)`. Checks cobrem enums, valores,
combinação tipo/origem e status/encerramento. Trigger confere que versão, cliente e
veículo correspondem à OS de origem e impede alteração desse snapshot de referências.
Índices por oficina/status, tipo, elegibilidade, próximo contato, responsável,
histórico por oportunidade e resultados por período. Unicidade por tipo/origem,
inclusive encerradas; índice parcial impede duas ativas do mesmo tipo para a mesma OS.
Resultado único por oportunidade e trigger diferida mantêm RECUPERADA equivalente à
existência de resultado. Triggers impedem edição/exclusão dos históricos, exclusão
de oportunidade e alteração/exclusão da primeira publicação.

Cliente e veículo são referências, não cópias de cadastros. Nome/telefone/placa do
DTO vêm de joins atuais; cliente é o registrado na OS original, inclusive se o veículo
mudar de proprietário posteriormente.

## Regras de identificação

Execução explícita por `POST /api/v1/dinheiro-esquecido/identificar`, sem corpo.
Nenhuma identificação automática em GET. Percorre OS candidatas em lotes de 100 por
UUID e faz uma transação por OS, usando o mesmo lock da criação de versões/decisões.
Pode ser repetido; falha parcial preserva as OS já processadas, e repetir é seguro.
Não há job global, Kafka, envio de mensagem ou acesso a outra oficina.

### Orçamento esquecido

OS em `AGUARDANDO_APROVACAO`, versão de maior número, nenhuma decisão, publicação
comprovada há **pelo menos 168 horas (7 dias corridos)**. Total da versão pendente é
o potencial. Criar versão ou emitir link não equivale a publicar; a transição para
`AGUARDANDO_APROVACAO` registra a primeira publicação, preservada em reenvios.

V3 recupera publicações antigas somente quando a timeline comprova a transição
`ORCAMENTO → AGUARDANDO_APROVACAO` durante a vigência daquela versão. Sem evidência,
não inventa uma data; nova publicação pelo fluxo normal registra o marco.
Não há status/endpoint de cancelamento no domínio atual: somente o estado pendente
é elegível, sem introduzir cancelamento de OS nesta fase.

### Revisão atrasada

Programar `proximaRevisaoEm` em OS `PRONTO`; data não anterior à conclusão.
É uma exceção limitada de pós-serviço à imutabilidade operacional da OS: altera
apenas a programação, com revisão e evento na timeline. Não reabre nem muda status.

Elegível na meia-noite de `proximaRevisaoEm`, em **America/Sao_Paulo**, portanto
`proximaRevisaoEm <= hoje`. Quilometragem não participa. Valor potencial sempre nulo.
Na V1, considera a necessidade atendida se existe outra OS do mesmo veículo/oficina
em PRONTO, **aberta depois da conclusão da OS de origem**. Apenas abrir outra OS não
resolve. O domínio não classifica serviços como revisão: essa aproximação por serviço
posterior concluído é explícita e deverá ser refinada se houver classificação futura.

Antes de gerar oportunidade, a data pode ser alterada/removida; após gerar, o ciclo
fica preservado. Agendamentos comerciais usam próximo contato. Uma nova revisão
deve ser programada na nova OS concluída; encerrar a oportunidade não a recria.

### Reavaliação pendente

Versão recusada há **720 horas (30 dias corridos)**, OS em ORCAMENTO ou
AGUARDANDO_APROVACAO e nenhuma decisão em versão posterior. Uma nova proposta ainda
sem decisão não é negociação concluída. Aprovação posterior encerra a necessidade;
recusa posterior passa a ser a nova origem, evitando acumular recusas antigas.

`reavaliarEm`, quando definido, prevalece: data local em São Paulo, não anterior à
recusa. Null restaura os 30 dias. A configuração tem revisão própria, não a revisão
da OS. Após gerar oportunidade, altera-se próximo contato, preservando a origem.
Potencial é o total da versão recusada, mesmo se uma proposta posterior tiver outro valor.

### Reconciliação e idempotência

Na identificação, uma oportunidade ativa cuja origem deixou de ser elegível é
DESCARTADA com motivo e auditoria, sem atribuir receita automaticamente. Versão
substituída, aprovação e atendimento posterior são exemplos. Contatos e resultados
anteriores são preservados. Encerradas nunca reabrem nem se regeneram para a mesma
origem. Mudanças de origem aparecem na carteira após a próxima identificação;
o endpoint retorna `{criadas, descartadas}`.

## Operação, valores e status

Todos os valores usam BigDecimal/numeric(19,2). Valores negativos e mais de duas
casas são rejeitados; valor recuperado exige entrada explícita (zero permitido).
Potencial não é receita. Exemplo válido: potencial 2000.00, recuperado 1450.00.
Não há média estimada para revisão nem pagamento/financeiro completo.

- ABERTA nasce na identificação.
- Contato SEM_RESPOSTA, CONTATO_REALIZADO, INTERESSADO ou NAO_INTERESSADO leva a
  EM_CONTATO. Não interessado não encerra automaticamente.
- Contato AGENDADO exige próximo contato futuro e leva a AGENDADA.
- Definir status AGENDADA exige próximo contato já registrado.
- Limpar próximo contato de AGENDADA leva a EM_CONTATO.
- Registrar resultado único leva a RECUPERADA; não é permitido chegar lá via status.
- PERDIDA exige tentativa anterior e motivo; DESCARTADA exige motivo.
- Estados terminais bloqueiam novos contatos, resultado, status e atribuição.
  Sem reabertura, exclusão física ou correção destrutiva de resultado na V1.

Contatos registram o instante do servidor; cada POST acrescenta histórico.
Canais: TELEFONE, WHATSAPP, EMAIL, PRESENCIAL, OUTRO. WHATSAPP é apenas registro manual.
Observações opcionais até 4000 caracteres. Próximo contato deve ser futuro;
omitido/null no registro de contato limpa a data atual, sem apagar o histórico.
Resultados têm OS opcional da mesma oficina; não é necessário que seja a OS de origem.
Encerrar limpa próximo contato e carimba encerramento.

Toda escrita operacional exige `revisao` não negativa. Lock por oportunidade,
comparação da revisão e `@Version` evitam sobrescrita silenciosa. Duas tentativas com
a mesma revisão: a primeira pode gravar, a segunda recebe 409 e deve recarregar.
Um contato rejeitado não entra no histórico; pode ser reenviado conscientemente com
a revisão atual. A unicidade de resultado impede receita duplicada também no banco.

## Auditoria

Eventos do módulo não duplicam a timeline da OS. Registram criação, status,
responsável, contato, próximo contato, resultado, valor recuperado e encerramento,
incluindo perda/descarte no novo status e motivo. Mesmo usuário autenticado que
aciona identificação fica como autor da criação/reconciliação. Todos na transação
da ação. Apenas programação de revisão/reavaliação usa a timeline da OS, com anterior/novo.

## Endpoints e relatórios

Contratos completos, exemplos, filtros e erros em [api-v1.md](../api/api-v1.md#fase-5--dinheiro-esquecido).
Base do módulo: `/api/v1/dinheiro-esquecido`.

| Método | Caminho | Resposta |
|---|---|---|
| POST | `/identificar` | 200 contagens |
| GET | `/oportunidades` | 200 página |
| GET | `/oportunidades/{id}` | 200 detalhe/históricos |
| POST | `/oportunidades/{id}/contatos` | 201 oportunidade atualizada |
| POST | `/oportunidades/{id}/resultados` | 201 oportunidade atualizada |
| POST | `/oportunidades/{id}/status` | 200 oportunidade atualizada |
| PUT | `/oportunidades/{id}/responsavel` | 200 oportunidade atualizada |
| PUT | `/oportunidades/{id}/proximo-contato` | 200 oportunidade atualizada |
| GET | `/resumo` | 200 indicadores |
| GET/PUT | `/api/v1/ordens-servico/{id}/proxima-revisao` (fora da base do módulo) | 200 programação |
| GET/PUT | `/api/v1/orcamento-versoes/{id}/reavaliacao` (fora da base do módulo) | 200 programação |

Paginação padrão `pagina=0&tamanho=20`, teto 100, feita no PostgreSQL. Ordenação por
allowlist, desempate por id e nulos por último. Filtros opcionais dinâmicos e
parametrizados: tipo, status, responsável, cliente, veículo, criação, próximo contato,
faixa de idade. Não usa `:data is null` com parâmetro temporal sem tipo.
Projeção por joins, sem uma busca por cliente/veículo para cada linha.

Resumo usa agregações no banco. Abertas = ABERTA + EM_CONTATO + AGENDADA; potencial
conhecido soma somente essas oportunidades (nulos ignorados). Sem período, considera
toda a carteira. `de/ate` inclusivos delimitam criação para carteira/grupos e
encerramento para recuperação/perda, permitindo contar negócios recuperados no
período mesmo que a oportunidade tenha sido criada antes dele.

**Taxa = 100 × recuperadas / (recuperadas + perdidas)** encerradas no período.
Descartadas excluídas, por não serem tentativas concluídas de conversão; denominador
zero devolve 0.00. HALF_UP com duas casas. Não representa taxa da coorte criada no período.

Agrupa por tipo, status, responsável (UUID ou SEM_RESPONSAVEL), mês de criação em
São Paulo e idade das ativas. Grupos sem registros são omitidos. Faixas 0–7, 8–15,
16–30, 31–60 e mais de 60 dias completos desde elegibilidade; no detalhe/listagem,
idade congela no encerramento. `diasEmAberto` não começa na data da varredura.

## Autorização e isolamento

OWNER e ATENDENTE operam todos os endpoints novos, coerente com orçamento e
relações comerciais. MECANICO não acessa o módulo financeiro nem as programações
comerciais; mantém as permissões anteriores sobre OS. Responsável comercial precisa
ser OWNER/ATENDENTE ativo da oficina; null desatribui.

Tenant exclusivamente do contexto autenticado. JPA com `TenantEntity`, consultas
JDBC com oficina explícita, joins e FKs compostos. Sem oficinaId no DTO.
Recurso/vínculo alheio recebe 404; filtro alheio página vazia; sem sessão 401;
papel proibido 403. Problem Details preservado, nenhum code novo.

## Validação

Concluída em 16/09/2026, na branch `feature/fase5-dinheiro-esquecido`, sem commit,
push, merge ou troca de branch.

```text
mvn -B -ntp verify
Testes unitários (surefire):     12 — 0 failures, 0 errors, 0 skipped
Testes de integração (failsafe): 90 — 0 failures, 0 errors, 0 skipped
  Fase1IT 22 | Fase2IT 27 | Fase3IT 15 | Fase5IT 26
BUILD SUCCESS
```

Regressão das Fases 1–4 preservada: os 64 testes de integração anteriores continuam
passando sem alteração, com PostgreSQL 17.11 e MinIO reais via Testcontainers.

Stack Docker isolada (projeto `garagem-fase5`, portas remapeadas, volumes próprios;
os volumes `garagem-saas_*` do desenvolvedor não foram tocados):

```text
postgres  healthy
minio     up
api       healthy
GET /actuator/health        200 {"status":"UP"}
GET /v3/api-docs            200 — 11 caminhos e 13 operações da fase presentes
GET /swagger-ui/index.html  200
flyway_schema_history       V1, V2, V3 aplicadas com sucesso
```

Fluxo HTTP exercitado na stack real, com a API empacotada: cadastro de cliente,
veículo e OS; orçamento publicado; identificação antes de 7 dias não cria nada;
após 168 horas cria uma oportunidade com potencial 2000.00 e a repetição devolve
`{criadas:0}`; contato WHATSAPP move para EM_CONTATO; revisão antiga devolve 409;
próximo contato futuro aceito e data passada rejeitada com 400; responsável atribuído;
recuperação de 1450.00 diferente do potencial; segunda recuperação devolve 409;
resumo com valorRecuperado 1450.00, quantidadeRecuperada 1 e taxa 100.00; auditoria
com criação, status, responsável, contato, próximo contato, resultado, valor e
encerramento; ordenação fora da allowlist devolve 400; MECANICO recebe 403 em
listagem, resumo e identificação; sem sessão, 401. A tentativa de alterar
`publicado_em` direto no banco foi barrada pela trigger `Publicacao imutavel`.

`Fase5IT` acrescenta 26 cenários com PostgreSQL real e relógio controlado para os
limites temporais, sem desativar triggers nem reescrever decisões históricas:
7 dias exatos e publicação/reenvio; aprovação/recusa/substituição; 30 dias e override;
negociação posterior; revisão futura/vencida e atendimento posterior antes/depois da
identificação; fluxo completo com contatos, responsável, recuperação, resumo/auditoria;
perda/descarte; validações; papéis/sessão; isolamento das três origens, referências e
programações; filtros/páginas/sort; limites das cinco faixas; taxa e período de
encerramento; identificação/contatos/resultados/status concorrentes; constraints,
imutabilidade e upgrade de base V2 com dados; OpenAPI.

### Achados e correções durante a implementação

- README ainda indicava Fase 4 em andamento. Atualizado conforme encerramento
  informado pelo time e entregas existentes; documentos históricos preservados.
- Não existia marco estruturado de publicação, próxima revisão ou reavaliação.
  Adicionados de forma compatível, sem tratar criação de versão como envio nem
  alterar histórico imutável.
- DTOs genéricos como StatusEntrada/EventoSaida poderiam colidir com nomes da OS
  no OpenAPI. Todos os schemas novos receberam prefixo Recuperacao.
- Primeira rodada: 17/18 cenários novos passaram; uma asserção comparava BigDecimal
  por escala (`900.0` versus `900.00`). A concorrência já produzia um sucesso e um
  conflito. A asserção passou a comparar o valor monetário, sem mudar a regra de negócio.
- Primeiro comando Compose foi recusado por variáveis ausentes em um arquivo
  temporário malformado. O arquivo foi corrigido antes de criar a stack; nenhuma
  configuração versionada ou credencial do desenvolvedor foi alterada.
- Na auditoria final, `aprovadosRecusadosESubstituidosNaoSaoEsquecidos` falhou com
  409 ao cadastrar veículo. Causa raiz: o helper `placaUnica` sorteava a placa em um
  espaço de 10.000 valores e a classe cria dezenas de veículos na mesma oficina, que
  tem `unique(oficina_id, placa)` — colisão intermitente, não regressão de regra. O
  gerador passou a ser sequencial, garantindo placa única por execução. Nenhuma
  asserção foi enfraquecida e nenhuma regra de negócio foi alterada.

## PENDÊNCIA PARA KAUÃ

- Construir a tela operacional com listagem paginada/filtros/ordenação e detalhe.
- Acionar identificação e apresentar contagens/última execução da própria sessão;
  GETs não atualizam elegibilidade automaticamente.
- Formulários de contatos, responsável, próximo contato, recuperação e encerramento.
- Programação de próxima revisão na OS concluída e reavaliação na versão recusada.
- Indicadores com a fórmula/semântica temporal acima; exibir valor desconhecido como
  “não avaliado”, nunca zero inventado; resolver nomes dos responsáveis pela equipe existente.
- Tratar 409 recarregando e pedindo nova decisão do usuário; não repetir recuperação
  automaticamente. Usar permissões, erros por campo e estados vazios reais.
- Testar integração visual com API real; nenhum dashboard/tela foi criado nesta frente.

## Limites técnicos

Identificação é manual, sem SLA de atualização; transações por OS permitem progresso
parcial e repetição, sem atomicidade de toda a oficina. Históricos do detalhe são
arrays completos por oportunidade, não a base inteira. Não foi executado teste de
carga. A regra de atendimento posterior usa OS concluída por ausência de tipificação
de revisão no domínio. Cancelamento de OS, ajuste/estorno de recuperação, gestão de
links antigos e deploy de staging não fazem parte desta entrega.

FASE 5 — PARTE DO CAUÃ CONCLUÍDA: SIM

A Fase 5 como um todo continua em andamento: falta a frente do Kauã, listada acima.
