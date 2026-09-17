# Fase 6 — roteiro de usabilidade e decisão comercial

Status: preparação entregue; sessões com usuários NÃO realizadas por esta entrega.
Não há tempos reais, aprovação comercial ou depoimentos coletados. Testes de navegador
com API simulada verificam interface, não substituem piloto com API e oficinas reais.

## Responsáveis e ordem

1. Cauã valida ambiente integrado, contas separadas por oficina, permissões, logs sem
   segredos, backup/restauração e tratamento de falhas. Não usar produção como laboratório.
2. Kauã combina autorização com a oficina A e prepara os dados fictícios do roteiro.
3. Kauã observa sem orientar; Cauã acompanha falhas técnicas sem alterar o sistema durante a medição.
4. Ambos classificam achados. Kauã corrige interface; Cauã corrige API/infra. Combinar contrato antes de mudar ambos.
5. Repetir tarefas que falharam na A, preservando o registro original e identificando a nova versão.
6. Repetir roteiro completo na oficina B, em conta independente e com participante novo.
7. Ambos aprovam ou adiam a versão paga pelos critérios abaixo. Depois, proposta comercial.

## Preparação de cada sessão (30–45 minutos sugeridos)

- [ ] Registrar versão/commit, data, ambiente, navegador, dispositivo e qualidade percebida da conexão.
- [ ] Usar identificadores A/B e P01/P02, sem nome, telefone ou placa reais no relatório.
- [ ] Explicar objetivo, pedir autorização para anotações; gravação somente se autorizada separadamente.
- [ ] Criar cliente e veículo fictícios distintos em cada oficina e uma OS apta ao orçamento.
- [ ] Validar conta OWNER/ATENDENTE para as três tarefas e conta MECANICO para revisão de permissões.
- [ ] Ter um destinatário de teste autorizado para o envio manual do link; não enviar a cliente real.
- [ ] Confirmar ausência de dados da outra oficina. Se houver mistura, interromper imediatamente.
- [ ] Usar cronômetro externo do observador, sem exigir que o participante opere o cronômetro.
- [ ] Começar na visão geral, autenticado, sem painéis abertos e com a página carregada.
- [ ] Primeira rodada sem guia; depois, apresentar “Primeiros passos” e repetir com outro exemplo.

## Tarefas e medição reproduzível

Iniciar ao terminar de ler o objetivo. Parar somente no critério de conclusão. Cronometrar
o tempo total incluindo espera da rede; anotar separadamente interrupções externas.
Não apagar tentativa com erro ou trocar seu tempo pelo tempo do reteste.

| ID | Diga ao participante | Pré-condição | Conclusão observável | Meta proposta, não validada |
|---|---|---|---|---|
| T1 | “Abra uma OS para o veículo de teste com o relato fornecido.” | Cliente e veículo cadastrados; responsável disponível | Servidor confirma criação e participante vê número da OS | até 120 s sem ajuda |
| T2 | “Encontre o cadastro do cliente de teste e confira o contato.” | Nome fornecido; começar novamente na visão geral | Perfil correto aberto e contato localizado | até 30 s sem ajuda |
| T3 | “Prepare e envie este orçamento ao destinatário de teste.” | OS em estado permitido; itens/valores fornecidos | Versão disponibilizada, link enviado manualmente e recebido no destino de teste | até 180 s sem ajuda |

Para T3 registrar também dois marcos: orçamento disponibilizado e link copiado. Copiar
não significa enviar; abrir WhatsApp não significa entregar. Se não houver canal de teste,
marcar envio como NÃO TESTADO, sem atribuir sucesso à tarefa completa.
Onboarding completo (cliente → veículo → OS) é rodada adicional, não misturar seu tempo com T1.
Após 5 minutos sem concluir, registrar abandono/limite excedido e oferecer ajuda; não excluir da análise.

## Observação e perguntas neutras

Não dar o nome do botão antes de a pessoa procurá-lo. Se pedir ajuda, perguntar “O que
você esperava encontrar aqui?”. Registrar cada intervenção, retorno, clique errado e
texto interpretado de outra forma. Distinguir relato do participante de hipótese do observador.

Após cada tarefa: “O que foi mais difícil?”, “O que você achou que esse botão faria?”,
“De 1 (muito difícil) a 5 (muito fácil), como foi concluir?”. Ao final: “O que impediria
usar amanhã?”, “Qual função é indispensável?”, “O que você esperava receber ao contratar?”.
Não induzir resposta nem prometer funcionalidades fora da versão.

## Registro — copiar por tentativa

```text
Sessão: A/B __  Participante: P__  Perfil: __  Observador: __
Data: __  Commit: __  Ambiente/dispositivo/navegador: __
Rodada: inicial / pós-guia / reteste  Tarefa: T1/T2/T3
Início: __  Fim: __  Total (segundos): __
T3 — disponibilizado em: __ s; copiado em: __ s; recebido em: __ s
Resultado: sucesso sem ajuda / sucesso com ajuda / falha / abandono / não testado
Ajuda fornecida e quantidade: __
Passos errados, voltas e confusões: __
Espera de rede / interrupções: __
Facilidade (1–5): __
Fala anonimizada do participante: __
Hipótese do observador (separada da fala): __
Achados relacionados: UX-__
```

Consolidar por tarefa/oficina/rodada: tentativas, sucessos sem ajuda, sucessos com ajuda,
falhas, abandonos e não testados. Calcular mediana dos tempos das conclusões e informar
o número usado; manter falhas/abandonos ao lado para não mascarar dificuldade. Não misturar
baseline e reteste. Duas oficinas fornecem indícios qualitativos, não validação estatística.

## Priorização e correção

| Prioridade | Critério | Conduta |
|---|---|---|
| P0 crítico | Mistura de oficinas, acesso indevido, perda de dados ou decisão/valor incorreto | Parar piloto, preservar evidência sem segredos, corrigir e testar antes de retomar |
| P1 alto | Tarefa essencial bloqueada sem alternativa confiável | Bloqueia aceite pago; atribuir responsável e retestar |
| P2 médio | Confusão ou atraso com alternativa segura | Priorizar por repetição e impacto; documentar alternativa |
| P3 baixo | Ajuste visual/textual sem impacto operacional | Backlog após bloqueadores |

```text
ID: UX-__  Origem: inspeção técnica / oficina A / oficina B
Título: __  Prioridade e justificativa: __  Frequência: __
Passos para reproduzir: __
Esperado: __  Observado: __  Evidência anonimizada: __
Responsável: Kauã / Cauã  Prazo combinado: __
Status: aberto / em correção / aguardando reteste / validado
Correção/commit: __  Reteste e resultado: __
```

Não marcar problema resolvido só por commit: reproduzir antes, testar depois, confirmar
critério e rodar regressões. Novos achados críticos dependem das sessões: não presumir ausência.
Não guardar relatórios preenchidos, links públicos, tokens ou dados pessoais no Git.
Guardar os registros em espaço restrito acordado com a oficina e combinar prazo de exclusão.

## Segunda oficina — condição de passagem

- [ ] Usar tenant, conta, dados fictícios e participante diferentes dos da oficina A.
- [ ] Executar T1/T2/T3, onboarding e consulta/aprovação no celular do destinatário de teste.
- [ ] Repetir critérios e cronometragem; registrar versão caso haja correções entre sessões.
- [ ] Verificar falha de rede, recusa do orçamento e recuperação sem duplicar ações.
- [ ] Confirmar perfis de acesso e isolamento com Cauã no ambiente integrado.
- [ ] Comparar achados e tempos A/B sem atribuir toda diferença à interface.
- [ ] Revalidar todos os P0/P1 de ambas as oficinas.

## Proposta de escopo pago V1 — sujeita à aprovação dos sócios

Incluído proposto: clientes e veículos, equipe/perfis existentes, ciclo de OS, checklist,
fotos, diagnóstico, versões de orçamento, disponibilização e decisão pelo link público,
painel operacional e carteira de Dinheiro Esquecido com origem, contato manual e recuperação.
Recuperação registrada não equivale a conciliação financeira ou comprovante de pagamento.

Fora: WhatsApp API/envios automáticos, agenda operacional, gestão de vagas/pátio, estoque,
financeiro/fiscal/cobrança, IA e notificações em tempo real. Não vender itens “Em breve”.
Quantidade de usuários/oficinas, armazenamento, retenção de fotos, preço, suporte e SLA
precisam de decisão explícita; não foram definidos por esta implementação.

### Checklist de liberação comercial

- [ ] Sessões A e B documentadas; três tarefas essenciais concluídas sem bloqueio.
- [ ] Metas de tempo avaliadas; desvios corrigidos ou aceitos explicitamente com justificativa.
- [ ] Nenhum P0/P1 aberto; P2 com responsável e plano conhecido.
- [ ] API real, isolamento, autorização, links/expiração e concorrência validados por Cauã.
- [ ] Ambiente externo seguro, backups com restauração testada, logs/alertas e suporte operacionais.
- [ ] Política de dados, termos, limites, preço e responsabilidades revisados pelos responsáveis.
- [ ] Escopo e exclusões aprovados pelos dois sócios; oficina entende envio manual e valor estimado.

Decisão: **PENDENTE**. Kauã: __ / data __. Cauã: __ / data __.
Resultado: liberar / adiar. Evidências: __. Pendências e data de revisão: __.
