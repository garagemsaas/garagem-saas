# Canais de Suporte

**Versão 1.0 — proposta.** Nenhum e-mail, telefone ou endereço foi inventado.

---

## 1. Canais oficiais

| Canal | Endereço | Uso | Situação |
|---|---|---|---|
| E-mail | `[INSERIR E-MAIL DE SUPORTE]` | Canal principal. Gera registro automático | `PRECISA DE DEFINIÇÃO` |
| WhatsApp | `[INSERIR NÚMERO]` | Dúvidas rápidas e P1 fora do e-mail | `PRECISA DE DEFINIÇÃO` |
| Sistema de chamados | `[INSERIR FERRAMENTA E URL]` | Acompanhamento formal com número de protocolo | `PRECISA DE DEFINIÇÃO — não contratado` |

**Somente estes canais são oficiais.** Solicitações recebidas por mensagem direta pessoal, rede
social ou telefone particular de integrante da equipe devem ser redirecionadas para um canal
oficial antes de qualquer atendimento — caso contrário não há registro, não há prazo de SLA e não há
como comprovar o atendimento.

> **Pendência estrutural:** sem sistema de chamados contratado, não existe protocolo, fila nem
> medição de prazo. Enquanto isso, o registro manual descrito em `processo-atendimento.md` §3 é
> obrigatório, sob pena de o SLA ficar sem lastro.

## 2. Horário de atendimento

| Item | Valor | Situação |
|---|---|---|
| Dias e horário | Segunda a sexta, 8h às 18h (Brasília) | `SUGESTÃO — PRECISA DE APROVAÇÃO` |
| Feriados nacionais | Sem atendimento | `SUGESTÃO — PRECISA DE APROVAÇÃO` |
| Plantão P1 | Não oferecido nesta versão | `SUGESTÃO — PRECISA DE APROVAÇÃO` |

Mensagens fora do horário são registradas e entram na fila no início do próximo dia útil. Os prazos
do SLA passam a contar a partir daí.

## 3. Quem pode abrir chamado

| Quem | Pode abrir | Observação |
|---|---|---|
| Proprietário (OWNER) da oficina | Sim | Único que pode tratar de assinatura, plano, cobrança e cancelamento |
| Atendente | Sim | Assuntos operacionais e dúvidas de uso |
| Mecânico | Sim | Assuntos operacionais do próprio trabalho |
| Cliente final da oficina | **Não** | Deve procurar a oficina, que é quem controla os dados dele |
| Terceiros | Não | — |

Pedido de **dados pessoais** (acesso, correção, exclusão) feito por cliente final é encaminhado à
oficina, que é a controladora desses dados. Ver Política de Privacidade, §7.

Assuntos **financeiros e contratuais só são tratados com o proprietário da conta**, ainda que outro
usuário abra o chamado. Isso não é burocracia: evita que uma pessoa sem poder de decisão cancele ou
altere o plano da oficina.

## 4. O que deve acompanhar um chamado

Sem estas informações o chamado não pode ser classificado, e a primeira resposta será um pedido de
complemento — o que consome o prazo do cliente.

**Obrigatório:**

1. Nome da oficina (o identificador usado no login)
2. Nome e papel de quem está relatando
3. O que aconteceu, em uma frase
4. O que se esperava que acontecesse
5. Quando ocorreu (data e hora aproximada)

**Quando houver:**

6. **Número da requisição (`requestId`)** — aparece na mensagem de erro da tela. É o que permite
   localizar o registro exato no servidor. **É a informação mais útil de todas.**
7. Número da OS, placa do veículo ou nome do cliente envolvido
8. Captura de tela com a mensagem de erro visível
9. Se acontece sempre ou de vez em quando
10. Se acontece com outros usuários ou só com um

**Nunca envie senha.** A equipe de suporte nunca pede senha, e nenhum atendimento legítimo exige
isso.

## 5. Classificação de prioridade

A prioridade é atribuída pelo suporte no primeiro atendimento, segundo os critérios de `sla.md` §2.
O cliente pode pedir revisão da classificação; a decisão final é do suporte, com justificativa
registrada no chamado.

| Nível | Critério | Exemplo |
|---|---|---|
| P1 | Sistema fora do ar ou perda de dados | Ninguém consegue entrar |
| P2 | Função crítica parada, sem alternativa | Não abre OS; link do cliente não carrega |
| P3 | Erro com alternativa | Filtro errado na listagem |
| P4 | Dúvida ou melhoria | "Como mudo de plano?" |

**Casos frequentes que não são P1:**

- *"O sistema bloqueou o cadastro"* com mensagem de limite do plano → **P4**, financeiro/comercial.
  É comportamento esperado.
- *"Assinatura suspensa"* → **P4**. Leitura e pagamento continuam liberados; a regularização resolve.
- *"Não consigo enviar foto"* com aviso de armazenamento cheio → **P4**.
- *"Só eu não consigo entrar"* → **P3**, provável usuário desativado ou senha.

## 6. Escalonamento

| Etapa | Quem | Quando |
|---|---|---|
| N1 — Atendimento | `[INSERIR RESPONSÁVEL]` | Toda entrada. Resolve dúvida, uso e configuração |
| N2 — Técnico | `[INSERIR RESPONSÁVEL]` | N1 não resolve, ou há suspeita de defeito |
| N3 — Responsável pelo produto | `[INSERIR RESPONSÁVEL]` | P1, perda de dados, risco de segurança ou decisão de negócio |

**Escalonamento imediato para N3, sem passar por N1 e N2:**

- suspeita de acesso de uma oficina a dados de outra;
- suspeita de vazamento ou incidente de segurança;
- perda ou corrupção de dados;
- sistema indisponível para todos.

Nesses casos aplica-se também o plano de resposta a incidente (pendência P9 de `revisao-lgpd.md`).

## 7. O que o suporte não faz

- Não altera dados operacionais da oficina (OS, orçamento, cliente) diretamente no banco. Correção
  de dado é feita pela própria oficina, pela interface.
- Não reativa assinatura por alteração manual em banco: a reativação é automática na confirmação do
  pagamento.
- Não fornece dados de uma oficina a outra, nem a terceiros, sem ordem judicial.
- Não pede nem aceita senha.

---

## Pendências para a empresa

| # | Pendência | Bloqueia |
|---|---|---|
| A1 | E-mail oficial de suporte | Canal principal |
| A2 | Número de WhatsApp | Canal secundário |
| A3 | Contratar sistema de chamados | Protocolo, fila e medição de SLA |
| A4 | Aprovar horário de atendimento | Contagem de prazos |
| A5 | Nomear responsáveis de N1, N2 e N3 | Escalonamento |
| A6 | Decidir sobre plantão P1 | Prazo de P1 no SLA |
