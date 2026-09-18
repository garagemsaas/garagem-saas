# Processo de Atendimento

**Versão 1.0 — processo interno.** Escrito para quem atende. Complementa `canais-suporte.md`
(quem entra em contato, por onde) e `sla.md` (prazos).

---

## 1. Fluxo

```text
Cliente entra em contato
        ↓
[1] Registro do atendimento          ← obrigatório, antes de qualquer resposta
        ↓
[2] Classificação  P1 / P2 / P3 / P4
        ↓
[3] Primeira resposta                ← dispara o prazo do SLA
        ↓
[4] Diagnóstico
        ↓
   ┌────┴─────────────┐
   ↓                  ↓
[5a] Orientação    [5b] Correção
   └────┬─────────────┘
        ↓
[6] Validação com o cliente
        ↓
[7] Encerramento
```

## 2. As sete etapas

### [1] Registro

**Nada é atendido sem registro.** Mesmo a dúvida resolvida em dois minutos por WhatsApp é
registrada. Sem registro não há prazo medido, não há histórico e um problema recorrente nunca é
identificado como recorrente.

Conferir se vieram as informações obrigatórias de `canais-suporte.md` §4. Faltando, pedir na
primeira resposta — e registrar mesmo assim.

### [2] Classificação

Atribuir P1–P4 pelos critérios de `sla.md` §2. Na dúvida entre dois níveis, **classificar no mais
grave** e reclassificar depois do diagnóstico, registrando a mudança e o motivo.

Antes de classificar como defeito, descartar comportamento esperado:

| Sintoma | Verificar antes |
|---|---|
| "Bloqueou o cadastro" | Área de assinatura: limite do plano atingido? → P4 comercial |
| "Não envia foto" | Armazenamento cheio? Arquivo não é PNG/JPEG? Acima de 10 MB? → P4 |
| "Não consigo entrar" | Usuário desativado? Oficina inativa? → P3 |
| "Sumiu o botão" | Papel do usuário permite a ação? (`permisoes.md`) → P4 |
| "Registro não aparece" | Está na oficina certa? → P4 |

### [3] Primeira resposta

Confirmar recebimento, informar a classificação e dizer o próximo passo. Nos prazos de `sla.md` §3.

Primeira resposta **não é solução**. Uma resposta honesta de "recebido, é P2, estamos investigando,
retorno até as 16h" vale mais do que silêncio até resolver.

### [4] Diagnóstico

Ordem de investigação:

1. **`requestId`** informado pelo cliente → localizar no log do servidor. Traz oficina, usuário,
   rota, status e duração.
2. Reproduzir em ambiente de teste, com dados equivalentes. **Nunca reproduzir em produção usando a
   conta do cliente.**
3. Conferir a área de assinatura da oficina: status, plano e consumo explicam boa parte dos
   bloqueios.
4. Conferir a linha do tempo da OS ou o histórico de cobrança — ambos são imutáveis e mostram quem
   fez o quê e quando.

**Limite firme:** não alterar dado operacional da oficina diretamente no banco. Se o dado está
errado, a correção é feita pela oficina, pela interface. Se a interface não permite corrigir, isso é
um defeito e vira chamado de produto.

### [5a] Orientação

Quando não há defeito. Explicar o comportamento, mostrar o caminho na tela e **registrar no chamado
qual foi a dúvida** — três oficinas com a mesma dúvida indicam problema de interface, não de
treinamento.

### [5b] Correção

Quando há defeito. Abrir registro técnico com passos de reprodução, escalonar conforme
`canais-suporte.md` §6, e manter o cliente informado na periodicidade da prioridade.

Correção de defeito **exige teste automatizado que falhe antes e passe depois**. É a regra do
projeto desde a Fase 1.

### [6] Validação

Confirmar **com o cliente** que o problema foi resolvido. Não encerrar por conta própria.

Sem resposta do cliente: seguir a régua de `[INSERIR REGRA — sugestão: cobrar em 2 dias úteis,
encerrar em 5 dias úteis com aviso de reabertura possível]`.

### [7] Encerramento

Registrar solução aplicada, categoria do problema e data. Em P1, produzir relatório de causa raiz se
solicitado (`sla.md` §9).

## 3. Estrutura mínima de registro

Enquanto não houver sistema de chamados (pendência A3), manter em planilha ou documento com estes
campos:

| Campo | Obrigatório | Observação |
|---|---|---|
| Protocolo | Sim | Sequencial ou data-hora |
| Oficina | Sim | Identificador usado no login |
| Usuário | Sim | Nome e papel de quem relatou |
| Cliente final envolvido | Não | Só quando o caso for sobre um atendimento específico |
| Data e hora de abertura | Sim | Base da contagem de SLA |
| Canal | Sim | E-mail, WhatsApp, sistema |
| Problema relatado | Sim | Nas palavras do cliente |
| `requestId` | Não | Quando houver |
| Prioridade | Sim | P1–P4, com justificativa se reclassificada |
| Responsável | Sim | Quem está com o chamado |
| Status | Sim | Aberto / Em análise / Aguardando cliente / Resolvido / Encerrado |
| Descrição técnica | Sim | O que foi investigado e encontrado |
| Solução | Sim | O que foi feito ou orientado |
| Data e hora de encerramento | Sim | Fecha a medição |

**Não registrar no chamado:** senha, token, conteúdo de `PAYMENT_WEBHOOK_SECRET`, `JWT_SECRET` ou
qualquer credencial — mesmo que o cliente envie. Se o cliente enviar senha, orientá-lo a **trocá-la
imediatamente** e não copiar o valor para o registro.

## 4. Situações especiais

### Suspeita de vazamento entre oficinas

**Escalonar para N3 imediatamente**, sem triagem. É o risco mais grave do produto. Preservar
evidências (`requestId`, horário, telas) e não apagar nada. Acionar o plano de resposta a incidente
(pendência P9 de `revisao-lgpd.md`).

### Pedido de dados pessoais (LGPD)

- Titular é cliente final da oficina → encaminhar à oficina, que é a controladora.
- Titular é contato da conta → encaminhar ao encarregado.
- Registrar o pedido e a data. Ver Política de Privacidade §7.

### Pedido de exportação de dados

Enquanto não houver exportação na Plataforma (pendência P1), o atendimento é operacional. Registrar,
escalonar para N2 e entregar no prazo de `politica-cancelamento.md` §6.

### Cobrança e cancelamento

Tratar **somente com o proprietário da conta**. Se outro usuário abriu o chamado, pedir a
confirmação do proprietário antes de qualquer ação.

Nunca reativar assinatura por alteração manual em banco: a reativação é automática na confirmação do
pagamento. Se não for, isso é defeito e vira P2.

## 5. Indicadores sugeridos

| Indicador | Para quê |
|---|---|
| Chamados por prioridade, por mês | Dimensionar equipe |
| % de primeiras respostas dentro do SLA | Cumprimento do acordo |
| Tempo médio de resolução por prioridade | Realismo dos alvos |
| Chamados por categoria | Onde o produto confunde |
| % de orientação vs. defeito | Muita orientação = problema de interface |
| Reincidência da mesma oficina | Risco de cancelamento |

---

## Pendências para a empresa

| # | Pendência | Referência |
|---|---|---|
| T1 | Contratar sistema de chamados | `canais-suporte.md` A3 |
| T2 | Nomear responsáveis N1/N2/N3 | `canais-suporte.md` A5 |
| T3 | Definir régua de inatividade do cliente | Etapa [6] |
| T4 | Definir plano de resposta a incidente | `revisao-lgpd.md` P9 |
