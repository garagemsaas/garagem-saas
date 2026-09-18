# Fase 6 — entrega de preparação do piloto (Kauã)

## Base

Branch `feature/fase6-observabilidade-piloto`, atualizada por fast-forward com
`origin/main` em `3249cf0` (PR #14). A branch remota de fase 6 estava em `56d4ad7`;
não havia entrega adicional de fase 6 nela no momento da atualização.
Nenhum contrato, regra de negócio, autenticação, migration ou backend foi alterado.

## Entregue

- Guia “Primeiros passos” na navegação, disponível em desktop/tablet/mobile.
- Ordem explícita cliente → veículo → OS → diagnóstico → orçamento → recuperação.
- Atalhos para telas existentes, com conteúdo comercial oculto ao mecânico.
- Distinção entre disponibilizar orçamento, copiar link e enviar manualmente ao cliente.
- Aviso de notificações limitado às OS carregadas, sem afirmar que toda a oficina está sem pendências.
- Roteiro reproduzível de duas oficinas, medidas T1/T2/T3, ficha de feedback e classificação P0–P3.
- Proposta de escopo pago, exclusões e critérios de liberação, sem inventar aprovação dos sócios.
- Testes do guia por perfil, navegação, teclado, acessibilidade automática e três tamanhos de tela.

Roteiro operacional e formulários: [fase6-piloto.md](../archive/fase6-piloto.md).
O guia não grava progresso, não coleta telemetria e não envia feedback ao servidor.
Os registros do piloto são manuais; não adicionar dados de participantes ao repositório.

## Registro de inspeção (não é feedback de usuário)

| ID | Prioridade | Evidência / problema | Tratamento |
|---|---|---|---|
| UX-01 | P2 | Dependência cliente/veículo/OS só era descoberta ao tentar cadastrar | Guia contextual com sequência e atalhos |
| UX-02 | P2 | Etapas de disponibilizar e enviar orçamento podiam ser confundidas | Texto explícito no detalhe e no guia; envio continua manual |
| UX-03 | P2 | “Tudo em ordem nas OS atuais” podia sugerir cobertura integral | Texto limita conclusão aos registros carregados |

O impacto sobre usuários ainda precisa ser retestado nas oficinas A e B. Não foram
identificados/validados novos P0/P1 nesta inspeção limitada; isso não certifica ausência
de problemas críticos. Falhas que surgirem no piloto precisam de correção e regressão.

## Validação

Validações locais desta entrega: lint, typecheck e build passaram; 23 testes unitários
e 48 testes de navegador passaram, incluindo 9 novos casos do guia (3 perfis × 3 telas).
O guia passou pelas verificações automáticas axe e de ausência de overflow horizontal;
capturas desktop, tablet e mobile foram inspecionadas. Isso não substitui avaliação manual
com tecnologias assistivas nem testes com usuários reais.

## O que NÃO está concluído

- Observação de usuários, tempos reais e feedback das duas oficinas.
- Classificação dos futuros achados de campo e correção/reteste de eventuais críticos.
- Validação integrada com API real e infraestrutura de observabilidade/staging.
- Escolha final de preço, limites, suporte, termos e aprovação da versão paga.

Esses itens exigem pessoas/ambiente e decisões dos sócios. Não declarar fase 6 inteira
encerrada nem prontidão comercial somente com a aprovação deste PR.
