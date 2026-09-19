# Contrato de Prestação de Serviço — Plataforma Automotiva

**Versão 1.0 — minuta para revisão jurídica. Não utilizar em contratação real antes do aceite do
advogado da empresa e do preenchimento de todos os campos entre colchetes.**

---

## Qualificação das partes

**CONTRATADA**

```text
Razão social:  [INSERIR RAZÃO SOCIAL]
CNPJ:          [INSERIR CNPJ]
Endereço:      [INSERIR ENDEREÇO COMPLETO]
Representante: [INSERIR NOME E CARGO]
E-mail:        [INSERIR E-MAIL OFICIAL]
```

**CONTRATANTE**

```text
Razão social / nome:  [INSERIR]
CNPJ / CPF:           [INSERIR]
Endereço:             [INSERIR]
Representante:        [INSERIR NOME E CARGO]
E-mail:               [INSERIR]
Telefone:             [INSERIR]
```

---

## Cláusula 1 — Objeto

A CONTRATADA licencia à CONTRATANTE, na modalidade software como serviço, o uso da plataforma
Plataforma Automotiva para gestão de oficina mecânica, nos limites do plano contratado na Cláusula 2.

O serviço abrange: cadastro de clientes e veículos, ordens de serviço, checklist de entrada,
diagnóstico, orçamento com versionamento imutável, aprovação pelo cliente final via link público,
registro fotográfico, linha do tempo, indicadores operacionais e módulo de recuperação de receita.

Não abrange: emissão de documento fiscal, controle de estoque, integração bancária, pagamento a
fornecedores, envio automático de mensagens a clientes finais, nem desenvolvimento de
funcionalidade sob medida.

## Cláusula 2 — Plano contratado

```text
Plano:                  [ ] Básico   [ ] Profissional   [ ] Premium
Usuários ativos:        [INSERIR] usuários
Armazenamento:          [INSERIR] GB
Ordens de serviço/mês:  [INSERIR ou "ilimitado"]
Veículos cadastrados:   [INSERIR ou "ilimitado"]
Periodicidade:          [ ] Mensal   [ ] Anual
Valor:                  R$ [INSERIR] por [mês/ano]
Data de início:         [INSERIR]
```

Os limites deste quadro são os efetivamente aplicados pelo sistema. Atingido um limite, novas
operações que aumentem o consumo são recusadas, com aviso do motivo, preservados a consulta e o
histórico existentes.

## Cláusula 3 — Usuários

3.1. A CONTRATANTE administra seus próprios usuários e responde pelas ações praticadas com as
credenciais deles.

3.2. O limite do plano conta **usuários ativos**. Desativar um usuário devolve a vaga; desativação,
edição e exclusão nunca são bloqueadas por limite de plano.

3.3. É vedado compartilhar credenciais entre pessoas para contornar o limite contratado.

## Cláusula 4 — Armazenamento

4.1. O limite considera o total de arquivos armazenados pela CONTRATANTE.

4.2. Antes de aceitar um novo arquivo, o sistema verifica se o total resultante cabe no limite. Não
cabendo, o envio é recusado, sem excluir nada do que já existe.

4.3. São aceitos apenas PNG e JPEG, de até 10 MB e 20 megapixels por arquivo.

## Cláusula 5 — Mensalidade e cobrança

5.1. O valor é o da Cláusula 2, devido pela periodicidade ali definida.

5.2. Meio de pagamento: `[INSERIR]`. Vencimento: `[INSERIR DIA]`.

5.3. Nota fiscal: `[INSERIR REGRA DE EMISSÃO]`.

5.4. A confirmação de pagamento é registrada no histórico financeiro da conta e não depende de
confirmação apresentada em tela.

## Cláusula 6 — Reajuste

O valor será reajustado a cada 12 meses pela variação do `[INSERIR ÍNDICE — ex.: IPCA]`, ou, na sua
extinção, pelo índice que o substituir.

`[CONFIRMAR COM O JURÍDICO: periodicidade mínima legal e índice adotado.]`

## Cláusula 7 — Vigência e renovação

7.1. Vigência inicial: `[INSERIR PRAZO]`, a contar da data de início da Cláusula 2.

7.2. Renovação automática por iguais períodos, salvo manifestação em contrário de qualquer das
partes com `[INSERIR PRAZO — sugestão: 30 dias]` de antecedência.

7.3. `[DEFINIR: há fidelidade e multa rescisória? O sistema hoje não impõe prazo mínimo nem calcula
multa. Se houver, o contrato precisa dizer, e a Política de Cancelamento precisa ser ajustada.]`

## Cláusula 8 — Inadimplência e suspensão

8.1. Não confirmado o pagamento, a assinatura passa à situação de atraso, com aviso na Plataforma.

8.2. Durante o período de tolerância de `[INSERIR PRAZO — padrão técnico atual: 7 dias corridos]` o
uso permanece integral.

8.3. Vencida a tolerância, a assinatura é suspensa. **A suspensão não exclui dados.** Permanecem
disponíveis: login, consulta a todos os registros, área financeira, atualização dos dados de
pagamento e exportação.

8.4. Ficam bloqueadas as operações que aumentam o consumo: criação de usuário, envio de arquivo,
cadastro de veículo e abertura de ordem de serviço.

8.5. Encargos por atraso: `[INSERIR — ex.: multa de 2% e juros de 1% ao mês]`.

## Cláusula 9 — Reativação

Confirmado o pagamento, a reativação é automática e imediata, sem necessidade de solicitação e sem
taxa de religação.

## Cláusula 10 — Alteração de plano

10.1. A CONTRATANTE pode mudar de plano pela Plataforma, a qualquer momento.

10.2. A migração para plano inferior ao uso atual é recusada até o ajuste do consumo. Nenhum dado é
excluído pela CONTRATADA para viabilizar a redução.

10.3. Prorrateio: `[INSERIR REGRA]`.

## Cláusula 11 — Cancelamento

Regido pela Política de Cancelamento, que integra este contrato. O cancelamento pode ser imediato ou
ao fim do período contratado, é solicitado pelo proprietário da conta na própria Plataforma e não
apaga dados no momento da solicitação.

## Cláusula 12 — Obrigações da CONTRATANTE

a) fornecer dados cadastrais verdadeiros e mantê-los atualizados;
b) responder pela licitude e pela base legal dos dados que inserir, inclusive os de seus clientes;
c) zelar pelas credenciais e desativar usuários que deixem a oficina;
d) usar a plataforma conforme os Termos de Uso;
e) pagar a mensalidade nos prazos acordados;
f) indicar ao menos um contato para comunicações técnicas e financeiras;
g) comunicar imediatamente qualquer uso não autorizado da conta.

## Cláusula 13 — Obrigações da CONTRATADA

a) disponibilizar a plataforma conforme o plano e o SLA;
b) manter isolamento entre oficinas, de modo que nenhuma acesse dados de outra;
c) adotar medidas técnicas de segurança compatíveis com o estado da técnica;
d) prestar suporte pelos canais e prazos acordados;
e) comunicar manutenções programadas com antecedência;
f) tratar dados pessoais conforme a LGPD e a Política de Privacidade;
g) comunicar incidentes de segurança com risco relevante;
h) permitir a exportação dos dados da CONTRATANTE nos termos da Política de Cancelamento.

## Cláusula 14 — Privacidade e proteção de dados (LGPD)

14.1. Para os dados dos clientes finais cadastrados pela CONTRATANTE, esta é **controladora** e a
CONTRATADA é **operadora**, nos termos do art. 5º da Lei nº 13.709/2018.

14.2. A CONTRATADA tratará esses dados exclusivamente para executar este contrato e conforme
instruções da CONTRATANTE.

14.3. A CONTRATADA manterá registro das operações de tratamento e adotará as medidas do art. 46.

14.4. Em caso de incidente com risco relevante, a CONTRATADA comunicará a CONTRATANTE para que esta
cumpra suas obrigações perante os titulares e a ANPD.

14.5. Subcontratação de operadores (nuvem, armazenamento, meio de pagamento) fica desde já
autorizada, obrigando-se a CONTRATADA a exigir deles nível equivalente de proteção.

14.6. Encerrado o contrato, os dados seguem os prazos da Política de Cancelamento.

## Cláusula 15 — Confidencialidade

As partes obrigam-se a manter sigilo sobre informações a que tiverem acesso em razão deste contrato,
pelo prazo de `[INSERIR — sugestão: 5 anos]` após o término, salvo informação pública ou exigida por
autoridade competente.

## Cláusula 16 — Suporte

Prestado pelos canais, horários e critérios de `canais-suporte.md`, que integra este contrato.

## Cláusula 17 — SLA

O nível de serviço é o do documento `sla.md`, que integra este contrato após aprovação dos
indicadores pela CONTRATADA.

`[ATENÇÃO: o SLA está em versão de proposta, com todos os números marcados como sugestão. Não anexar
a contrato antes da aprovação formal.]`

## Cláusula 18 — Propriedade intelectual

18.1. O software, a marca e a documentação pertencem à CONTRATADA.

18.2. Este contrato concede licença de uso não exclusiva, intransferível e limitada à vigência, sem
cessão de direito de propriedade intelectual.

18.3. É vedada engenharia reversa, cópia, sublicenciamento ou redistribuição.

## Cláusula 19 — Dados da CONTRATANTE

19.1. Os dados inseridos pela CONTRATANTE são de sua titularidade.

19.2. A CONTRATADA não os utiliza para finalidade diversa da execução do contrato, não os
comercializa e não os compartilha com terceiros além dos operadores da Cláusula 14.5.

19.3. `[DEFINIR: a CONTRATADA pode usar dados agregados e anonimizados para estatística e melhoria
do produto? Se sim, precisa constar aqui expressamente.]`

## Cláusula 20 — Encerramento

20.1. O contrato se encerra por decurso do prazo sem renovação, por cancelamento nos termos da
Cláusula 11, ou por descumprimento não sanado em `[INSERIR PRAZO — sugestão: 15 dias]` após
notificação.

20.2. Encerrado o contrato, aplicam-se os prazos de retenção, exportação e eliminação da Política de
Cancelamento.

20.3. Subsistem as obrigações de confidencialidade, propriedade intelectual e proteção de dados.

## Cláusula 21 — Responsabilidade

21.1. A CONTRATADA não responde por decisões comerciais, orçamentos, diagnósticos ou serviços
prestados pela CONTRATANTE a seus clientes.

21.2. A CONTRATADA não responde por indisponibilidade decorrente das exceções previstas no SLA.

21.3. `[INSERIR LIMITAÇÃO DE RESPONSABILIDADE — decisão jurídica. Referência de mercado: limite ao
valor pago nos 12 meses anteriores ao evento, com exclusão de lucros cessantes.]`

## Cláusula 22 — Disposições gerais

22.1. A tolerância quanto a descumprimento não implica novação nem renúncia.

22.2. A nulidade de uma cláusula não afeta as demais.

22.3. Integram este contrato: Termos de Uso, Política de Privacidade, Política de Cancelamento, SLA
e Canais de Suporte.

22.4. Alterações somente por aditivo escrito, ressalvadas as alterações dos documentos integrantes,
comunicadas na forma dos Termos de Uso.

## Cláusula 23 — Foro

Fica eleito o foro da comarca de `[INSERIR COMARCA/UF]`, com renúncia a qualquer outro.

---

```text
Local e data: ______________________, ____ / ____ / ________


_______________________________        _______________________________
CONTRATADA                             CONTRATANTE
[INSERIR RAZÃO SOCIAL]                 [INSERIR RAZÃO SOCIAL]
[INSERIR NOME DO REPRESENTANTE]        [INSERIR NOME DO REPRESENTANTE]


Testemunhas:

1. ____________________________        2. ____________________________
Nome:                                  Nome:
CPF:                                   CPF:
```

---

## Pendências para a empresa preencher

| Campo | Cláusula |
|---|---|
| Qualificação completa da CONTRATADA | Cabeçalho |
| Valores e limites de cada plano | 2 |
| Meio de pagamento, vencimento e nota fiscal | 5 |
| Índice e periodicidade de reajuste | 6 |
| Prazo de vigência e aviso de não renovação | 7 |
| Decisão sobre fidelidade e multa rescisória | 7.3 |
| Prazo de tolerância contratual | 8.2 |
| Encargos por atraso | 8.5 |
| Regra de prorrateio na mudança de plano | 10.3 |
| Prazo de confidencialidade | 15 |
| Aprovação formal dos números do SLA | 17 |
| Uso de dados agregados e anonimizados | 19.3 |
| Prazo para sanar descumprimento | 20.1 |
| Limitação de responsabilidade | 21.3 |
| Comarca e foro | 23 |
