# Fase 7 — Preparação para Vendas — Cauã

Branch `fase-7-preparacao-vendas`, criada a partir de `feature/fase6-observabilidade-piloto`
(HEAD `fa3a760`), porque as Fases 5 e 6 ainda não estavam na `main` e esta fase depende delas.

Escrito para quem vai manter o módulo de assinatura e cobrança.

---

## 1. Arquitetura do sistema de planos

Módulo `br.com.garagem.assinatura`, na mesma estrutura do resto do projeto
(`domain` / `repository` / `application` / `api`). Nenhuma arquitetura paralela, nenhum
framework novo, nenhuma dependência nova no `pom.xml`.

```text
plano  (catálogo global)          ← todo limite do produto mora aqui
  ↑
assinatura  (1 por oficina)       ← ciclo de vida comercial
  ├── evento_cobranca             ← trilha financeira imutável
  └── webhook_pagamento (global)  ← idempotência do gateway
```

**A tabela `plano` não é multi-tenant de propósito:** é o catálogo da plataforma, igual para todas
as oficinas, e não contém dado pessoal. `assinatura` e `evento_cobranca` são `TenantEntity`, com
`@TenantId` e chaves estrangeiras compostas como todo o resto.

### Por que os limites ficam em tabela, não em código

Mudar o limite de um plano é decisão comercial e acontece com frequência. Se o número estivesse em
constante Java, cada ajuste exigiria build, revisão e deploy. Em tabela, é um `UPDATE` — e o
`PlanoLimiteService` lê o valor a cada verificação, sem cache que possa ficar defasado.

### Estados

| Estado | Equivalente usual | Pode crescer | Significado |
|---|---|:--:|---|
| `TRIAL` | TRIAL | ✅ | Avaliação |
| `ATIVA` | ACTIVE | ✅ | Pagamento em dia |
| `INADIMPLENTE` | PAST_DUE | ✅ | Em tolerância |
| `SUSPENSA` | SUSPENDED | ❌ | Tolerância vencida |
| `CANCELADA` | CANCELED | ❌ | Encerrada |

Nomes em português pela convenção do projeto (`ATIVA`, `PRONTO`, `ABERTA`). O mapeamento acima é a
tradução para o vocabulário de gateways.

**`INADIMPLENTE` permite crescer de propósito.** Cortar a operação já no atraso esvaziaria a
tolerância, que existe justamente para a oficina se regularizar sem parar de trabalhar.

### O que deliberadamente não foi tocado

`oficina.situacao` continua governando o login e **não** é usada para suspensão por inadimplência.
Suspender por ali derrubaria o acesso, e o requisito é o oposto: a oficina suspensa precisa entrar,
consultar e pagar. As duas noções são independentes e permanecem assim.

`oficina.plano` já existia sem uso. Em vez de removê-la, passou a espelhar o código do plano por
gatilho. A fonte de verdade é `assinatura.plano_id`.

## 2. Como criar um novo plano

```sql
insert into plano(id, codigo, nome, descricao, valor_centavos, periodicidade,
                  max_usuarios, max_armazenamento_bytes, max_ordens_servico_mes, max_veiculos, ordem)
values (gen_random_uuid(), 'ENTERPRISE', 'Plano Enterprise', 'Rede com várias unidades.',
        49900, 'MENSAL', 100, 214748364800, null, null, 4);
```

- `codigo` é o identificador estável usado pela API e pelo frontend. Maiúsculas, sem espaço.
- `null` em `max_ordens_servico_mes` ou `max_veiculos` significa **ilimitado**, e o serviço de
  limites trata assim. Usuários e armazenamento são sempre obrigatórios.
- `ativo=false` tira o plano da vitrine sem afetar quem já o assina.
- `provider_price_id` recebe o identificador do preço no gateway, quando houver.

Em produção, prefira uma migration nova a um `UPDATE` manual.

## 3. Como alterar limites

```sql
update plano set max_usuarios = 15 where codigo = 'PROFISSIONAL';
```

Vale na verificação seguinte, sem reinício. **Reduzir um limite não quebra quem já está acima
dele:** o excesso permanece, e apenas novas criações são recusadas. Nada é apagado.

## 4. Como funciona a assinatura

Criada junto com a oficina, pelo gatilho `assinatura_da_oficina`:

```sql
create trigger assinatura_da_oficina after insert on oficina ...
```

O invariante é do banco, não do caminho de cadastro — vale para bootstrap, migração, importação e
teste do mesmo jeito. Sem isso, uma oficina criada por SQL direto ficaria sem assinatura e toda
criação falharia.

Nasce em `TRIAL` por 14 dias, no plano `PROFISSIONAL`.

Leituras normalizam o estado (`AssinaturaService.atual`): tolerância vencida vira `SUSPENSA`,
cancelamento agendado vencido vira `CANCELADA`. Assim o estado não fica defasado por falta de
rotina agendada — que o projeto não tem.

## 5. Como funciona a cobrança

```text
AssinaturaService  ──►  PagamentoProvider (interface)
                          └── ProvedorManual (padrão)
```

`PagamentoProvider` isola o gateway: `criarCliente`, `criarAssinatura`, `cancelarAssinatura`,
`reativarAssinatura`, `mudarPlano`, `consultarAssinatura`, `assinaturaValida`, `lerEvento`.
Nenhum vocabulário de fornecedor vaza para o resto da aplicação.

**Nenhum gateway foi contratado**, então o padrão é `ProvedorManual`: a cobrança acontece fora do
sistema (boleto, PIX, maquininha) e é confirmada pelo **mesmo webhook assinado** que um gateway
usaria. Isso mantém um único caminho de ativação e de inadimplência — quando um gateway entrar, o
fluxo já está exercitado.

**Nenhum método do provedor decide status.** Quem confirma pagamento é o webhook.

## 6. Como configurar o gateway

```env
PAYMENT_PROVIDER=MANUAL
PAYMENT_WEBHOOK_SECRET=<openssl rand -hex 32>
PAYMENT_GRACE_PERIOD=P7D
PAYMENT_API_KEY=
```

Para plugar um gateway real:

1. Implemente `PagamentoProvider` em `application/pagamento/`.
2. Anote com `@ConditionalOnProperty(name="app.pagamento.provedor", havingValue="<NOME>")`.
3. Aponte `PAYMENT_PROVIDER=<NOME>`.

`ProvedorManual` sai de cena sozinho. **Nenhum outro arquivo muda.**

## 7. Como funcionam os webhooks

`POST /api/v1/webhooks/pagamento` — sem JWT, porque gateway não tem sessão.

```text
1. Confere HMAC-SHA256 do corpo cru      → inválido: 401, nada é gravado
2. Grava o recebimento (transação própria) → duplicado: devolve o existente
3. Resolve o tenant pelo provider_subscription_id
4. Aplica o evento sob lock da assinatura
5. Marca como processado
```

Quatro decisões que valem registro:

**Falha fechado.** Sem `PAYMENT_WEBHOOK_SECRET` nenhum evento é aceito. Um ambiente mal configurado
não ativa assinaturas a partir de requisição anônima.

**Idempotência é do banco.** O unique `(provedor, provider_event_id)` decide o empate entre duas
entregas simultâneas. Não é cache em memória.

**O payload não escolhe a oficina.** O tenant sai do `provider_subscription_id` já cadastrado.
Assinatura externa desconhecida → evento registrado e ignorado.

**O tenant é instalado antes da transação abrir.** `@TenantId` é capturado na abertura da sessão do
Hibernate; um `@Transactional` comum abriria a sessão com tenant vazio e não enxergaria a
assinatura. Por isso `processar` usa `TransactionTemplate` explícito. Isso foi um bug real durante a
implementação (C3 em `revisao-lgpd.md`).

Duplicado e tipo desconhecido respondem **200**: reentregar é normal, e devolver erro faria o
gateway repetir indefinidamente um evento já resolvido.

### Vocabulário aceito

| Tipo | Efeito |
|---|---|
| `pagamento.aprovado`, `assinatura.renovada` | Ativa e renova o período |
| `pagamento.falhou` | Entra em tolerância; aplica suspensão se vencida |
| `assinatura.suspensa` | Marca atraso e aplica tolerância |
| `assinatura.cancelada` | Cancela, preservando dados |
| qualquer outro | Registrado como `WEBHOOK_FAILED` e ignorado |

## 8. Como testar webhooks

```bash
CORPO='{"id":"evt_001","tipo":"pagamento.aprovado","assinaturaExterna":"manual:<OFICINA_UUID>"}'
ASSINATURA=$(printf '%s' "$CORPO" | openssl dgst -sha256 -hmac "$PAYMENT_WEBHOOK_SECRET" -hex | awk '{print $2}')

curl -X POST http://localhost:8080/api/v1/webhooks/pagamento \
  -H 'Content-Type: application/json' \
  -H "X-Garagem-Signature: $ASSINATURA" \
  --data-raw "$CORPO"
```

Descubra `assinaturaExterna` com:

```sql
select provider_subscription_id from assinatura where oficina_id = '<OFICINA_UUID>';
```

Resposta: `{"status":"PROCESSADO|DUPLICADO|IGNORADO","eventoId":"evt_001"}`.

**Assine o corpo exato que será enviado.** Qualquer reserialização muda o HMAC. Por isso
`--data-raw`, e por isso o controller recebe `@RequestBody String`.

## 9. Como funciona a inadimplência

```text
ATIVA → pagamento.falhou → INADIMPLENTE → [7 dias] → SUSPENSA
```

Durante a tolerância **nada muda** para a oficina. Vencida, ficam bloqueadas apenas as operações que
aumentam consumo: novo usuário, upload, novo veículo, nova OS.

Seguem liberados: login, leitura de tudo, dashboard, área financeira, histórico de cobrança,
atualização de pagamento e exportação.

`aplicarTolerancia` é idempotente e sem relógio próprio — pode ser chamada por webhook, por leitura
ou por rotina futura sem duplicar suspensão.

## 10. Como funciona a reativação

Automática na confirmação do pagamento. `confirmarPagamento` limpa `inadimplenteDesde` e
`suspensaEm`, volta para `ATIVA` e registra `ACCOUNT_REACTIVATED`.

**Nenhuma reativação exige alteração manual em banco.** Testado em
`Fase7IT#pagamentoConfirmadoReativaAutomaticamente`.

A reativação pedida pela oficina (`POST /assinatura/reativacao`) revoga cancelamento agendado na
hora; a partir de `CANCELADA`, devolve à tolerância — porque quem confirma pagamento é o webhook,
não um clique.

## 11. Como verificar consumo

`GET /api/v1/assinatura/consumo`

```json
{
  "geradoEm": "2026-09-17T13:00:00Z",
  "plano": "PROFISSIONAL",
  "status": "ATIVA",
  "limites": [
    { "chave": "usuarios", "rotulo": "Usuários ativos", "usado": 6, "limite": 10,
      "usadoLegivel": "6", "limiteLegivel": "10", "percentual": 60, "atingido": false },
    { "chave": "armazenamento", "rotulo": "Armazenamento", "usado": 4294967296, "limite": 10737418240,
      "usadoLegivel": "4,0 GB", "limiteLegivel": "10,0 GB", "percentual": 40, "atingido": false },
    { "chave": "veiculos", "rotulo": "Veículos cadastrados", "usado": 87, "limite": 2000,
      "usadoLegivel": "87", "limiteLegivel": "2000", "percentual": 4, "atingido": false },
    { "chave": "ordensServicoMes", "rotulo": "Ordens de serviço no mês", "usado": 41, "limite": 400,
      "usadoLegivel": "41", "limiteLegivel": "400", "percentual": 10, "atingido": false }
  ]
}
```

`limite: null` e `limiteLegivel: "Ilimitado"` quando o plano não restringe.

Contagem feita no banco no instante da chamada, sempre dentro da oficina autenticada. Não há total
mantido à parte, que sairia do lugar em qualquer falha parcial. O mês de `ordensServicoMes` é o mês
civil em America/São_Paulo — o cliente conta o mês pelo calendário dele, não em UTC.

## 12. Como consultar logs de cobrança

`GET /api/v1/assinatura/eventos?pagina=0&tamanho=20` — paginado, mais recentes primeiro.

Tipos: `SUBSCRIPTION_CREATED`, `SUBSCRIPTION_ACTIVATED`, `SUBSCRIPTION_CHANGED`,
`SUBSCRIPTION_CANCELED`, `SUBSCRIPTION_REACTIVATED`, `PAYMENT_APPROVED`, `PAYMENT_FAILED`,
`ACCOUNT_PAST_DUE`, `ACCOUNT_SUSPENDED`, `ACCOUNT_REACTIVATED`, `WEBHOOK_RECEIVED`,
`WEBHOOK_PROCESSED`, `WEBHOOK_FAILED`, `PLAN_LIMIT_REACHED`.

No log estruturado da aplicação: `evento_cobranca` e `webhook_pagamento`.

**Higienização:** `CobrancaService.higienizar` descarta qualquer metadado cuja chave contenha
senha, token, segredo, autorização, cartão, CVV, chave de API ou assinatura — em qualquer
capitalização. O metadado é descartado **inteiro**: é preferível perder diagnóstico a gravar
segredo. Os valores são cortados em 200 caracteres e não é JSON, para não convidar a despejar
payload de gateway.

A tabela é imutável por gatilho: `UPDATE` e `DELETE` falham.

## 13. Onde os limites são aplicados

| Operação | Ponto |
|---|---|
| Criar usuário | `UsuarioController.criar` |
| Enviar foto | `FotoService.upload`, após reescrita e antes do S3 |
| Cadastrar veículo | `VeiculoService.criar` |
| Abrir OS | `OsService.criar` |

Sempre `PlanoLimiteService`, nunca lógica duplicada em controller.

A cota de armazenamento é conferida sobre os **bytes reescritos**, que são os que de fato ocupam o
bucket — não sobre o tamanho informado pelo navegador. E antes do `storage.put`, para não gravar
objeto que será recusado.

### Erros

| `code` | HTTP | Quando |
|---|---|---|
| `PLAN_LIMIT_REACHED` | 402 | Limite de usuários, veículos ou OS/mês |
| `STORAGE_LIMIT_REACHED` | 402 | Armazenamento cheio |
| `SUBSCRIPTION_INACTIVE` | 402 | Assinatura suspensa ou cancelada |

**402 e não 403:** o papel do usuário está correto; o que falta é capacidade contratada. Misturar os
dois no 403 faria a tela dizer "seu papel não permite" para quem é dono da oficina.

## 14. Frontend

`Configurações › Plano e assinatura` (`Subscription.tsx`), usando os componentes existentes
(`Drawer`, `PageState`, `Icon`, `money`, `date`). Nenhum componente visual novo, nenhuma dependência.

Mostra plano, situação em texto, valor, próxima cobrança, barras de consumo, vitrine de planos,
cancelamento com confirmação em duas etapas, reativação e histórico de cobrança.

Só OWNER vê as ações contratuais; ATENDENTE lê; MECANICO não acessa.

**Preço zero é exibido como "Valor a definir", não como "grátis"** — os valores comerciais ainda não
foram definidos, e inventar número na tela seria pior do que assumir a pendência.

`api.ts` ganhou a mensagem de fallback para 402.

## 15. Testes

`Fase7IT` — **27 cenários**, PostgreSQL 17.11 real, relógio controlado.

| Grupo | Cenários |
|---|---|
| Planos e limites | Abaixo, exatamente no e acima do limite; desativar devolve vaga; storage; OS/mês; ilimitado; consumo real |
| Assinatura | Mudança de plano; redução abaixo do uso recusada; revisão desatualizada; cancelamento agendado e imediato; motivo obrigatório; reativação |
| Webhook | Válido; sem assinatura; assinatura errada; corpo adulterado; duplicado; tipo desconhecido |
| Inadimplência | Tolerância; suspensão após o prazo; leitura preservada; reativação automática |
| Segurança | MECANICO 403; sem sessão 401; ATENDENTE lê mas não decide; isolamento A/B de assinatura, consumo e eventos; payload não escolhe tenant; log sem segredo; trilha imutável; OpenAPI |

```text
15 unitários + 122 de integração — 0 falhas, 0 erros, 0 ignorados
Fase1IT 22 | Fase2IT 27 | Fase3IT 15 | Fase5IT 26 | Fase6IT 5 | Fase7IT 27
BUILD SUCCESS
```

Regressão das Fases 1–6 preservada sem alteração em nenhum teste anterior.

## 16. Bugs encontrados e corrigidos durante a implementação

| # | Problema | Correção |
|---|---|---|
| C1 | Oficina podia existir sem assinatura | Invariante movido para gatilho no banco |
| C2 | `check` impedia cancelamento agendado | Restrição relaxada para implicação |
| C3 | Webhook não enxergava a assinatura (tenant resolvido depois da sessão abrir) | `TransactionTemplate` explícito |
| C4 | Inadimplência bloqueava operação, esvaziando a tolerância | `INADIMPLENTE` passa a permitir crescer |
| C5 | `char(3)` em `moeda` quebrava a validação de schema do Hibernate | `varchar(3)` com check de formato |

## 17. Pendências técnicas

| # | Pendência | Impacto |
|---|---|---|
| P1 | Sem endpoint de exportação completa | Atendimento ao titular é operacional |
| P2 | Sem eliminação/anonimização por titular | Art. 18, VI da LGPD |
| P3 | Trial não expira sozinho (não há agendador) | Depende de evento do gateway |
| P4 | Retenção não aplicada automaticamente | Expurgo é manual |
| P5 | Sem gateway contratado | Provedor manual atende, mas sem cobrança automática |
| P6 | Sem prorrateio na mudança de plano | Decisão comercial pendente |
| P7 | Sem estorno/reembolso automatizado | Operação manual do financeiro |

Pendências jurídicas e organizacionais em `revisao-lgpd.md` §6.

## 18. Documentos desta fase

| Documento | Conteúdo |
|---|---|
| `termos-de-uso.md` | Termos de Uso, 22 cláusulas |
| `politica-privacidade.md` | Inventário real de dados, bases legais, retenção |
| `revisao-lgpd.md` | Revisão técnica de isolamento, IDOR, uploads e achados |
| `contrato-comercial.md` | Minuta de contrato, 23 cláusulas |
| `sla.md` | Proposta de SLA com números marcados para aprovação |
| `canais-suporte.md` | Canais, horários, quem abre, o que enviar, escalonamento |
| `politica-cancelamento.md` | Cancelamento, retenção, exportação, reativação |
| `processo-atendimento.md` | Fluxo interno em 7 etapas e registro mínimo |

Todos usam placeholders explícitos onde a informação depende da empresa. **Nenhum CNPJ, razão
social, endereço, e-mail, telefone, foro ou preço foi inventado.**
