> Atualização Fase 9: o tenant representa Empresa. Billing e cotas históricas estão desativados e fora do core. OWNER edita somente a própria identidade; situação e módulos são administrativos. Veja [modelo atualizado](/docs/architecture/empresa-white-label.md) e [provisionamento](/docs/operations/provisionamento-empresa.md). Referências a planos nas fases anteriores são históricas.

# Auditoria de segurança e prontidão para produção

Auditoria executada em **18/09/2026**, na branch `feature/fase7-preparacao-vendas`, contra o código
real, a stack Docker isolada (`garagem-audit`) com PostgreSQL 17.11 e MinIO, e a suíte automatizada.

Escrito para quem vai revisar segurança e decidir sobre piloto e produção.

**Método:** inventário de endpoints a partir dos controllers; sondagem HTTP anônima e autenticada
contra a stack real; dois tenants e os três papéis; leitura do código em cada ponto citado; e testes
de regressão em `Fase8SegurancaIT`. Onde não foi possível validar, está escrito **NÃO VALIDADO** com
o motivo.

**Resultado:** nenhuma vulnerabilidade **CRÍTICA**. Duas de severidade **ALTA**, ambas corrigidas e
cobertas por teste. O isolamento entre oficinas — a garantia mais importante do produto — resistiu a
todos os vetores testados.

---

## 1. Superfície acessível sem autenticação

Sondagem direta contra a stack real. **Nada foi considerado protegido por não ter link no frontend.**

| Método | Rota | Componente | Auth | Papel | Tenant | Devolve | Público? | Risco |
|---|---|---|---|---|---|---|---|---|
| POST | `/api/v1/auth/login` | `AuthController` | ❌ | — | resolve | sessão | **Sim, necessário** | Força bruta — F-01 |
| POST | `/api/v1/auth/refresh` | `AuthController` | ❌ | — | do token | sessão | **Sim, necessário** | Adivinhação — F-01 |
| POST | `/api/v1/auth/logout` | `AuthController` | ❌ | — | do token | 204 | **Sim, necessário** | Nenhum: só revoga |
| GET | `/api/v1/publico/{token}` | `PublicoController` | ❌ | — | do link | status, veículo, orçamento | **Sim, é o produto** | Token de 256 bits |
| POST | `/api/v1/publico/{token}/decisao` | `PublicoController` | ❌ | — | do link | decisão | **Sim, é o produto** | Grava IP — F-06 |
| POST | `/api/v1/webhooks/pagamento` | `WebhookPagamentoController` | ❌ (HMAC) | — | do `provider_subscription_id` | status | **Sim, gateway não tem sessão** | Coberto |
| GET | `/actuator/health` (+`/readiness`,`/liveness`) | Actuator | ❌ | — | — | `{"status":"UP"}` | Sim | Nenhum: sem detalhe |
| GET | `/v3/api-docs`, `/swagger-ui*` | springdoc | ❌ | — | — | contrato completo | **Discutível** | F-05 |
| GET | `/institucional`, `/ajuda`, `/materiais/*` | frontend estático | ❌ | — | — | páginas e material comercial | Sim, é marketing | Nenhum |

**Endpoints públicos indevidos encontrados: nenhum.** Toda a API de negócio exige sessão.

```text
$ for p in /api/v1/clientes /api/v1/veiculos /api/v1/ordens-servico /api/v1/usuarios \
           /api/v1/dashboard /api/v1/assinatura /api/v1/dinheiro-esquecido/resumo; do curl -s -o /dev/null -w "$p %{http_code}\n" $A$p; done
/api/v1/clientes 401      /api/v1/usuarios 401       /api/v1/dinheiro-esquecido/resumo 401
/api/v1/veiculos 401      /api/v1/dashboard 401
/api/v1/ordens-servico 401 /api/v1/assinatura 401
```

**Deny by default confirmado.** `anyRequest().authenticated()` em
[SecurityConfig.java:109](../backend/src/main/java/br/com/garagem/config/SecurityConfig.java#L109)
faz qualquer caminho fora da allowlist exigir sessão — inclusive os que um scanner tenta primeiro:

```text
/.env 401   /.git/config 401   /application.yml 401   /BOOT-INF/classes/application.yml 401
/backup.sql 401   /actuator/env 401   /actuator/heapdump 401   /actuator/beans 401
```

**`permitAll()` aparece uma única vez**, sobre a lista acima. Cada entrada foi verificada
individualmente e tem justificativa funcional.

---

## 2. Achados

### F-01 · Ausência de teto de requisições — **ALTO** · ✅ CORRIGIDO

**Arquivo:** `config/SecurityConfig.java`, `auth/api/AuthController.java` (antes da correção, nenhum).

**Evidência (antes):** varredura por `ratelimit|bucket4j|throttl|resilience4j|429` em todo o backend
retornou **zero ocorrências**. Sondagem confirmou:

```text
20 tentativas de senha errada -> 401 401 401 ... (20x)
429 recebidos: 0 | tempo total: 8314ms | login correto depois: HTTP 200
```

**Cenário de exploração:** duplo. (a) Enxame de credenciais contra `/auth/login`, sem bloqueio nem
atraso progressivo — a única fricção era o custo do BCrypt. (b) **Negação de serviço**: cada
tentativa anônima consome ~415 ms de CPU em BCrypt de custo 12. Algumas centenas de requisições
simultâneas saturam o processador e derrubam a API para os clientes pagantes. A proteção de senha e
a proteção de disponibilidade são, aqui, o mesmo controle.

**Impacto:** comprometimento de conta por adivinhação; indisponibilidade a custo baixo para o
atacante.

**Correção:** `shared/seguranca/LimiteRequisicoes.java` (janela deslizante por chave) e
`LimiteRequisicoesFilter.java`, aplicados a login, refresh, link público e webhook, com tetos
independentes. Recusa devolve **429** no mesmo formato RFC 7807 do resto da API, com `Retry-After`.
Login bem-sucedido zera o contador da origem, para quem acertou não carregar as falhas de terceiros
atrás do mesmo IP de saída.

**Evidência (depois), na stack real:**

```text
14 tentativas -> 401 401 401 401 401 401 401 401 401 401 429 429 429 429
HTTP/1.1 429 | Retry-After: 54
{"code":"RATE_LIMITED","detail":"Muitas tentativas. Aguarde 54 segundo(s)..."}
```

**Teste:** `Fase8SegurancaIT#tetoDeLoginBarraForcaBrutaEDevolveRetryAfter` — cobre também que o teto
não vira bypass (a senha certa continua recusada durante o bloqueio) e que outra origem não é punida.

**Risco residual:** contagem **em memória**. Com duas instâncias atrás de um balanceador, cada uma
conta separadamente e o teto efetivo dobra. Escalar horizontalmente exige um contador compartilhado.
Documentado na própria classe. Além disso, um ataque distribuído por muitos IPs não é contido por
teto por origem — mitigar isso é papel da borda (WAF/CDN), fora do escopo da aplicação.

---

### F-02 · Não havia como revogar o acesso de um usuário — **ALTO** · ✅ CORRIGIDO

**Arquivo:** `usuario/api/UsuarioController.java`.

**Evidência (antes):** o controller tinha apenas `GET` e `POST`. Sondagem:

```text
PUT    /api/v1/usuarios/{id}  ->  404
PATCH  /api/v1/usuarios/{id}  ->  404
DELETE /api/v1/usuarios/{id}  ->  404
```

A coluna `usuario.ativo` existia e era exigida no login e em `TenantRequestFilter`, mas **nenhum
caminho da aplicação conseguia alterá-la**.

**Cenário de exploração:** um funcionário desligado mantém acesso completo à oficina. O refresh token
vale 7 dias e **cada renovação emite outro com prazo cheio**, então o acesso se sustenta
indefinidamente. A única forma de cortar era `UPDATE` manual no banco de produção.

**Impacto:** não existia controle de revogação de acesso — um requisito básico. Contradizia
diretamente a cláusula 16.2 dos Termos de Uso, que atribui à oficina o dever de "desativar usuários
que deixem a oficina".

**Correção:** `PUT /api/v1/usuarios/{id}/situacao`, exclusivo do OWNER. Ao desativar, revoga os
refresh tokens do usuário na mesma transação. O efeito é **imediato** mesmo com access token válido,
porque `TenantRequestFilter` confere `u.ativo=true` no banco a cada requisição. Três travas:

- ninguém desativa a si mesmo (trancaria o dono para fora, sem quem o reative pela aplicação);
- o último OWNER ativo não pode ser desativado (deixaria a oficina sem dono);
- reativar consome vaga do plano e é recusado com 402 se o limite estiver cheio.

Nenhum dado é apagado: a autoria dos registros históricos permanece íntegra.

**Evidência (depois), na stack real:**

```text
mecanico ANTES: 200
desativar: 200
mecanico DEPOIS (mesmo access token): 401
refresh do mecanico: 401
motivos gravados: DESATIVACAO|2  LOGOUT|2
```

**Testes:** `desativarUsuarioRevogaAcessoImediatamenteERefreshTokens`, `reativarUsuarioDevolveOAcesso`,
`ninguemSeTrancaForaDaPropriaOficina`, `somenteProprietarioRevogaAcessoESomenteNaPropriaOficina`.

**Risco residual:** nenhum conhecido. O access token já emitido morre na requisição seguinte.

---

### F-03 · CSP, HSTS e Permissions-Policy ausentes — **MÉDIO** · ✅ CORRIGIDO

**Evidência (antes):** `curl -I` trazia `nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy` e
`Cache-Control: no-store`, mas **nenhum** `Content-Security-Policy`, `Strict-Transport-Security` ou
`Permissions-Policy`.

**Impacto:** camada de defesa ausente. Sozinha não constitui vulnerabilidade — não há sink de XSS no
frontend (item 4 abaixo) — mas é a rede de proteção para quando um aparecer.

**Correção:** em `SecurityConfig`. A CSP é **escopada por caminho**, porque a política certa para a
API quebraria o Swagger, que é HTML servido pelo mesmo app:

| Caminho | Política |
|---|---|
| API (tudo que não é doc) | `default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'` |
| `/swagger-ui*`, `/v3/api-docs*` | `default-src 'self'; script-src 'self' 'unsafe-inline'; …; frame-ancestors 'none'` |

**Evidência (depois):**

```text
API:     Content-Security-Policy: default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'
         Permissions-Policy: accelerometer=(), camera=(), geolocation=(), … usb=()
Swagger: Content-Security-Policy: default-src 'self'; script-src 'self' 'unsafe-inline'; …
Swagger continua funcionando: api-docs=200 swagger-ui=200
```

**Testes:** `respostasTrazemOsCabecalhosDeSeguranca`, `documentacaoRecebeCspPropriaParaNaoQuebrar`.

**Risco residual — importante:** **o HSTS não será emitido na configuração atual de produção.** O
Spring só o envia em requisição segura, e `server.forward-headers-strategy: none` faz a aplicação
nunca se enxergar em HTTPS atrás de um proxy que termina TLS. Resolver exige, no deploy:
`TRUST_PROXY=true` e `server.forward-headers-strategy=framework`. Ver item 5.

O `'unsafe-inline'` da CSP do Swagger é exigência da própria ferramenta. Mitigado por desligar a
documentação em produção (F-05).

---

### F-04 · Reuso de refresh token não era detectado — **MÉDIO** · ✅ CORRIGIDO

**Evidência (antes):**

```text
refresh rotacionado: SIM
reuso do refresh antigo: 401
novo refresh ainda funciona apos reuso do antigo: 200   <-- família não revogada
```

**Cenário de exploração:** com um refresh token roubado, o atacante rotaciona primeiro. O token do
dono legítimo passa a falhar — mas o dono só vê um 401 isolado, entra de novo, e **a sessão do
ladrão continua válida e se renovando**. O sinal mais claro de comprometimento passava despercebido.

**Correção:** ao receber um refresh já revogado **por rotação**, revoga-se toda a família de tokens
do usuário. Ambas as partes são obrigadas a autenticar de novo, e o evento fica no log como
`refresh_reutilizado_familia_revogada`.

**Duas correções de percurso, ambas encontradas por teste:**

1. A revogação estava dentro da transação que termina lançando 401 — **o rollback do erro a
   desfazia**. A defesa sumia exatamente no caminho em que precisava valer. Corrigido com
   `TransactionTemplate` em `REQUIRES_NEW`.
2. A primeira versão tratava **logout** como reuso, porque logout também revoga o token. Isso
   derrubaria todas as sessões da pessoa a cada saída, com alarme falso. Quebrou
   `Fase6IT#refreshRevogadoDiagnosticavelELogoutIdempotente` — um teste que estava certo. Corrigido
   com a migration **V5**, que grava *por que* o token foi revogado (`LOGOUT`, `ROTACAO`, `REUSO`,
   `DESATIVACAO`); só `ROTACAO` é evidência de reuso.

**Testes:** `refreshReutilizadoDerrubaAFamiliaInteira` e
`logoutNaoEhTratadoComoReuseENaoDerrubaAsDemaisSessoes` (este garante que sair de uma aba não derruba
o aparelho do lado).

---

### F-05 · Documentação interativa pública — **MÉDIO** · ✅ CORRIGIDO (configuração)

**Evidência:** `/v3/api-docs` e `/swagger-ui/index.html` respondiam **200 sem autenticação**,
entregando o contrato completo da API a quem ainda não entrou.

**Impacto:** não é vulnerabilidade — nenhum endpoint fica acessível por estar documentado —, mas
entrega de graça o mapa da superfície: nomes de campos, enums, regras de validação e caminhos
administrativos. Reconhecimento facilitado.

**Correção:** `API_DOCS_ENABLED` (padrão `true` para não atrapalhar desenvolvimento) desliga
`springdoc.api-docs` e `springdoc.swagger-ui`. `.env.staging.example` já vai com `false`.

**Ação pendente de deploy:** definir `API_DOCS_ENABLED=false` no ambiente de produção.

---

### F-06 · IP registrado é o do proxy — **MÉDIO** · 📋 DOCUMENTADO + configurável

**Arquivo:** `ordemservico/acessopublico/api/PublicoController.java` — `request.getRemoteAddr()`
alimenta `aprovacao_orcamento.ip_origem`.

**Impacto:** `server.forward-headers-strategy: none` (que é o padrão **seguro**, pois impede forja de
cabeçalho) faz com que, atrás de um proxy, o IP gravado seja o **do proxy, não o do cliente**. A
trilha que a Política de Privacidade descreve como prova de autoria da aprovação do orçamento perde
o valor probatório em produção. O mesmo vale para a chave do teto de requisições: sem tratar o
proxy, todos os clientes contam como uma origem só, e um abusador barra a oficina inteira.

**Correção parcial:** `TRUST_PROXY` controla a leitura de `X-Forwarded-For` no filtro de teto, com o
alerta explícito de que ligá-lo com a porta exposta diretamente permite forjar o cabeçalho e escapar
do limite.

**Pendente antes de produção:** ao publicar atrás de proxy, ligar `TRUST_PROXY=true` **e**
`server.forward-headers-strategy=framework`, e confirmar que o proxy **sobrescreve** (não concatena)
`X-Forwarded-For`. Sem isso, `ip_origem` e o teto por origem ficam ambos incorretos.

---

### F-07 · Frontend sem serviço de produção — **MÉDIO** · 📋 DOCUMENTADO

**Evidência:** `compose.yml` define `postgres`, `minio`, `minio-init` e `api`. **Nenhum serviço de
frontend.** Em desenvolvimento o Vite serve e faz proxy; em produção não há nada definido.

**Impacto:** não existe hoje onde configurar, para as páginas que o navegador realmente renderiza:
CSP do frontend, HSTS, terminação TLS, redirecionamento HTTP→HTTPS e compressão gzip/brotli. A CSP
do F-03 protege a origem da API, **não** a origem do frontend.

**Recomendação:** acrescentar um serviço servindo `frontend/dist` atrás de TLS (nginx/Caddy), com
CSP própria, HSTS, compressão e as rotas SPA. Não implementado aqui por ser decisão de
infraestrutura, e por `npm run build` já produzir `dist/` pronto.

---

### F-08 · Migration V5 quebrava em base com dados — **MÉDIO** · ✅ CORRIGIDO

Introduzido e corrigido **dentro desta auditoria**. Fica registrado porque o modo de descoberta
importa.

**Evidência:** a primeira versão da V5 criava a restrição `(revogado_em is null) = (motivo_revogacao
is null)` **antes** do `UPDATE` que preenchia a coluna. Em banco vazio passava; na stack real, que já
tinha tokens revogados das sondagens, a aplicação **não subiu**:

```text
Script V5__motivo_de_revogacao_do_refresh.sql failed
SQL State: 23514 | ERROR: check constraint ... is violated by some row
```

**Impacto (se tivesse ido para produção):** indisponibilidade total no deploy, já que o Flyway roda
antes do contexto da aplicação.

**Correção:** preenchimento antes das restrições. E, mais importante, um teste que exercita o
caminho: `Fase8SegurancaIT#migrationV5SobeSobreBaseComTokensJaRevogados` aplica V1–V4 num schema
isolado, insere um token vivo e um revogado, e só então roda a V5.

**Lição, registrada:** a suíte validava migrations apenas contra banco vazio. Migration só é
validada contra **dados**.

---

### F-09 · Higiene de publicação: favicon, robots, sitemap, prévia social — **BAIXO** · ✅ CORRIGIDO

**Evidência:** `frontend/public/` continha apenas `materiais/`. Sem favicon (404 no navegador), sem
`robots.txt`, sem `sitemap.xml`; `index.html` sem Open Graph nem Twitter card.

**Risco relevante para segurança:** sem `robots.txt`, nada instruía buscadores a **não** indexar
`/acompanhar` — a rota do link do cliente. O token tem 256 bits e não é adivinhável, mas um link
compartilhado e rastreado poderia acabar indexado.

**Correção:** `robots.txt` liberando só `/institucional` e `/ajuda` e bloqueando `/acompanhar`,
`/materiais/`, `/api/` e o resto; `sitemap.xml` só com as páginas institucionais; `favicon.svg`; e
Open Graph/Twitter apontando para a imagem de demonstração já existente. Verificado em `dist/` após
`npm run build`.

**Pendente:** trocar `garagem.example` pelo domínio real em `robots.txt` e `sitemap.xml`.

---

### F-10 · Políticas não são acessíveis ao usuário — **BAIXO** · 📋 PENDENTE

Termos de Uso, Política de Privacidade e Política de Cancelamento existem como Markdown em `docs/`,
**mas não são servidos em lugar nenhum**. A página institucional não os referencia.

**Impacto:** o produto se propõe a cobrar assinatura sem apresentar termos ao contratante. É questão
contratual e de conformidade, não técnica.

**Recomendação:** publicar como páginas em `/termos` e `/privacidade` e referenciá-las no rodapé
institucional e na área de assinatura. Não implementado por ser frente do Kauã (páginas
institucionais) e depender do preenchimento dos dados societários.

---

### F-11 · Sem restart policy no Compose — **BAIXO** · ✅ CORRIGIDO

Nenhum serviço declarava `restart`. Uma falha transitória deixava o serviço parado até intervenção
manual. Adicionado `restart: unless-stopped` a `postgres`, `minio` e `api`.

---

### F-12 · `.env.staging.example` desatualizado — **BAIXO** · ✅ CORRIGIDO

Faltavam todas as variáveis `PAYMENT_*` da Fase 7. Staging subiria sem `PAYMENT_WEBHOOK_SECRET` —
que **falha fechado**, recusando todo webhook, mas sem que ninguém entendesse por quê. Acrescentadas
as de pagamento e as novas de segurança.

---

### F-13 · Poster de vídeo de 528 KB — **INFORMATIVO**

`garagem-demonstracao.png` tem 528 KB para uma imagem de poster. Não é risco; é peso na primeira
visita da página institucional. Recomendado reexportar como WebP. Não alterado para não mexer em
material comercial sem pedido.

---

## 3. Verificações que passaram

Cada linha foi testada, não presumida.

### Isolamento entre oficinas — **OK**

A garantia mais importante do produto. Duas oficinas reais, recursos criados em A, sondados por B:

```text
B GET  /clientes/{idA}                 404      B PUT  /clientes/{idA}                 404
B GET  /veiculos/{idA}                 404      B POST /os/{idA}/links                 404
B GET  /ordens-servico/{idA}           404      B POST /veiculos vinculando clienteA   404
B GET  /os/{idA}/timeline              404      listagem de B: {"itens":[],"total":0}
B GET  /os/{idA}/fotos                 404
B GET  /os/{idA}/orcamento/versoes     404
```

**404, não 403** — B não confirma sequer que o registro existe.

Três camadas independentes: `@TenantId` do Hibernate em toda entidade; `TenantContext.current()` em
todo SQL manual; e chaves estrangeiras compostas `(id, oficina_id)` no banco, que impedem o vínculo
cruzado mesmo com um bug na aplicação. Varredura manual de todo SQL e JPQL em
`backend/src/main/java`: **nenhuma consulta de negócio sem filtro por oficina**. As quatro sem filtro
são, por desenho, login (resolve o tenant), link público (devolve só o escopo), catálogo de planos
(global, sem dado pessoal) e recebimento de webhook (o evento chega antes de sabermos a oficina).

### Autorização por papel — **OK**

```text
MECANICO  POST /usuarios          403     ATENDENTE POST /usuarios (criar OWNER)   403
MECANICO  POST /clientes          403     ATENDENTE PUT  /assinatura/plano         403
MECANICO  GET  /assinatura        403     ATENDENTE POST /assinatura/cancelamento  403
MECANICO  PUT  /assinatura/plano  403
MECANICO  GET  /dinheiro-esquecido/resumo 403
```

Aplicada no backend com `@PreAuthorize`, verificada por chamada direta ignorando o frontend.

### Mass assignment — **OK**

Forjar `id`, `oficinaId`, `ativo`, `criadoEm` e `revisao` no corpo **não tem efeito**: os DTOs são
`record` com componentes declarados, e `TenantEntity.@PrePersist` força o tenant do token.

```text
enviado: id=9999…, oficinaId=<oficina B>, ativo=false
devolvido: id=3ef4e328-… (servidor), ativo=true | cliente na oficina B: 0
```

### Escalonamento de privilégio — **OK (sem superfície)**

Não existe endpoint de troca de papel. Criar usuário é exclusivo do OWNER. O novo
`PUT /{id}/situacao` altera apenas `ativo`, nunca `papel`.

### SQL injection — **OK**

Ordenação dinâmica passa por allowlist; qualquer campo fora dela é **400** antes de tocar o banco.
Filtros e buscas são parametrizados.

```text
?ordenacao=nome;DROP TABLE cliente--   400      ?busca=' OR 1=1--        200 (texto, não SQL)
?ordenacao=(select 1)                  400      ?busca=';DROP TABLE…--   200
?faixaIdade=X'--                       400      tabela cliente: intacta
```

### XSS — **OK**

Varredura por `dangerouslySetInnerHTML|innerHTML|document.write|eval|new Function|outerHTML|
insertAdjacentHTML` em `frontend/src`: **nenhum sink**. Payload armazenado é devolvido como
`application/json` com `nosniff` e escapado pelo React. Os únicos `href`/`src` dinâmicos são o link
público gerado pelo servidor e um blob vindo da própria API.

### Uploads — **OK**

O tipo vem do **conteúdo decodificado**, nunca da extensão ou do `Content-Type`:

```text
PNG válido                         201      HTML disfarçado de image/png   415
SVG com <script>                   415      filename=../../../etc/passwd   201, mas…
```

O nome enviado é **descartado**: a chave gravada é `oficina/os/uuid`, derivada de identificador
interno. Path traversal é impossível por construção. Teto de 10 MB e 20 MP; a imagem é reescrita,
o que descarta EXIF/GPS; `finalidade` fora do conjunto é 400.

### MinIO — **OK**

Bucket privado, sem ACL e sem URL pré-assinada. Os bytes passam pela API, que confere oficina e
vínculo com a OS antes de ler o objeto. As portas ficam em `127.0.0.1` no Compose.

### JWT — **OK**

```text
alg=none                    401      assinatura HS256 forjada    401
```

HS256 com segredo de no mínimo 32 bytes (a aplicação recusa subir com menos), `issuer` e `audience`
validados, expiração de 15 minutos. Além disso, `TenantRequestFilter` reconfere no banco, a cada
requisição, se o usuário está ativo, com o papel do token e a oficina ativa — um token de usuário
desativado morre **antes de expirar**.

### Enumeração de usuário — **OK**

Mensagem idêntica e tempo equivalente nos três casos, graças ao hash `dummy` que faz o BCrypt rodar
mesmo quando a conta não existe:

```text
usuário existe, senha errada      490ms   "Credenciais inválidas ou expiradas."
usuário inexistente               476ms   "Credenciais inválidas ou expiradas."
oficina inexistente               510ms   "Credenciais inválidas ou expiradas."
```

### Senhas — **OK**

BCrypt de custo 12. Mínimo de 12 caracteres, teto de 72 bytes. Texto puro nunca é gravado nem
registrado. Nenhuma resposta traz `senhaHash`. Verificado por
`senhaEhGravadaApenasComoHashForte`, que casa o prefixo `$2[aby]$12$`.

### Segredos — **OK**

- Varredura por segredo embutido em `backend/src/main` e `frontend/src`: **nada**.
- Histórico do Git por padrões conhecidos (`AKIA…`, `sk_live_…`, `xox[baprs]-…`): **nada**.
- `.env` **nunca foi commitado**; `.gitignore` cobre `.env`, `backups/`, `*.dump`, `*.log`.
- Rastreados apenas os `*.example`, todos com `CHANGE_ME`.
- Bundle de produção: única variável `VITE_*` é `VITE_API_BASE_URL` (uma URL, não segredo). As
  ocorrências de `password`/`jwt` no bundle são nomes de campo e internals do React.
- **Nenhum source map** em `dist/`.

### CORS — **OK**

`CorsProperties` **recusa a aplicação subir** com `*` e valida o formato de cada origem.
`allowCredentials=false`, coerente com autenticação por cabeçalho. Sem origens configuradas, nenhum
cabeçalho CORS é emitido.

### CSRF — **OK (não aplicável por arquitetura)**

`csrf.disable()` é **correto aqui**, e a verificação foi feita, não presumida: não há cookie algum —
varredura por `localStorage|sessionStorage|document.cookie` em `frontend/src` retornou **zero
ocorrências**. A sessão vive apenas em memória, e o token vai em `Authorization`. Como o navegador
não anexa credencial automaticamente, uma requisição forjada por outro site chega anônima. Se um dia
a autenticação passar a usar cookie, **esta conclusão deixa de valer**.

Efeito colateral aceito: recarregar a página exige novo login. É uma troca deliberada — imune a
roubo de token por XSS, ao custo de conveniência.

### Tokens no navegador — **OK**

Nem `localStorage`, nem `sessionStorage`, nem IndexedDB, nem cookie.

### Actuator — **OK**

Apenas `health` exposto (`management.endpoints.web.exposure.include: health`), com
`show-details: never`. Corpo público: `{"status":"UP","groups":["liveness","readiness"]}` — sem
componentes, sem URL de banco. `env`, `beans`, `heapdump`, `threaddump`, `metrics`, `loggers`,
`configprops`: todos **401**.

### Erros e stack traces — **OK**

RFC 7807 consistente, com `code` estável e `requestId` correlacionável. Nenhum stack trace, nome de
classe, SQL ou caminho de arquivo em resposta. `include-message: never`, `include-stacktrace: never`.
`ApiErrors` substitui o `instance` em rotas públicas para o token não vazar no corpo do erro.

### Logs — **OK**

Logback estruturado com `request_id`, `oficina_id`, `usuario_id`, método, rota, status e duração.
Sem `Authorization`, sem token, sem senha, sem corpo. O token do link público é removido do caminho
antes de virar log. Na cobrança, `CobrancaService.higienizar` descarta metadado cuja chave contenha
termo sensível.

**Log injection:** aceita-se `\n` em campo de texto (201), mas o log é **JSON**, e o encoder escapa
a quebra de linha dentro da string. A estrutura não é forjável. **BAIXO, aceito.**

### Limites de API — **OK**

```text
tamanho=100000 -> 100      tamanho=-1 -> 1
tamanho=1000   -> 100      tamanho=0  -> 1
```

Teto de 100 por página, piso de 1. Multipart limitado a 10 MB/11 MB. Além disso, os limites por plano
da Fase 7 (usuários, armazenamento, veículos, OS/mês) são aplicados no backend.

### Webhook de pagamento — **OK**

Auditado na Fase 7 e reconfirmado aqui: HMAC-SHA256 do corpo cru comparado em tempo constante;
**falha fechado** sem segredo; idempotência pelo unique `(provedor, provider_event_id)` no banco; o
tenant sai do `provider_subscription_id` já cadastrado, **não do payload**; corpo adulterado após
assinado é recusado; tipo desconhecido é registrado e ignorado, nunca tratado como pagamento
aprovado. Coberto por 6 cenários em `Fase7IT`.

**Ressalva registrada:** não há verificação de *timestamp*. A idempotência impede que um evento
repetido gere efeito duplicado, o que cobre o risco prático de replay. Um gateway real que envie
timestamp assinado deve ter essa checagem somada — anotado para a integração.

### Duplicidade de assinatura e de pagamento — **OK**

`unique(oficina_id)` em `assinatura`; `unique(oficina_id, oportunidade_id)` em resultado; unique do
`provider_event_id`; lock pessimista e `@Version` nas transições. Requisições simultâneas testadas na
Fase 7 resultam em `201/409`, nunca em dois efeitos.

### Índices — **OK**

Toda tabela quente tem índice liderado por `oficina_id`, que é como 100% das consultas filtram. As
FKs sem índice dedicado (`assinatura.plano_id`, `evento_cobranca.assinatura_id`, …) nunca são
consultadas isoladamente — sempre com `oficina_id` à frente, já coberto. **Nenhum índice
desnecessário adicionado.**

### Acessibilidade, mobile e contraste — **OK**

`npx playwright test tests/browser/commercial.spec.mjs` → **12 passed**, em três viewports (desktop
1440, tablet 768, mobile 390), com `AxeBuilder` em `wcag2a`, `wcag2aa` e `wcag21aa` exigindo **zero
violações**. Nenhuma `<img>` sem `alt`; vídeo com `<track kind="captions">` e `aria-label`.

### Dependências — **OK**

`npm audit` (com e sem dev): **0 vulnerabilidades**. Maven em Spring Boot 3.5.16 gerenciado.
**Nenhuma atualização aplicada** — sem CVE conhecida a tratar, e atualizar sem motivo é risco.

### Contêiner e portas — **OK**

API roda como `uid=999(garagem)`, não-root, com healthcheck. `postgres`, `minio` e `api` publicam
**apenas em `127.0.0.1`**: nenhum banco ou console de storage exposto.

---

## 4. Itens não aplicáveis ou não validados

| Item | Estado | Motivo |
|---|---|---|
| Cookie consent | **NÃO APLICÁVEL** | Nenhum cookie é usado. Verificado, não presumido. Banner inútil não foi adicionado |
| Analytics | **NÃO APLICÁVEL** | Nenhum rastreador. Se adicionar, revisar privacidade e consentimento |
| SSRF | **NÃO APLICÁVEL** | Backend não faz requisição a URL fornecida pelo usuário. Única saída é o endpoint S3, de configuração |
| Open redirect | **NÃO APLICÁVEL** | Nenhum redirecionamento controlado por parâmetro |
| RLS no PostgreSQL | **AVALIADO, NÃO IMPLEMENTADO** | Ver abaixo |
| Spending caps | **PARCIAL** | Nada gera custo externo hoje (sem gateway, sem e-mail, sem IA). Armazenamento tem teto por plano. Reavaliar ao contratar gateway |
| Verificação de e-mail | **NÃO IMPLEMENTADO** | Não há autocadastro: a conta é provisionada pela equipe, e o e-mail é validado fora. Risco baixo na V1; necessário se houver autocadastro |
| Backup/restore | **NÃO VALIDADO NESTA AUDITORIA** | Procedimento em `docs/backup-piloto.md`, exercitado na Fase 6. Não reexecutado aqui para não competir por recursos com a stack de auditoria |
| Uptime monitoring | **NÃO IMPLEMENTADO** | `/actuator/health` existe e é a sonda correta; não há monitor externo contratado (pendência S2 do SLA) |
| Medição de page load | **NÃO VALIDADO** | Sem ambiente representativo. Dados objetivos do build: JS 345 KB (104 KB gzip), CSS 53 KB (11,7 KB gzip), sem source map |
| Compressão HTTP | **NÃO APLICÁVEL HOJE** | Depende do serviço de frontend que não existe (F-07) |
| 404 customizado | **PARCIAL** | Rota desconhecida cai no app (tela de login), não em tela branca. Não é 404 próprio. Baixo impacto: `robots.txt` já impede indexação |

### Sobre Row Level Security

**Não implementado, e a recomendação é não implementar agora.**

Proteção existente: três camadas independentes (`@TenantId`, filtro explícito em todo SQL, FKs
compostas no banco). A terceira já é uma barreira **no banco**: mesmo que a aplicação erre e tente
vincular um registro de outra oficina, a FK composta recusa.

Risco residual sem RLS: uma consulta nova que esqueça `oficina_id` **em leitura** passaria. As FKs
protegem o vínculo, não a leitura. Hoje isso é contido por revisão e pelos testes de isolamento de
todas as fases.

Benefício do RLS: transformaria o esquecimento de filtro de vazamento em conjunto vazio.

Custo: exigiria `SET LOCAL` de variável de sessão por transação em toda a aplicação, interagindo com
o pool de conexões e com o Flyway; risco relevante de introduzir bug de disponibilidade em um sistema
que hoje **não apresenta falha de isolamento**.

**Recomendação:** manter o modelo atual, e reavaliar RLS se a equipe crescer a ponto de a revisão
deixar de ser garantia — ou antes de abrir o banco a qualquer consumidor fora desta aplicação.

---

## 5. Tabela final

| Item | Estado | Severidade | Evidência | Correção |
|---|---|---|---|---|
| Teto de requisições / força bruta | ✅ Corrigido | **ALTO** | 20 falhas sem bloqueio → 429 após 10 | `LimiteRequisicoesFilter` |
| Revogação de acesso de usuário | ✅ Corrigido | **ALTO** | PUT/DELETE `/usuarios/{id}` → 404 | `PUT /{id}/situacao` |
| CSP / HSTS / Permissions-Policy | ✅ Corrigido | MÉDIO | `curl -I` sem os três | `SecurityConfig` |
| Reuso de refresh token | ✅ Corrigido | MÉDIO | token novo válido após replay | família revogada + V5 |
| Swagger público | ✅ Corrigido | MÉDIO | 200 anônimo | `API_DOCS_ENABLED` |
| IP do proxy na auditoria | 📋 Documentado | MÉDIO | `getRemoteAddr()` + `strategy: none` | `TRUST_PROXY` + deploy |
| Frontend sem serviço de produção | 📋 Documentado | MÉDIO | `compose.yml` sem frontend | Infraestrutura |
| Migration V5 em base com dados | ✅ Corrigido | MÉDIO | `SQL State 23514` no boot | Ordem + teste de upgrade |
| favicon / robots / sitemap / OG | ✅ Corrigido | BAIXO | `public/` só com `materiais/` | Arquivos criados |
| Termos/Privacidade não servidos | 📋 Pendente | BAIXO | só Markdown em `docs/` | Páginas públicas |
| Restart policy | ✅ Corrigido | BAIXO | ausente no Compose | `unless-stopped` |
| `.env.staging.example` | ✅ Corrigido | BAIXO | sem `PAYMENT_*` | Variáveis acrescentadas |
| Poster de 528 KB | 📋 Informativo | INFO | `ls -lh public/materiais` | Reexportar WebP |
| Isolamento entre oficinas | ✅ OK | — | 9 vetores → 404 | — |
| Autorização por papel | ✅ OK | — | 8 sondagens → 403 | — |
| Mass assignment | ✅ OK | — | campos forjados ignorados | — |
| SQL injection | ✅ OK | — | allowlist → 400 | — |
| XSS | ✅ OK | — | nenhum sink | — |
| Uploads / path traversal | ✅ OK | — | 415 e chave `oficina/os/uuid` | — |
| JWT (`alg=none`, forja) | ✅ OK | — | 401 nos dois | — |
| Enumeração de usuário | ✅ OK | — | mesma msg, ~490 ms | — |
| Senhas | ✅ OK | — | `$2[aby]$12$` | — |
| Segredos / Git / bundle | ✅ OK | — | varreduras vazias | — |
| CORS | ✅ OK | — | recusa `*` no boot | — |
| CSRF | ✅ OK (N/A) | — | zero cookies | — |
| Actuator | ✅ OK | — | só `health`, sem detalhe | — |
| Stack trace / erros | ✅ OK | — | RFC 7807 limpo | — |
| Logs | ✅ OK | — | sem token/senha | — |
| Webhook | ✅ OK | — | 6 cenários na Fase 7 | — |
| Paginação / limites | ✅ OK | — | teto 100, piso 1 | — |
| Acessibilidade / mobile | ✅ OK | — | axe WCAG 2.1 AA, 12 passed | — |
| Dependências | ✅ OK | — | `npm audit`: 0 | — |
| Contêiner / portas | ✅ OK | — | uid 999, `127.0.0.1` | — |
| RLS | 📋 Avaliado | INFO | 3 camadas existentes | Não recomendado agora |

---

## 6. Priorização

### BLOQUEIA PRODUÇÃO

1. **F-06 — `TRUST_PROXY=true` + `server.forward-headers-strategy=framework`** ao publicar atrás de
   proxy. Sem isso, três coisas ficam erradas ao mesmo tempo: o IP da aprovação de orçamento vira o
   do proxy (a prova que a Política de Privacidade descreve perde valor), o teto por origem trata
   todos os clientes como um só (um abusador barra a oficina inteira) e o HSTS nunca é emitido.
2. **F-07 — servir o frontend por HTTPS**, com CSP própria, HSTS e redirecionamento HTTP→HTTPS.
   Hoje não existe serviço de produção para o frontend.
3. **F-05 — `API_DOCS_ENABLED=false`** em produção.

Justificativa de serem bloqueadores: os três são *configuração de publicação*. Nenhum se resolve em
código — todos dependem de como o sistema é colocado no ar, e sem eles a aplicação vai ao ar com
controle de segurança degradado de forma silenciosa.

### CORRIGIR ANTES DO PILOTO

4. Confirmar `PAYMENT_WEBHOOK_SECRET`, `JWT_SECRET` e `DATABASE_PASSWORD` gerados **por ambiente**,
   nunca reaproveitados (`openssl rand`). O bootstrap deve ficar `APP_BOOTSTRAP_ENABLED=false` após
   a primeira subida, e a senha inicial trocada.
5. **F-10 — publicar Termos e Política de Privacidade** e referenciá-los. Cobrar assinatura sem
   apresentar termos é exposição contratual.
6. Validar backup **e restore** no ambiente do piloto (`docs/backup-piloto.md`). Backup sem restore
   testado não é backup.
7. Trocar `garagem.example` pelo domínio real em `robots.txt` e `sitemap.xml`.

### CORRIGIR ANTES DO LANÇAMENTO PÚBLICO

8. Monitor externo de `/actuator/health` (pendência S2 do SLA — sem ele não há medição auditável de
   disponibilidade).
9. Reavaliar o teto de requisições com tráfego real; se houver mais de uma instância, trocar a
   contagem em memória por contador compartilhado.
10. Verificação de e-mail, **se** houver autocadastro. Não é necessária no modelo atual.
11. Verificação de timestamp no webhook, ao integrar um gateway que o envie assinado.

### MELHORIA PÓS-LANÇAMENTO

12. 404 próprio no frontend (F-13 / rota desconhecida).
13. Reexportar o poster para WebP.
14. Reavaliar RLS se a equipe crescer ou o banco for aberto a outro consumidor.
15. Exportação e eliminação de dados por titular (P1/P2 de `revisao-lgpd.md`).

---

## 7. Testes e execuções

```text
mvn -B -ntp verify
  15 unitários  + 143 integração — 0 falhas, 0 erros, 0 ignorados
  Fase1IT 22 | Fase2IT 27 | Fase3IT 15 | Fase5IT 26 | Fase6IT 5 | Fase7IT 27 | Fase8SegurancaIT 22
  BUILD SUCCESS

npm run typecheck   OK
npm run lint        OK (0 avisos)
npm test            23 passed
npm run build       OK — sem source maps
npx playwright test tests/browser/commercial.spec.mjs   12 passed (desktop/tablet/mobile, axe WCAG 2.1 AA)
npm audit           0 vulnerabilidades (com e sem dev)
```

`Fase8SegurancaIT` acrescenta **22 cenários** de regressão: superfície anônima, caminhos de scanner,
Actuator, cabeçalhos, CSP escopada, força bruta, enumeração, revogação de acesso e suas três travas,
reuso de refresh, logout não confundido com reuso, upgrade da V5 sobre base com dados, JWT forjado,
mass assignment, escalonamento de papel, allowlist de ordenação, paginação, erro sem stack trace,
hash de senha e ausência de hash nas respostas.

**Regressão preservada:** nenhum teste anterior foi alterado. A única quebra durante a auditoria
(`Fase6IT#refreshRevogadoDiagnosticavelELogoutIdempotente`) foi causada pela minha própria correção e
resolvida **corrigindo a correção**, não enfraquecendo o teste — o teste estava certo.
