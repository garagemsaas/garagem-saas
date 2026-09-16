# Fase 3 — Kauã conecta o frontend

Branch: `feature/fase3-api-integration`. Base: `origin/main` em `6f8fcab`.
A branch remota já continha a entrega de API de Cauã, integrada pelo PR #9.
Esta continuação avança a branch para a main e acrescenta a entrega do frontend.

## Checklist implementado

- [x] Login via `POST /api/v1/auth/login`, sem credenciais preenchidas.
- [x] Sessão em memória, Bearer, refresh compartilhado, logout e retorno ao login na expiração.
- [x] Clientes: listagem, busca por nome/telefone/e-mail, criação e edição com revisão.
- [x] Veículos: listagem, busca por placa/marca/modelo, vínculo com cliente, criação e edição.
- [x] OS: listagem, busca, abertura e carregamento do detalhe real com suas relações.
- [x] Status: envio da revisão, confirmação, atualização pelo servidor e recuperação de conflito 409.
- [x] Dados de produção vêm da API; fixtures existem somente nos testes.
- [x] Erros de conexão, HTTP, validação por campo, conflitos e resposta JSON inválida.
- [x] Carregamentos, bloqueio de envio duplicado, estados vazios e novas tentativas.

## Mudanças desta entrega

A integração de gravações já existente foi preservada. Listagens agora consultam
páginas de 10 registros, usam `total` do servidor e enviam `busca` com URLSearchParams.
Clientes e veículos relacionados às OS são consultados por ID. O detalhe também
abre registros encontrados fora da página atual. Os corpos de cliente/veículo
enviam apenas os campos de entrada do contrato, sem IDs gerados pelo formulário.

A busca tem debounce de 300 ms e ignora respostas de consultas anteriores. A busca
global consulta três endpoints e mostra até 10 resultados por categoria, orientando
a refinar a consulta ou usar as listagens. Alterações recarregam a página no servidor.

Os indicadores usam `/dashboard`. Prioridades e entradas são identificadas como
recortes das 10 OS recentes. Orçamentos pendentes são consultados ao abrir o painel,
sem buscar versões de toda a base no login. Seletores de formulários ainda percorrem
páginas de 100 sob demanda; equipe para responsáveis e orçamentos pendentes também
percorrem páginas. Autocomplete remoto é uma evolução possível desses seletores.

Uma resposta 401 atrasada reutiliza o access token já renovado antes de tentar outro
refresh. Respostas de sessão anterior são descartadas inclusive após ler o JSON.
Tokens não são persistidos: recarregar a aba exige novo login.

## Validação

- `npm run lint`
- `npm run typecheck` / `npm run build`
- `npm test`: 23 testes unitários.
- `npm run test:ui`: 18 cenários (6 em cada viewport: desktop, tablet e celular).
- `git diff --check`

Os testes de navegador controlam o contrato REST apenas na suíte. Cobrem cadastro,
edição, paginação com 21 clientes, busca por e-mail fora da página, busca global,
erros, expiração, conflito de revisão, OS até PRONTO, fotos, aprovação pública,
permissões e acessibilidade.

**Limite de validação:** Docker não estava disponível no terminal desta execução.
Esta entrega não comprova persistência contra PostgreSQL/MinIO reais. Antes do aceite
integrado, executar o ambiente documentado na raiz e conferir: login → cadastrar e
editar cliente → cadastrar e editar veículo → abrir OS → avançar status → atualizar
os dados → sair e entrar → confirmar persistência. Repetir com mecânico e outra
oficina para validar permissões e isolamento no ambiente real.

## Subir a branch e abrir o PR

Na raiz `C:\Projetos\garagem-saas`, depois de revisar:

```powershell
git status
git add README.md docs/fase3-kaua-frontend.md frontend/README.md frontend/src/App.tsx frontend/src/Dashboard.tsx frontend/src/Workspace.tsx frontend/src/api.ts frontend/tests/api.test.mjs frontend/tests/browser/frontend.spec.mjs
git commit -m "feat: integra frontend aos contratos da fase 3"
git push -u origin feature/fase3-api-integration
```

Abra um **novo** PR com base `main` e compare `feature/fase3-api-integration`:
[abrir comparação no GitHub](https://github.com/garagemsaas/garagem-saas/compare/main...feature/fase3-api-integration?expand=1).
Não reutilize o PR #9, já integrado. Não é necessário force push.

Título sugerido: `feat: integra frontend aos contratos da fase 3`.

Descrição sugerida:

> Conecta as listagens de clientes, veículos e OS à paginação e busca da API, usa os
> indicadores oficiais do dashboard e busca registros fora da página atual. Preserva
> login, sessão, cadastros e alterações de status reais, aprimorando concorrência de
> refresh, erros e carregamentos. Sem dados fictícios no frontend de produção.
>
> Validação: lint, TypeScript, build, 23 testes unitários e 18 cenários de navegador.
> Aceite com PostgreSQL e MinIO reais pendente; Docker indisponível nesta execução.

Após CI e revisão do colega, ele pode aceitar e fazer o merge. Nenhum commit,
push, PR ou merge foi executado por esta entrega local.

No PowerShell desta máquina, `npm` resolvia para um shim quebrado em AppData.
Os checks usaram `& 'C:\Program Files\nodejs\npm.cmd' run ...`, sem alterar a
instalação global. Se ocorrer o mesmo erro, use esse caminho completo.
