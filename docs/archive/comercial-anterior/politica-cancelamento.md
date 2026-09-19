# Política de Cancelamento

**Versão 1.0 — minuta para revisão jurídica.**

Esta política descreve o que o sistema **faz hoje**. Cada regra abaixo corresponde a comportamento
implementado e testado; onde a decisão ainda é da empresa, o campo está marcado como pendência.

---

## 1. Como cancelar

O cancelamento é feito pelo próprio proprietário da conta, em **Configurações › Plano e
assinatura**, sem necessidade de contato com o suporte.

- Somente o **proprietário** (OWNER) pode cancelar. Atendente e mecânico não têm essa ação.
- É obrigatório informar um **motivo**.
- O sistema registra, de forma imutável: quem solicitou, quando solicitou, o motivo e a data efetiva
  de término.

Também é possível cancelar pelos canais de suporte (`canais-suporte.md`), caso a oficina prefira.
Nesse caso a solicitação é registrada no chamado e executada pela equipe.

## 2. Prazo

Não há prazo mínimo de permanência nem aviso prévio obrigatório. `[CONFIRMAR: se o contrato
comercial previr fidelidade ou multa rescisória, esta seção precisa ser reescrita para refletir
aquilo, e o sistema hoje não impõe nenhuma das duas.]`

## 3. Duas modalidades

### 3.1 Cancelamento ao fim do período contratado (recomendado)

- A assinatura permanece **vigente até o fim do período já pago**.
- Durante esse intervalo, **nada muda**: a oficina continua abrindo ordens de serviço, cadastrando
  clientes e veículos, enviando fotos e criando usuários, dentro dos limites do plano.
- Na data efetiva, a assinatura passa a CANCELADA automaticamente.
- **Pode ser revogado a qualquer momento até a data efetiva**, pelo botão "Reativar assinatura", sem
  nova cobrança e sem perda de nada.

### 3.2 Cancelamento imediato

- O acesso de criação termina no ato.
- A partir daí ficam bloqueadas: abertura de OS, cadastro de clientes e veículos, envio de fotos e
  criação de usuários.
- **Continuam liberados**: login, consulta a todo o histórico, área financeira e reativação.

## 4. Cobranças já processadas

`[INSERIR POLÍTICA DE REEMBOLSO — decisão comercial e jurídica da empresa.]`

Pontos que a decisão precisa cobrir, porque hoje o sistema não implementa nenhum deles:

- Cancelamento imediato dentro de um período já pago gera reembolso proporcional?
- Há período de arrependimento? Em venda a distância a consumidor, o art. 49 do CDC prevê 7 dias;
  cabe ao jurídico avaliar a aplicabilidade a contratação entre empresas.
- Cobranças já processadas antes da solicitação são estornadas?

**Comportamento atual do sistema:** nenhum estorno ou cálculo proporcional é executado
automaticamente. Qualquer devolução é operação manual do financeiro.

## 5. Acesso após o cancelamento

Depois de a assinatura ficar CANCELADA:

| Ação | Disponível |
|---|---|
| Login | Sim |
| Consultar clientes, veículos, OS, orçamentos, fotos e histórico | Sim |
| Área financeira e histórico de cobrança | Sim |
| Reativar a assinatura | Sim |
| Abrir OS, cadastrar cliente/veículo, enviar foto, criar usuário | **Não** |

Esse acesso de leitura permanece durante o **período de recuperação** da cláusula 7.

## 6. Exportação de dados

A oficina é titular dos dados que inseriu e pode solicitar a exportação.

> **Pendência técnica (P1 em `revisao-lgpd.md`):** não existe hoje botão de exportação completa na
> Plataforma. A exportação é atendida **operacionalmente**: a oficina solicita pelos canais de
> suporte e a equipe entrega os dados em formato legível.
>
> Prazo de atendimento: `[INSERIR PRAZO — sugestão: 15 dias corridos]`.
>
> Enquanto essa funcionalidade não existir, **a exportação deve ser solicitada antes do fim do
> período de recuperação da cláusula 7.**

## 7. Retenção e período de recuperação

| Fase | Duração | O que acontece |
|---|---|---|
| Recuperação | `[INSERIR PRAZO — sugestão: 90 dias após a data efetiva]` | Dados preservados integralmente. Leitura e reativação disponíveis |
| Após a recuperação | — | Dados elegíveis à eliminação, conforme `[INSERIR DECISÃO]` |

Registros com obrigação legal ou contábil própria — trilha financeira, auditoria e registros de
acesso — seguem os prazos da Política de Privacidade, §5, e **não são eliminados** junto com os
dados operacionais.

> **Pendência (P4 em `revisao-lgpd.md`):** o expurgo automático não está implementado. Hoje nenhum
> dado é apagado por decurso de prazo. A eliminação, quando ocorrer, é operação manual.

## 8. Reativação

- Disponível a qualquer momento pelo proprietário, na área de assinatura.
- **Cancelamento agendado:** a reativação o revoga na hora e a assinatura segue o ciclo normal, sem
  cobrança adicional.
- **Assinatura já cancelada:** a reativação recoloca a conta no fluxo de cobrança; o acesso pleno
  volta **na confirmação do pagamento**, que é automática.
- Nenhuma reativação exige alteração manual em banco de dados.
- Reativação dentro do período de recuperação devolve a conta com **todos os dados intactos**.

## 9. Cancelamento por iniciativa da Plataforma

A Plataforma pode encerrar a assinatura em caso de violação dos Termos de Uso (cláusula 11) ou
determinação legal, mediante comunicação prévia, preservados os dados da oficina e o direito de
exportação desta política.

---

## Coerência entre política e código

| Regra desta política | Onde está implementada |
|---|---|
| Só OWNER cancela | `AssinaturaController` — `@PreAuthorize("hasRole('OWNER')")` |
| Motivo obrigatório | `AssinaturaService.cancelar` — 400 sem motivo |
| Registro de quem, quando, motivo e data efetiva | Colunas `cancelada_por`, `cancelada_em`, `cancelamento_motivo`, `cancelamento_efetivo_em` |
| Agendado preserva operação até o fim do período | `Fase7IT#cancelamentoAoFimDoPeriodoPreservaAcessoEDados` |
| Imediato bloqueia criação, preserva dados | `Fase7IT#cancelamentoImediatoBloqueiaCrescimentoSemApagarDados` |
| Revogação do agendado | `Fase7IT#reativacaoRevogaCancelamentoAgendadoEVoltaAOperar` |
| Nenhum dado apagado no cancelamento | Sem `DELETE` no fluxo; gatilho impede exclusão de histórico |

**Nenhuma regra desta política contraria o código.** As lacunas (reembolso, prazos de retenção,
exportação automatizada) estão marcadas como pendência em vez de descritas como se existissem.

## Pendências para a empresa

| # | Pendência | Seção |
|---|---|---|
| C1 | Política de reembolso e arrependimento | 4 |
| C2 | Prazo do período de recuperação | 7 |
| C3 | Decisão sobre eliminação após a recuperação | 7 |
| C4 | Prazo de atendimento da exportação | 6 |
| C5 | Confirmar ausência de fidelidade/multa no contrato comercial | 2 |
