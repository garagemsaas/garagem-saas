# Frontend na Vercel

Projeto: `plataforma-automotiva`, no workspace `caua12`. Execute a CLI dentro de
`frontend/`. O projeto usa Vite, Node.js 24, `npm install`, `npm run build` e saída
`dist`. Esses comandos também estão explícitos em `vercel.json`.

Endereço público: https://plataforma-automotiva.vercel.app.
Previews mantêm a proteção de acesso da Vercel; a CLI autenticada pode verificá-los
com `vercel curl / --deployment <URL>`. O primeiro deployment de um projeto novo
pode ser atribuído automaticamente à produção; use `--target preview` nos
deployments seguintes de validação.

```powershell
vercel link --project plataforma-automotiva
npm install
npm run typecheck
npm run lint
npm test
npm run build
npm run test:ui
vercel --target preview
# Depois de validar o preview:
vercel --prod
```

As rotas públicas reais `/institucional`, `/ajuda` e `/acompanhar` recebem o
`index.html` para suportar navegação direta e atualização da página. `/` abre o
acesso à empresa. Os caminhos `/api/*` não são reescritos para HTML.

## API e identidade da empresa

Não há API pública HTTPS configurada neste deploy. `VITE_API_BASE_URL` permanece
sem configuração na Vercel; não foi criada URL fictícia nem proxy para localhost.
Sem essa variável, o cliente usa `/api/v1` na mesma origem, onde a Vercel não
hospeda o backend. As páginas estáticas podem ser validadas, mas login, operações
e branding carregado de `/empresa` dependem da publicação da API.

Quando a API estiver disponível, configure `VITE_API_BASE_URL` com sua origem
HTTPS real, **sem** o sufixo `/api/v1`, nos ambientes Preview e Production e
refaça os deployments: o Vite incorpora a variável durante o build. Configure
também no backend o CORS para as origens autorizadas do frontend. Valores `VITE_*`
são públicos; nunca coloque credenciais ou tokens neles. Os proxies definidos
em `vite.config.ts` só se aplicam aos servidores locais do Vite.

## GitHub

A tentativa de conexão oficial com `garagemsaas/garagem-saas` foi recusada pela
Vercel por acesso/permissão. O auto-deploy por Git não está conectado. O deploy
manual pela CLI funciona independentemente dessa integração.

Para habilitar o auto-deploy, autorize o app Vercel a acessar esse repositório na
organização GitHub e execute `vercel git connect`. Ao importar o monorepo,
configure **Root Directory = frontend** e **Production Branch = main**. O vínculo
local atual foi criado a partir de `frontend/` para publicação pela CLI.

`.vercel/`, `.env*` (exceto `.env.example`), `node_modules/` e `dist/` estão
ignorados. A CLI pode gerar `.env.local` com credenciais de desenvolvimento;
esse arquivo nunca deve ser versionado ou publicado como asset.
