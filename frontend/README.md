# Frontend Garagem SaaS — Fase 1

React + TypeScript + Vite. Interface integrada à API REST real; layout aprovado preservado. A fonte operacional é o backend, com PostgreSQL e MinIO privado.

## Executar

```sh
npm ci
npm run dev -- --host 127.0.0.1 --port 5173
```

API em http://127.0.0.1:8080, encaminhada pelo proxy `/api` de Vite (dev/preview). Login exige oficina provisionada. Papel/usuário vêm da sessão; tokens somente em memória e novo login após reload. Registros continuam no banco.

## Verificar

```sh
npm run lint
npm run typecheck
npm test
npm run build
```

Node 22.18+ ou 24+ para executar os testes TypeScript no Node. Roteiros `tests/e2e-real.mjs` e `tests/public-real.mjs` exigem API, PostgreSQL, MinIO, Playwright/Chromium e duas oficinas de teste novas. Executar separadamente conforme [fixture e reprodução](../docs/fase1-finalizacao.md#relatórios-e-reprodução). Não usam mocks de rede; criam registros reais no ambiente de teste.

## Integração

- `api.ts`: Bearer, refresh compartilhado, descarte de respostas de sessão antiga, paginação e mapeamento de diagnósticos.
- `App.tsx` / `forms.tsx`: autenticação, cadastros, equipe, listas e abertura de OS.
- `OrderDetail.tsx`: ações reais, revisões, conflitos e histórico do backend.
- `PrivatePhoto.tsx`: bytes autenticados em Blob URL temporária, revogada ao desmontar.
- `PublicOrder.tsx`: `/acompanhar#token`, resumo público e decisão integral com confirmação.

Listas percorrem páginas de 100 e exibem dez por página; busca global é local sobre os registros carregados. Não há filtro de status/data/responsável na API. “Atualizar dados” busca o estado atual; não há tempo real. Links não podem ser recuperados após a emissão: copie imediatamente; a interface mantém o último link apenas no detalhe carregado.

O gerador `seed()` permanece como fixture histórica, sem uso no App integrado. Referências visuais preexistentes a módulos futuros não implementam Fase 2.

[Contratos REST](../docs/api-contracts.md) · [Finalização](../docs/fase1-finalizacao.md) · [README geral](../README.md)
