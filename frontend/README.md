# Frontend Garagem SaaS — Fase 2 (Kauã)

React, TypeScript e Vite, integrado aos contratos REST documentados em [api-v1.md](../docs/api-v1.md).
Não existe modo demonstração nem fallback com registros fictícios.

## Executar

Requer Node 22.18+ ou 24+:

```powershell
cd C:\Projetos\garagem-saas\frontend
npm ci
npm run dev
```

Abra o endereço indicado pelo Vite. O proxy encaminha `/api` para
`http://127.0.0.1:8080`. O login precisa de uma oficina provisionada e credenciais válidas no backend.

`VITE_API_BASE_URL` é opcional: vazio usa a mesma origem e o proxy local.
Outra origem exige CORS configurado no backend. Em produção, configure o servidor
para encaminhar `/api` à API e servir `index.html` também em `/acompanhar`.
Nunca coloque segredos em variáveis `VITE_*`.

## Verificar

```powershell
npm run lint
npm run typecheck
npm test
npm run build
npx playwright install chromium
npm run test:ui
```

- Testes unitários: sessão, renovação concorrente, isolamento de respostas antigas,
  erros RFC 7807, multipart, paginação e cálculos do dashboard.
- Testes de navegador: cenários controlados do contrato REST, com Chromium em
  1440×1000, 768×1024 e 390×844. Exercitam os cadastros, OS até conclusão,
  fotos, aprovação pública, estados de erro/vazio/carregamento, permissões e
  verificações axe de acessibilidade WCAG A/AA.
- Esses testes usam API simulada **somente dentro da suíte de testes**; não comprovam
  persistência no PostgreSQL/MinIO.
- Os roteiros legados `tests/e2e-real.mjs` e `tests/public-real.mjs` pertencem à
  Fase 1 e precisam de ambiente/fixtures reais e atualização dos seletores para
  as novas confirmações. Não fazem parte de `npm run test:ui`.

O workflow `.github/workflows/frontend.yml` executa lint, testes, build e testes
de navegador em PRs que alteram o frontend.

## Organização

| Arquivo | Responsabilidade |
|---|---|
| `src/api.ts` | Cliente HTTP único: Bearer, refresh compartilhado, timeout, JSON, upload, fotos privadas e erros estruturados |
| `src/api/types.ts` | DTOs e tipos do contrato, incluindo VERDE/AMARELO/VERMELHO |
| `src/App.tsx` | Login, proteção das telas, navegação, clientes, veículos e listagem/abertura de OS |
| `src/forms.tsx` | Formulário com envio bloqueado, confirmação, erros por campo e formulários de cadastro |
| `src/ui.tsx` | Campos acessíveis, painel modal, status, busca, paginação e estados vazios |
| `src/PageState.tsx` | Carregamento, erro recuperável e proteção contra falhas de renderização |
| `src/TableRegion.tsx` | Região de tabela com rolagem por teclado e toque |
| `src/Timeline.tsx` | Histórico cronológico reutilizável com estado vazio |
| `src/OrderDetail.tsx` | Checklist, diagnóstico, orçamento versionado, fotos, responsável, status e links |
| `src/PublicOrder.tsx` | Acompanhamento público e decisão integral do cliente com confirmação |
| `src/icons.tsx` | Única fonte de ícones: lucide-react |
| `src/design-system.css` | Responsividade, contraste e componentes visuais |

## Comportamentos importantes

A sessão fica somente em memória. Recarregar ou fechar a aba exige novo login.
Ao receber 401, uma única renovação atende às chamadas concorrentes; se recusada,
a interface limpa a sessão e volta ao login. Logout revoga o refresh quando a API
está acessível. Papéis limitam as ações visíveis, e a API continua responsável por
autorizar cada operação e isolar as oficinas.

Cada ação da OS escreve uma única vez e só usa o resultado confirmado pela API.
Checklist e versões de orçamento pedem confirmação e preservam seus registros.
Mudança de status, disponibilização do orçamento, revogação do link e decisão
pública também têm confirmação explícita. O link público usa a origem do frontend
e o token no fragmento: `/acompanhar#token`. Copie ao emitir; a API não lista tokens
já emitidos. A página pública exibe somente o resumo permitido pelo contrato,
sem fotos, dados pessoais ou timeline interna.

Listagens carregam todas as páginas de 100 registros e exibem dez por página;
buscas são locais. O dashboard ainda calcula indicadores sobre esses registros.
Paginação/filtros no servidor e uso do endpoint agregado `/dashboard` são uma
evolução de escala, não implementada nesta entrega.

Veja o [checklist e as evidências da entrega](../docs/fase2-kaua-frontend.md).
