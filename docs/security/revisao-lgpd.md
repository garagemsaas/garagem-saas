# Revisão técnica de LGPD e controle de acesso

Revisão do código, das migrations `V1`–`V4` e dos testes em **17/09/2026**, na branch
`fase-7-preparacao-vendas`. Escrita para quem vai revisar segurança e para o encarregado de dados.

**Conclusão:** não foi encontrado vazamento entre oficinas, IDOR, `tenant_id` manipulável, consulta
sem filtro de tenant, upload público indevido nem URL previsível. As pendências abertas são de
funcionalidade de atendimento ao titular e de decisão organizacional, não de falha de isolamento.

---

## 1. Prioridade máxima: isolamento entre oficinas

### Como o tenant é decidido

O identificador da oficina **nunca vem do corpo, da query string ou de um cabeçalho**. Ele é lido da
claim `oficina_id` do JWT e, a cada requisição, reconferido no banco
([TenantRequestFilter.java](../backend/src/main/java/br/com/garagem/tenancy/TenantRequestFilter.java)):

```sql
select count(*) from usuario u join oficina o on o.id = u.oficina_id
 where u.oficina_id = ? and u.id = ? and u.ativo = true and u.papel = ? and o.situacao = 'ATIVA'
```

Consequência prática: um token de usuário desativado, com papel alterado ou de oficina inativa é
recusado com 401 **mesmo antes de expirar**. Um token forjado não passa pela validação de assinatura
HS256 com audience e issuer fixos.

Nenhum DTO de entrada da API possui campo `oficinaId`. Verificado por varredura.

### Três camadas de contenção

| Camada | Mecanismo | O que impede |
|---|---|---|
| Aplicação (JPA) | `@TenantId` em `TenantEntity` + `TenantResolver` | Hibernate injeta o filtro por oficina em toda consulta de entidade |
| Aplicação (SQL direto) | Todo `jdbc`/`NamedParameterJdbcTemplate` recebe `TenantContext.current()` | Consulta manual sem filtro |
| Banco | Chaves estrangeiras compostas `(id, oficina_id)` | Relacionar registro de outra oficina, mesmo com bug na aplicação |

**Varredura realizada:** toda instrução SQL e JPQL em `backend/src/main/java` foi inspecionada. As
únicas consultas sem filtro por oficina são, por desenho:

1. **Login** (`AuthService`): busca por slug da oficina + e-mail. É a resolução do tenant; devolve
   escopo, não dado de negócio.
2. **Link público** (`TenantRequestFilter`): busca por hash do token. Devolve apenas o par
   (link, oficina); é o que permite ao cliente final abrir o acompanhamento.
3. **Catálogo de planos** (`plano`): tabela global, sem dado pessoal, igual para todos.
4. **Recebimento de webhook** (`webhook_pagamento`): o evento chega antes de sabermos a oficina.
   Tratado no item 4 abaixo.

### Testes que sustentam a conclusão

| Teste | O que prova |
|---|---|
| `Fase2IT#matrizDePermissoes` | Matriz completa de papéis por endpoint |
| `Fase5IT#isolamentoLeituraEscritaReferenciasERelatorios` | Oficina A não lê, não escreve, não vincula nem relata dados de B |
| `Fase7IT#oficinaNaoVeAssinaturaNemConsumoNemEventosDaOutra` | Assinatura, consumo e histórico financeiro isolados |
| `Fase7IT#webhookNaoDeixaOPayloadEscolherAOficinaAlvo` | Payload assinado não escolhe o tenant alvo |
| `Fase7IT#mecanicoNaoAcessaAreaFinanceiraESemSessaoTambemNao` | 403 por papel, 401 sem sessão |

## 2. Broken Access Control e IDOR

- Recurso de outra oficina responde **404**, não 403: não se confirma a existência do registro.
- Toda leitura por id passa por `findByIdAndOficinaId`; nenhum repositório expõe `findById` puro
  para entidade de tenant.
- Autorização por papel é declarativa (`@PreAuthorize`) e verificada pela matriz de `Fase2IT`.
- Ordenação dinâmica passa por allowlist (`Pagina.request` e `ConsultaRecuperacao.ORDEM`); campo fora
  da lista é 400. Testado com tentativa de injeção em `Fase5IT`.
- Identificadores são UUID aleatórios; não há id sequencial adivinhável exposto. `ordem_servico.numero`
  é sequencial, mas é único por oficina e não serve de chave de acesso.

## 3. Uploads e URLs previsíveis

| Risco | Situação |
|---|---|
| Bucket público | **Não.** O repositório é privado; os bytes passam pela API, que confere oficina e vínculo com a OS antes de ler o objeto |
| Nome de arquivo adivinhável | Chave é `oficina/os/uuid`, derivada de identificador interno, nunca do nome enviado |
| Tipo por extensão | **Não.** O tipo vem do conteúdo decodificado; PNG/JPEG apenas, com teto de 10 MB e 20 MP |
| Metadados EXIF/GPS | Descartados: a imagem é reescrita no servidor |
| Link público de OS | Token aleatório de 43 caracteres, armazenado **somente como hash**, com expiração e revogação; ausente/expirado/revogado responde 404 indistinguível |
| Vazamento do token na resposta de erro | Tratado: `ApiErrors` substitui o `instance` por `/api/v1/publico/oculto` |

## 4. Superfície nova da Fase 7

| Item | Avaliação |
|---|---|
| `POST /api/v1/webhooks/pagamento` sem JWT | Necessário: gateway não tem sessão. Compensado por HMAC-SHA256 do corpo cru, comparado em tempo constante (`MessageDigest.isEqual`) |
| Segredo ausente | **Falha fechado**: sem `PAYMENT_WEBHOOK_SECRET` nenhum evento é aceito |
| Replay | Unicidade `(provedor, provider_event_id)` no banco; reentrega devolve 200 sem reprocessar |
| Escolha do tenant pelo payload | O tenant é resolvido pelo `provider_subscription_id` já cadastrado. Assinatura externa desconhecida → evento registrado e **ignorado** |
| Corpo adulterado após assinar | Recusado com 401; nada é gravado |
| Segredo no frontend | Nenhum. `PAYMENT_WEBHOOK_SECRET`, `PAYMENT_API_KEY` e `JWT_SECRET` só existem no servidor, por variável de ambiente |
| Dado sensível em log financeiro | `CobrancaService.higienizar` descarta metadado cuja chave contenha senha, token, segredo, autorização, cartão, CVV, chave de API ou assinatura. Testado em `Fase7IT#logDeCobrancaNaoGuardaSegredo` |
| Trilha financeira adulterável | Travada no banco por gatilho; `UPDATE`/`DELETE` falham |

## 5. Princípios da LGPD

| Princípio | Situação |
|---|---|
| Necessidade e minimização | **Atende.** Inventário em `politica-privacidade.md` §2. IP só na decisão de orçamento; logs sem IP, sem user agent, sem corpo de requisição; EXIF descartado no upload |
| Finalidade | **Atende.** Cada campo tem finalidade operacional direta |
| Qualidade e exatidão | Atende. Cadastros editáveis com controle de concorrência por revisão |
| Transparência | Atende após publicação dos documentos desta fase |
| Segurança | Atende, conforme itens 1–4 |
| Prevenção | Atende. Trilha imutável de auditoria em OS, recuperação e cobrança |
| Não discriminação | Não aplicável: não há decisão automatizada sobre pessoas |
| Responsabilização | Atende. Auditoria com autor e instante em toda operação sensível |

### Senhas, tokens e credenciais

- Senha: bcrypt custo 12, mínimo 12 caracteres, teto de 72 bytes. Texto puro nunca é gravado nem
  registrado em log.
- Access token de curta duração (padrão 15 minutos); refresh token guardado como hash SHA-256, com
  expiração e revogação.
- Sessão do frontend apenas em memória: sem cookie, sem `localStorage`, sem `sessionStorage`.

## 6. Achados

### Corrigidos nesta fase

| # | Achado | Correção |
|---|---|---|
| C1 | Oficina poderia existir sem assinatura (bootstrap, importação, teste), deixando a aplicação de limites em estado indefinido | Invariante movido para o banco: gatilho `assinatura_da_oficina` cria a assinatura de avaliação junto com a oficina |
| C2 | `assinatura.cancelada_em` obrigava status `CANCELADA`, o que impedia o cancelamento agendado e travava a operação já paga | Restrição relaxada para implicação, não equivalência |
| C3 | Webhook não enxergava a assinatura: a sessão do Hibernate abria antes de o tenant ser resolvido, e o `@TenantId` filtrava com tenant vazio | Tenant instalado antes da abertura da transação, com `TransactionTemplate` explícito |
| C4 | Inadimplência bloqueava a operação imediatamente, esvaziando o período de tolerância | `INADIMPLENTE` passa a permitir operação; só `SUSPENSA` bloqueia |

### Pendências abertas

| # | Pendência | Natureza | Onde se aplica | O que falta |
|---|---|---|---|---|
| **P1** | Não há endpoint de **exportação completa** dos dados da oficina | Funcionalidade | Atendimento ao art. 18, V e Política de Cancelamento | Implementar exportação (JSON/CSV + arquivos). Hoje o atendimento é operacional, conduzido pela equipe |
| **P2** | Não há **eliminação ou anonimização por titular** | Funcionalidade | Art. 18, VI | Definir o que é eliminável sem quebrar histórico contábil e de auditoria imutável, e implementar anonimização em vez de exclusão física |
| **P3** | **Trial não expira sozinho** | Funcionalidade | `StatusAssinatura.TRIAL` | Não há agendador no projeto. A expiração depende hoje de evento do gateway. Definir se entra rotina agendada |
| **P4** | **Retenção não é aplicada automaticamente** | Funcionalidade + decisão | Logs, auditoria, dados pós-cancelamento | Definir prazos (§5 da Política) e implementar expurgo |
| **P5** | Contrato de **operador com subcontratados** | Jurídica | Nuvem, armazenamento, gateway | Formalizar cláusulas de proteção de dados com cada fornecedor |
| **P6** | **Encarregado (DPO)** não designado | Organizacional | Política de Privacidade | Nomear e publicar contato |
| **P7** | **Política de backup e teste de restauração** | Organizacional | `backup-piloto.md` existe; falta periodicidade e teste formal | Definir RPO/RTO e testar restauração |
| **P8** | **Região de hospedagem** indefinida | Organizacional | Transferência internacional | Decidir e documentar salvaguarda se for fora do Brasil |
| **P9** | **Plano de resposta a incidente** | Organizacional | Art. 48 | Definir prazo interno, responsáveis e modelo de comunicação |

## 7. O que foi explicitamente testado nesta revisão

```
122 testes de integração, 15 unitários — BUILD SUCCESS
PostgreSQL 17.11 real via Testcontainers
```

Cobertura direta dos vetores desta revisão: isolamento de assinatura, consumo e eventos entre
oficinas; webhook sem assinatura, com assinatura errada, com corpo adulterado, duplicado, de tipo
desconhecido e apontando para assinatura externa inexistente; 403 por papel e 401 sem sessão em
todos os endpoints financeiros; imutabilidade da trilha financeira no banco; higienização de
metadados sensíveis.
