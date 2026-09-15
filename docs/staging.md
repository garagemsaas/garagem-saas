# Ambiente de staging

Escrito para quem vai subir e operar o ambiente de staging.

Staging é uma cópia fiel da produção pretendida, com dados descartáveis. Serve para validar
migrations, contratos e o fluxo completo antes de qualquer promoção. **Nenhum deploy externo foi
feito**: este documento e o `.env.staging.example` deixam o projeto pronto para a subida, que
depende de autorização e de um servidor.

## Requisitos

| Item | Versão de referência |
|---|---|
| Docker Engine + Compose v2 | 29.x |
| PostgreSQL | 17.11 (sobe pelo Compose) |
| MinIO | release de 2025-09-07 (sobe pelo Compose) |
| CPU / RAM / disco | 2 vCPU, 4 GB, 20 GB |
| Portas expostas ao proxy | 8080 (API) |

O Compose publica todas as portas em `127.0.0.1`. Nada fica exposto na interface pública: quem
termina TLS e encaminha para a API é um proxy reverso no host.

## Variáveis de ambiente

Copie `.env.staging.example` para `.env` **no servidor** e preencha. O arquivo é lido pelo Compose e
repassado ao contêiner da API; nenhum segredo está no código ou na imagem.

| Variável | Obrigatória | Padrão | Observação |
|---|---|---|---|
| `DATABASE_PASSWORD` | sim | — | Compose recusa subir sem ela. |
| `JWT_SECRET` | sim | — | Mínimo de 32 bytes; a aplicação recusa subir com menos. |
| `JWT_ACCESS_TTL` | não | `PT15M` | Duração ISO-8601. |
| `JWT_REFRESH_TTL` | não | `P7D` | Duração ISO-8601. |
| `PUBLIC_LINK_TTL` | não | `P7D` | Validade do link do cliente. |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | sim | — | Credenciais do MinIO. |
| `S3_BUCKET` | não | `garagem-fotos` | Use um nome próprio de staging. |
| `PUBLIC_BASE_URL` | não | `http://localhost:5173` | Origem do frontend; monta o link público. |
| `CORS_ALLOWED_ORIGINS` | não | vazio | Origens explícitas, separadas por vírgula. Vazio = nenhuma origem cruzada. |
| `APP_BOOTSTRAP_*` | não | desligado | Cria a primeira oficina e o primeiro OWNER. |

### Gerando os segredos

```bash
openssl rand -base64 48   # JWT_SECRET
openssl rand -base64 24   # DATABASE_PASSWORD, S3_SECRET_KEY
```

Gere no próprio servidor. Não reaproveite segredos entre staging e produção: staging costuma ter
mais gente com acesso, e um vazamento ali não pode virar acesso à produção. Trocar `JWT_SECRET`
invalida todos os access tokens em circulação imediatamente.

### CORS

Se o frontend de staging estiver em outro domínio que não o da API, liste a origem exata:

```
CORS_ALLOWED_ORIGINS=https://staging.garagem.example
```

Com esquema, host e porta; sem caminho e sem barra final. **`*` não é aceito** — a aplicação recusa
subir, por decisão deliberada: a API é autenticada e curinga seria uma porta aberta. A origem
autorizada recebe os cabeçalhos CORS; qualquer outra tem o preflight recusado com 403. Se o proxy
servir frontend e API no mesmo domínio, deixe a variável vazia.

## Banco e migrations

O Flyway roda na subida da aplicação e é a única forma de alterar o schema. `ddl-auto` está em
`validate`: se o schema divergir das entidades, a aplicação **não sobe**, em vez de corrigir sozinha.

- Migrations ficam em `backend/src/main/resources/db/migration`.
- Nunca edite uma migration já aplicada. Crie a próxima (`V3__...`).
- O volume `postgres-data` guarda os dados. **A senha do Postgres é gravada na criação do volume**:
  trocar `DATABASE_PASSWORD` depois não muda a senha do banco existente e a API falha a
  autenticação. Para trocar de verdade, altere a senha dentro do Postgres (`ALTER ROLE`) ou recrie o
  volume aceitando a perda dos dados.

## Storage

O serviço `minio-init` cria o bucket e aplica `mc anonymous set none`, deixando-o **privado**. As
fotos nunca são servidas direto pelo storage: os bytes passam pela API, que confere sessão, oficina
e vínculo com a OS antes de ler o objeto. Não publique a porta 9000 no proxy.

## Subida

```bash
# 1. No servidor, com o repositório em uma tag ou commit revisado
cp .env.staging.example .env
$EDITOR .env                      # preencha todos os CHANGE_ME

# 2. Confirme que o .env não vai para o Git (já está no .gitignore)
git check-ignore -v .env

# 3. Suba
docker compose up -d --build

# 4. Acompanhe até a API ficar healthy
docker compose ps
docker compose logs -f api
```

A API depende do Postgres `service_healthy` e do `minio-init` `service_completed_successfully`, então
o Compose já ordena a subida. O contêiner da API só fica `healthy` quando `/actuator/health` responde
200, o que inclui banco e storage.

Na primeira subida, deixe `APP_BOOTSTRAP_ENABLED=true` para criar a oficina e o OWNER inicial.
Depois de confirmar o login, volte para `false` e suba de novo.

## Validação pós-subida

```bash
# Saúde: espera-se HTTP 200 e {"status":"UP"}
curl -i http://127.0.0.1:8080/actuator/health

# Contrato: espera-se HTTP 200 e o JSON do OpenAPI
curl -s http://127.0.0.1:8080/v3/api-docs | head -c 200

# Swagger UI: espera-se HTTP 200
curl -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/swagger-ui/index.html

# Sessão fechada: espera-se HTTP 401 com corpo problem+json e "code":"UNAUTHORIZED"
curl -i http://127.0.0.1:8080/api/v1/clientes

# Actuator sensível fechado: espera-se 4xx nos três
for e in env beans loggers; do
  curl -o /dev/null -w "$e %{http_code}\n" http://127.0.0.1:8080/actuator/$e
done

# CORS: a origem configurada é liberada, outra é recusada com 403
curl -s -o /dev/null -w 'autorizada %{http_code}\n' -X OPTIONS \
  http://127.0.0.1:8080/api/v1/clientes \
  -H "Origin: $CORS_ALLOWED_ORIGINS" -H 'Access-Control-Request-Method: GET'
curl -s -o /dev/null -w 'estranha   %{http_code}\n' -X OPTIONS \
  http://127.0.0.1:8080/api/v1/clientes \
  -H 'Origin: https://atacante.example' -H 'Access-Control-Request-Method: GET'
```

Depois, pela interface: entre com o OWNER criado no bootstrap, cadastre cliente e veículo, abra uma
OS, registre checklist e diagnóstico, envie uma foto, crie uma versão de orçamento, emita o link
público, aprove por ele e confira a timeline. É o caminho que exercita banco, storage, tenancy e
transições de status de uma vez.

## Health checks

- `GET /actuator/health` agrega **banco** (indicador padrão do Spring) e **storage** (indicador
  próprio, que apenas confirma a existência do bucket). Fica `DOWN` com 503 se qualquer um falhar.
- `show-details: never`: o corpo traz só o status. Nenhum nome de componente, versão, host ou
  caminho vaza.
- Sondas `/actuator/health/liveness` e `/readiness` disponíveis para orquestrador.
- O `HEALTHCHECK` da imagem e o healthcheck do Compose consultam `/actuator/health` a cada 10s, com
  60s de carência na subida.
- Nenhum outro endpoint do Actuator está exposto.

## Logs

Saída JSON estruturada (formato logstash) no stdout do contêiner, coletada pelo driver de log do
Docker. Não há plataforma externa de observabilidade neste estágio.

Cada linha traz `request_id`, `oficina_id` e `usuario_id`, e cada resposta HTTP devolve o mesmo id no
cabeçalho `X-Request-Id`. Para investigar um erro relatado por um usuário, peça esse número:

```bash
docker compose logs api | grep '"request_id":"<id>"'
```

**Nunca são registrados**: senha, hash, access token, refresh token, segredos ou corpo de
requisição. O token do link público é apagado do caminho antes de virar log
(`/api/v1/publico/{token}`), porque ele é uma credencial.

Ajuste de verbosidade sem rebuild, via variável de ambiente no serviço `api`:

```
LOGGING_LEVEL_BR_COM_GARAGEM=DEBUG
```

## Backup

O essencial é o banco; as fotos são recuperáveis apenas pelo volume do MinIO.

```bash
# Banco — diário, guardando 7 dias
docker compose exec -T postgres pg_dump -U garagem garagem \
  | gzip > backup-$(date +%F).sql.gz

# Fotos — volume do MinIO
docker run --rm -v garagem-saas_fotos-data:/data -v "$PWD":/backup alpine \
  tar czf /backup/fotos-$(date +%F).tar.gz -C /data .

# Restauração do banco, em base vazia
gunzip -c backup-AAAA-MM-DD.sql.gz \
  | docker compose exec -T postgres psql -U garagem garagem
```

Guarde os backups **fora do servidor** e teste a restauração pelo menos uma vez: backup não testado
não é backup. Não versione dumps.

## Atualização

```bash
git fetch && git checkout <tag ou commit revisado>
docker compose up -d --build
docker compose logs -f api     # acompanhe o Flyway
```

O Flyway aplica as migrations pendentes na subida. Faça backup do banco antes de qualquer
atualização que traga migration nova.

## Rollback

Voltar a imagem é simples; voltar o schema não é. Migrations do projeto não têm script de reversão.

1. Suba a versão anterior da imagem.
2. Se a versão nova aplicou migration, o schema antigo não volta sozinho: restaure o backup tomado
   antes da atualização.

Por isso o backup pré-atualização não é opcional.

## Checklist antes de promover

- [ ] `mvn verify` passa no commit que vai subir.
- [ ] `.env` preenchido, sem nenhum `CHANGE_ME`, e ignorado pelo Git.
- [ ] Segredos de staging diferentes dos de produção.
- [ ] `CORS_ALLOWED_ORIGINS` com origens explícitas, sem `*`.
- [ ] `/actuator/health` responde 200 e os três contêineres estão healthy.
- [ ] `/actuator/env`, `/beans` e `/loggers` respondem 4xx.
- [ ] Bucket privado e porta do MinIO não publicada pelo proxy.
- [ ] `APP_BOOTSTRAP_ENABLED=false` após a primeira subida.
- [ ] Senha do OWNER inicial trocada.
- [ ] Backup do banco agendado e uma restauração testada.
- [ ] Fluxo completo validado pela interface.
