# Publicação em produção

O frontend já está publicado em `https://plataforma-automotiva.vercel.app`. A API ainda não tem
host. Este documento registra o que já foi verificado, o que falta e por quê.

## O que já está provado

A imagem de produção foi validada contra banco **vazio**, do zero:

| Verificação | Resultado |
|---|---|
| Flyway V1–V8 em base nova | 8 migrations, todas com sucesso |
| `/actuator/health` | `{"status":"UP"}`, contêiner `healthy` |
| Provisionamento por `APP_BOOTSTRAP_*` | empresa `ATIVA`, módulos `OFICINA+REVENDA`, OWNER criado |
| Login / refresh com rotação / logout | 200 / token rotacionado / 204, e 401 ao reusar o revogado |
| Sem autenticação | 401 |
| CORS da origem da Vercel | ecoa a origem exata; outra origem recebe 403 |
| Cabeçalhos | `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy`, CSP fechada |
| Fluxo OFICINA | cliente → veículo → OS → transição de status → dashboard |
| Fluxo REVENDA | estoque → `DISPONIVEL` → lead → proposta → reserva → venda com margem → dashboard |
| Upload de foto de OS | gravado no bucket S3 |
| Identidade white-label | logo aceito e persistido |

Ou seja: o que falta é **onde** rodar, não **se** roda.

## O que falta, e por que depende de uma pessoa

Nenhum provedor de hospedagem tem sessão autenticada nesta máquina — não há credencial de Railway,
Render, Fly.io, Heroku, AWS nem equivalente. Criar conta exige verificação de e-mail e, em quase
todos, confirmação por OAuth no navegador. Esse é o único passo que não pode ser automatizado.

Depois que a conta existir, o `render.yaml` na raiz publica serviço, banco e variáveis a partir
deste repositório. Outro provedor serve igualmente bem: o contrato está todo em variáveis de
ambiente e a imagem é um `Dockerfile` comum.

### Passos após criar a conta

1. Providenciar um bucket compatível com S3 e anotar endpoint, região, bucket, access key e secret.
   **Isto não é opcional**: `/actuator/health` agrega o storage, então sem S3 alcançável a
   aplicação nunca fica saudável — o que é deliberado, não um defeito.
2. Apontar o provedor para este repositório e aplicar o blueprint.
3. Preencher as variáveis marcadas como `sync: false` (storage e provisionamento).
4. No primeiro start, ligar `APP_BOOTSTRAP_ENABLED=true` com slug, nome, e-mail e uma senha forte;
   desligar logo em seguida. Repetir o mesmo slug não recria empresa nem redefine senha.
5. Conferir `GET /actuator/health` público.
6. No projeto Vercel, definir `VITE_API_BASE_URL` com a URL da API em Production e Preview, e
   refazer o deploy. Vazio faz o frontend chamar `/api/v1` na própria origem, onde não há backend.
7. Conferir que `CORS_ALLOWED_ORIGINS` contém exatamente a origem do frontend.

## Variáveis que a aplicação exige

Sem valor padrão — a aplicação não sobe sem elas: `DATABASE_PASSWORD`, `JWT_SECRET`,
`S3_ACCESS_KEY`, `S3_SECRET_KEY`.

Com padrão, mas que precisam de valor real em produção: `DATABASE_URL`, `DATABASE_USER`,
`S3_ENDPOINT`, `S3_BUCKET`, `S3_REGION`, `CORS_ALLOWED_ORIGINS`, `PUBLIC_BASE_URL`,
`API_DOCS_ENABLED=false`, `TRUST_PROXY=true`, `FORWARD_HEADERS_STRATEGY=framework`.

`TRUST_PROXY` e `FORWARD_HEADERS_STRATEGY` andam juntos: ligados isoladamente, ou o IP auditado
fica errado, ou qualquer cliente forja `X-Forwarded-For` e escapa do teto de requisições.

## Sessão que sobrevive ao recarregamento

O access token continua apenas em memória e morre com a página. Quem atravessa o F5 é o refresh,
num cookie `HttpOnly; Secure; SameSite=None` com `Path=/api/v1/auth` — ilegível por JavaScript e
restrito ao único caminho que o usa. Ao carregar, o frontend pede uma rotação antes de decidir
mostrar a tela de entrada.

`localStorage` foi descartado de propósito: resolveria o recarregamento entregando o refresh a
qualquer XSS. Nenhuma defesa da Fase 8 mudou — rotação de uso único, revogação da família inteira
ao detectar reuso e 401 para token revogado seguem valendo, cobertos por `SessaoPersistenteIT`.

Com frontend e API em domínios diferentes, `REFRESH_COOKIE_SAME_SITE=None` e
`REFRESH_COOKIE_SECURE=true` são obrigatórios, e o CORS precisa de credenciais com a origem exata.

## Ambiente temporário de demonstração

Enquanto não há host contratado, `scripts/demo/publicar.sh` publica a stack local por um túnel HTTPS
da Cloudflare e aponta o frontend da Vercel para ele:

```bash
bash scripts/demo/publicar.sh
```

O script sobe Postgres, MinIO e API por Docker, espera a API ficar saudável, abre o túnel apenas
para a porta da API, confere a saúde pela internet e — se o endereço tiver mudado — atualiza
`VITE_API_BASE_URL` na Vercel e refaz o deploy de produção. Os segredos ficam em
`.tools/prod/producao.env`, fora do Git.

Só a API atravessa. Postgres e MinIO continuam presos a `127.0.0.1` pelo `compose.demo.yml`, e o
túnel mapeia um endereço só. Uploads passam pela API, então o navegador nunca fala com o MinIO.

**Isto não é produção.** O endereço é sorteado a cada execução, só responde com esta máquina ligada
e não tem garantia nenhuma de disponibilidade. Serve para teste, homologação e apresentação.
