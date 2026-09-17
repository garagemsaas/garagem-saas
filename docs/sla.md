# SLA — Acordo de Nível de Serviço

**Versão 1.0 — proposta.** Todos os números abaixo estão marcados como
`SUGESTÃO — PRECISA DE APROVAÇÃO`. **Nenhum deles é compromisso contratual enquanto a empresa não
aprovar e anexar este documento ao contrato.**

---

## 1. Disponibilidade

| Item | Valor | Situação |
|---|---|---|
| Disponibilidade mensal | 99,0% | `SUGESTÃO — PRECISA DE APROVAÇÃO` |
| Janela de apuração | Mês civil | `SUGESTÃO — PRECISA DE APROVAÇÃO` |
| Indisponibilidade mensal tolerada | ~7h18 | Decorre de 99,0% |

99,0% é deliberadamente conservador para o estágio atual: a operação ainda não tem redundância
comprovada nem histórico de medição. Prometer 99,9% agora criaria obrigação que a infraestrutura
atual não sustenta. Revisar para cima depois de três meses de medição real.

**Como medir:** `[INSERIR FERRAMENTA DE MONITORAMENTO]` verificando `GET /actuator/health` a cada
`[INSERIR INTERVALO — sugestão: 1 minuto]`. Considera-se indisponível o período com duas
verificações consecutivas sem resposta 200.

> **Pendência:** não há monitoramento externo contratado. Sem ele não existe medição auditável de
> disponibilidade e a cláusula fica sem lastro.

## 2. Prioridades

| Nível | Definição | Exemplo real do produto |
|---|---|---|
| **P1** | Sistema indisponível para todas as oficinas, ou perda/corrupção de dados | API fora do ar; login falhando para todos; orçamento gravado com valor errado |
| **P2** | Funcionalidade crítica indisponível, sem alternativa | Não é possível abrir OS; upload de fotos falhando; link público de aprovação inacessível ao cliente final |
| **P3** | Erro com alternativa disponível | Filtro de listagem incorreto; indicador com número divergente; erro de layout que não impede a operação |
| **P4** | Dúvida, solicitação de melhoria ou orientação de uso | "Como mudo de plano?"; pedido de novo relatório |

**Cobrança suspensa ou assinatura suspensa por inadimplência não é P1.** É P4, tratado pelo
financeiro — a suspensão é comportamento esperado do produto, e leitura e pagamento seguem
liberados.

## 3. Prazo de primeira resposta

Primeira resposta é a confirmação humana de que o chamado foi recebido, classificado e está em
tratamento. **Não é a solução.**

| Prioridade | Primeira resposta | Situação |
|---|---|---|
| P1 | 1 hora útil | `SUGESTÃO — PRECISA DE APROVAÇÃO` |
| P2 | 4 horas úteis | `SUGESTÃO — PRECISA DE APROVAÇÃO` |
| P3 | 1 dia útil | `SUGESTÃO — PRECISA DE APROVAÇÃO` |
| P4 | 3 dias úteis | `SUGESTÃO — PRECISA DE APROVAÇÃO` |

## 4. Resolução

Resolução é a correção do problema ou a entrega de uma alternativa aceita pelo cliente.

| Prioridade | Alvo de resolução | Situação |
|---|---|---|
| P1 | 8 horas úteis | `SUGESTÃO — PRECISA DE APROVAÇÃO` |
| P2 | 2 dias úteis | `SUGESTÃO — PRECISA DE APROVAÇÃO` |
| P3 | 10 dias úteis | `SUGESTÃO — PRECISA DE APROVAÇÃO` |
| P4 | Sem alvo; resposta orientativa no prazo da cláusula 3 | `SUGESTÃO — PRECISA DE APROVAÇÃO` |

**Alvo de resolução é meta de esforço, não garantia.** Diferente da primeira resposta, que depende
apenas da equipe, a resolução depende da natureza do defeito. A distinção é proposital e deve ser
mantida em qualquer versão contratual.

## 5. Horário de atendimento

| Item | Valor | Situação |
|---|---|---|
| Horário comercial | Segunda a sexta, 8h às 18h (horário de Brasília) | `SUGESTÃO — PRECISA DE APROVAÇÃO` |
| Feriados | Nacionais não são dia útil | `SUGESTÃO — PRECISA DE APROVAÇÃO` |
| Plantão P1 fora do horário | Não oferecido nesta versão | `SUGESTÃO — PRECISA DE APROVAÇÃO` |

Contagem de prazo: chamados abertos fora do horário começam a contar no início do próximo dia útil.

> **Pendência:** sem plantão, um P1 iniciado sexta à noite só tem resposta na segunda. Se isso for
> inaceitável comercialmente, é preciso dimensionar plantão **antes** de assinar o SLA.

## 6. Manutenção programada

- Janela preferencial: `[INSERIR — sugestão: domingos, 0h às 4h]` — `SUGESTÃO — PRECISA DE APROVAÇÃO`
- Aviso prévio: 48 horas — `SUGESTÃO — PRECISA DE APROVAÇÃO`
- Teto mensal: 4 horas — `SUGESTÃO — PRECISA DE APROVAÇÃO`

Manutenção programada e comunicada dentro da janela **não conta** como indisponibilidade.

Correção emergencial de segurança pode ser aplicada sem aviso prévio, com comunicação posterior em
até 24 horas.

## 7. Exceções

Não contam como indisponibilidade nem disparam prazos de SLA:

a) falha de conexão, equipamento, rede ou navegador da Contratante;
b) indisponibilidade de terceiros fora do controle da Plataforma (provedor de nuvem, armazenamento,
   meio de pagamento, DNS, certificados), observado o item 8;
c) suspensão por inadimplência ou por violação dos Termos de Uso;
d) uso fora das condições dos Termos, incluindo automação que degrade o serviço;
e) caso fortuito e força maior;
f) manutenção programada dentro da janela e do teto da cláusula 6;
g) indisponibilidade de funcionalidade não contratada no plano da Contratante.

## 8. Indisponibilidade causada por terceiros

Quando a causa for de fornecedor da Plataforma, a Plataforma se obriga a: comunicar a Contratante,
acionar o fornecedor, informar andamento na periodicidade do item 9 e registrar a causa raiz no
encerramento.

`[INSERIR DECISÃO: a Plataforma assume ou não crédito por indisponibilidade de fornecedor? Repassar
integralmente o risco ao cliente é praxe de mercado, mas é decisão comercial e jurídica da empresa.]`

## 9. Comunicação durante incidente P1

| Momento | Ação | Situação |
|---|---|---|
| Detecção | Aviso às oficinas afetadas | `SUGESTÃO — PRECISA DE APROVAÇÃO` |
| A cada 2 horas | Atualização de andamento | `SUGESTÃO — PRECISA DE APROVAÇÃO` |
| Normalização | Aviso de restabelecimento | `SUGESTÃO — PRECISA DE APROVAÇÃO` |
| Até 5 dias úteis | Relatório de causa raiz, se solicitado | `SUGESTÃO — PRECISA DE APROVAÇÃO` |

## 10. Créditos por descumprimento

`[INSERIR POLÍTICA DE CRÉDITO — decisão comercial da empresa.]`

Modelo comum, apenas como referência, **não aprovado**:

| Disponibilidade no mês | Crédito sobre a mensalidade |
|---|---|
| < 99,0% e ≥ 97,0% | 5% |
| < 97,0% e ≥ 95,0% | 10% |
| < 95,0% | 20% |

Crédito costuma ser abatido na fatura seguinte, mediante solicitação em até 30 dias, e limitado ao
valor da mensalidade do mês afetado.

---

## Resumo das pendências

| # | Pendência | Bloqueia |
|---|---|---|
| S1 | Aprovar todos os números marcados como sugestão | Anexar o SLA ao contrato |
| S2 | Contratar monitoramento externo | Medição auditável de disponibilidade |
| S3 | Decidir sobre plantão fora do horário comercial | Viabilidade do prazo de P1 |
| S4 | Decidir política de créditos | Cláusula 10 |
| S5 | Decidir repasse de risco de fornecedor | Cláusula 8 |
| S6 | Definir ferramenta e intervalo de verificação | Cláusula 1 |
