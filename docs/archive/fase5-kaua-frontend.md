# Fase 5 — Kauã: carteira de oportunidades

Branch `feature/fase5-dinheiro-esquecido`, sincronizada com `origin/main` em
`f2cb7a9`, incluindo a entrega do Cauã (`56d4ad7`, PR #13).

## Escopo solicitado entregue

- Tela de oportunidades pelo acesso Dinheiro Esquecido, com listagem e detalhe.
- Origem: tipo, OS, referência da versão no detalhe, valor potencial e datas reais.
  Revisão sem valor é exibida como Não avaliado, nunca zero estimado.
- Próxima ação sugerida a partir do tipo/status e do próximo contato registrado.
- Filtros remotos por tipo, status, idade e ordenação; paginação de dez registros
  usando o total do servidor, sem baixar toda a carteira.
- Registro manual de contato, canal, resultado, observação e próximo contato.
  Agendado exige data; enviar sem data limpa o agendamento, com aviso no formulário.
  O sistema não envia mensagens nem aciona WhatsApp API.
- Registro de valor recuperado explícito, separado do potencial, com confirmação
  antes de encerrar. O valor não é pré-preenchido, zero é válido e duas casas são
  validadas. O registro não representa confirmação de pagamento.
- Estado sem oportunidades, carregamento, falhas recuperáveis e novas tentativas.
- Identificação manual com contagens retornadas e horário da execução nesta sessão.
  GET não identifica oportunidades; repetir identificação após falha parcial é seguro.
- Resumo real da carteira com potencial conhecido e recuperado. A interface informa
  que os indicadores são globais da oficina e não seguem os filtros da listagem.
- Consulta do histórico de contatos e comprovante do resultado. Estados terminais
  deixam de oferecer contato e recuperação.

Usa a camada HTTP, sessão, Form/Field, Drawer e estados existentes. Nenhum backend,
contrato, migration, autenticação ou regra de elegibilidade foi alterado. Fixtures
ficam exclusivamente nos testes. Mecânicos não têm acesso visual nem carregam o módulo.

## Gravação e concorrência

Corpos usam a revisão recebida do servidor. Um 409 recarrega o detalhe, informa a
mudança e exige nova decisão do usuário; não repete contato ou recuperação. Após
gravação confirmada seguida de falha de leitura, informa que o registro foi salvo e
oferece recarga, sem manter ações sobre dados antigos. Formulários bloqueiam envio
duplicado e exibem os erros de validação pelo mecanismo existente.

## Validação

Resultado: lint, typecheck, build e 23 testes unitários aprovados; execução completa
final com 39 cenários de navegador aprovados (30 anteriores e nove novos), em 1,2 min.
Capturas desktop/mobile inspecionadas. `git diff --check` sem erros.

Na pasta frontend: `npm.cmd run lint`, `npm.cmd run typecheck`, `npm.cmd test`,
`npm.cmd run build`, `npm.cmd run test:ui -- --workers=1`.

Suíte nova em `tests/browser/recovery.spec.mjs`: origem/valor, identificação,
paginação, filtro e vazio, contato, recuperação diferente do potencial,
confirmação, encerramento, valor desconhecido, erro/retentativa, conflito sem
reenvio, papel mecânico, axe e overflow. Executada em desktop, tablet e mobile.

Limite: Docker não está disponível no terminal desta execução. As evidências de
backend/PostgreSQL/MinIO do Cauã estão em `fase5-caua.md`; não são uma nova validação
integrada deste frontend. Antes do piloto, executar a mesma operação contra o
ambiente real e confirmar persistência após novo login e isolamento de outra oficina.

## Fora dos oito itens solicitados nesta entrega

Atribuição comercial, alteração avulsa de próximo contato, encerramento por
perda/descarte, programação de revisão/reavaliação dentro da OS e relatórios
avançados têm endpoints no backend, mas não receberam formulários nesta entrega.
Próximo contato pode ser registrado junto ao contato. Isso não declara encerrada
toda a lista ampliada de pendências do documento do Cauã nem publica staging.
