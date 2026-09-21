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

## Limite conhecido do frontend: sessão não sobrevive ao recarregamento

A sessão vive apenas em memória (`let session` em `frontend/src/api.ts`). Não há localStorage,
sessionStorage nem cookie. O efeito é que **recarregar a página desloga**, em qualquer ambiente.

Isso não se resolve publicando a API. As duas saídas honestas:

- **Cookie `HttpOnly` para o refresh token**, emitido pela API. Com frontend e API em domínios
  diferentes exige `SameSite=None; Secure`, CORS com credenciais e a origem exata — nunca curinga.
  É a opção correta e mexe no backend.
- **Refresh token em `localStorage`**, que é mais simples e menos seguro: fica exposto a XSS. Foi
  deliberadamente evitado até aqui.

A decisão é de produto, não de infraestrutura, e por isso não foi tomada aqui.
