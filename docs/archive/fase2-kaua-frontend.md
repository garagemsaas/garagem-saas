# Fase 2 — Frontend de Kauã

Entrega local em 15/09/2026, sobre `feature/fase2-api-contracts-security`.
Os itens abaixo descrevem o código implementado. A validação integrada com o
backend real ainda depende do ambiente descrito ao final.

## Estrutura

- [x] Cliente HTTP centralizado: uma única implementação, timeout, JSON/multipart e erros RFC 7807.
- [x] Gerenciamento de sessão: memória, refresh concorrente, logout e limpeza após expiração.
- [x] Proteção das páginas autenticadas e ações visíveis conforme OWNER/ATENDENTE/MECANICO.
- [x] Estados de carregamento de dados, detalhe, formulários e página pública.
- [x] Estados de erro com nova tentativa e mensagens em português.
- [x] Estados vazios nas listas, detalhes, timeline e pré-requisitos de cadastro.
- [x] Componentes reutilizáveis de formulário com labels, erros por campo e confirmação.
- [x] Componentes de status com texto e ícone, sem depender só de cor.
- [x] Componentes de lista/tabela: busca, paginação, região rolável e layout móvel.
- [x] Componente reutilizável de timeline cronológica.

## Telas

- [x] Login real via `/auth/login`.
- [x] Lista, cadastro e edição de clientes com revisão.
- [x] Lista, cadastro e edição de veículos com proprietário e revisão.
- [x] Lista, abertura e detalhe da ordem de serviço.
- [x] Checklist de entrada único, com confirmação.
- [x] Diagnóstico mapeado para VERDE/AMARELO/VERMELHO.
- [x] Orçamento com itens, versões, total do servidor e disponibilização confirmada.
- [x] Histórico da OS com eventos retornados pela API.
- [x] Fotos PNG/JPEG, validação de tamanho/resolução, upload multipart e download autenticado.
- [x] Página pública: link válido/inválido/expirado, resumo, orçamento e aprovação/recusa confirmada.

## Qualidade visual

- [x] Desktop: fluxo exercitado em 1440×1000.
- [x] Tablet: fluxo exercitado em 768×1024.
- [x] Celular: fluxo exercitado em 390×844; orçamento em cartões legíveis.
- [x] Formulários com labels, autocomplete, erros associados e foco no campo inválido.
- [x] Mensagens de erro em português, inclusive falha de conexão e arquivo inválido.
- [x] Confirmações para registros definitivos, disponibilização, status, revogação e decisão pública.
- [x] Dados demonstrativos removidos de `src/model.ts`; nenhuma fixture carregada em produção.
- [x] Ícones de interface exclusivamente Lucide; sprites e logos de template sem uso removidos.

## Correções de integração

A branch continha duas implementações HTTP incompatíveis, chamadas a métodos que
não existiam na implementação efetivamente importada e placeholders JSX inválidos.
Esses trechos foram removidos. O detalhe da OS já persiste suas operações: seu
callback agora apenas atualiza a interface, evitando duplicação de POSTs e revisões.
Somente ausência de checklist (404) vira estado vazio; outros erros permanecem visíveis.

Links emitidos usam a origem do frontend. O token de emissão única é preservado na
interface antes da atualização do histórico. Se uma escrita termina e a releitura
falha, a interface informa que a alteração foi salva e pede atualização, evitando
uma nova gravação automática.

## Evidências executadas

- `npm run lint`: sem avisos.
- `npm run typecheck`: aprovado.
- `npm test`: 18 testes aprovados.
- `npm run build`: aprovado, bundle de produção gerado.
- `npm run test:ui`: 12 testes aprovados, quatro cenários em três resoluções.
- Axe: nenhuma violação A/AA detectada nas telas e formulários examinados pelos testes.
- Inspeção visual das capturas do histórico em desktop e celular.

O fluxo de navegador cobre cliente e veículo (criação/edição), OS, checklist,
diagnóstico, orçamento, link, foto, timeline, aprovação pública e conclusão.
Outros cenários cobrem erro recuperável, carregamento, erro por campo, busca sem
resultado, sessão expirada, novo login após reload, link inválido e restrições do mecânico.
As capturas e traces são artefatos locais em `frontend/test-results/`, ignorados pelo Git.

## Limite da validação e aceite integrado

Os testes de navegador usam respostas REST controladas dentro da suíte. Não houve
validação de persistência desta entrega com PostgreSQL/MinIO reais: o Docker Engine
local não estava acessível e a tentativa `docker desktop start` não o disponibilizou.
Build e testes do frontend não substituem esse aceite integrado.

Para o aceite final, disponibilizar o backend da branch com banco/storage e uma
oficina provisionada; executar o frontend e repetir o fluxo operacional com as
credenciais reais de teste. Conferir também recusa, revisão concorrente e revogação
de link contra o servidor real. Nenhuma credencial ou dado de teste foi incluído
no código de produção.

Sessões permanecem em memória; reload exige login. Listas e dashboard mantêm a
estratégia local da Fase 1, percorrendo páginas da API. Módulos anunciados como
futuros (agenda, pátio, peças e recuperação comercial) não integram esta lista.

A pasta antiga `garagem-saas-git/` foi preservada. Alterações estão locais, sem commit
ou push. Os arquivos rastreados removidos podem ser recuperados no histórico do Git.
