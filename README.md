# Garagem SaaS

“Sua oficina já tem clientes. Faça eles voltarem.”

Backend da **Fase 1** implementado. Java 25, Spring Boot, Spring Security/JWT, Spring Data JPA, PostgreSQL, Flyway e Maven. A interface React + TypeScript + Vite ainda aguarda a validação humana do [fluxo proposto](docs/proposta-interface.md). As fases 2–4 não foram iniciadas.

## O que está disponível

- Login por oficina + e-mail, access token e refresh token com rotação/revogação; papéis OWNER, MECANICO e ATENDENTE.
- Cadastro e edição de clientes e veículos, com controle de revisão e isolamento entre oficinas.
- Abertura de OS, atribuição de mecânico, fluxo de status, busca por placa/cliente/número e timeline.
- Checklist de entrada, diagnóstico verde/amarelo/vermelho e fotos privadas em S3.
- Orçamentos versionados e imutáveis, incluindo proteção de itens e totais no PostgreSQL.
- API pública limitada, link com expiração/revogação e aprovação/recusa integral com prova e idempotência.
- OpenAPI, logs JSON com contexto, duas migrations com scripts de reversão, Docker Compose e CI.

## Executar com Docker

Requisitos: Docker Engine funcional com containers Linux e Docker Compose. Não é necessário instalar Java/Maven no host neste modo.

1. Copie `.env.example` para `.env` e substitua todos os `CHANGE_ME` por valores próprios. `JWT_SECRET` precisa de pelo menos 32 bytes aleatórios; senha do OWNER, pelo menos 12 caracteres e no máximo 72 bytes UTF-8.
2. Para criar sua primeira oficina, configure `APP_BOOTSTRAP_ENABLED=true`, slug, nome, e-mail e senha. O bootstrap só cria uma oficina se o slug ainda não existir; nunca altera dados ou redefine senhas de uma oficina existente.
3. Execute na raiz:

```sh
docker compose up --build -d
```

4. Acesse `http://localhost:8080/swagger-ui/index.html`. Use `POST /api/v1/auth/login` com os dados configurados. Copie `accessToken` para o botão **Authorize** do Swagger.
5. Depois do primeiro início, desative o bootstrap e remova a senha inicial da configuração utilizada pelo serviço.

API em `localhost:8080`, OpenAPI em `/v3/api-docs`, health em `/actuator/health`. PostgreSQL em `localhost:5432`; MinIO em `localhost:9000`, console em `localhost:9001`. As portas do Compose ficam vinculadas apenas ao localhost. O bucket é criado privado pelo serviço `minio-init`.

O `PUBLIC_BASE_URL` é o endereço previsto para a futura interface. Por enquanto, a URL de acompanhamento não apresenta página; use o token retornado pelo endpoint de links com `/api/v1/publico/{token}` no Swagger.

## Executar o backend fora de container

Requisitos: Java 25 e PostgreSQL. O Maven Wrapper baixa Maven 3.9.11; não precisa de instalação global. Para fotos, configure também um bucket S3 privado. O Spring não carrega `.env` automaticamente: exporte as variáveis abaixo no processo antes de iniciar.

```text
DATABASE_URL=jdbc:postgresql://localhost:5432/garagem
DATABASE_USER=garagem
DATABASE_PASSWORD=<senha do banco>
JWT_SECRET=<segredo aleatório de pelo menos 32 bytes>
S3_ENDPOINT=http://localhost:9000
S3_ACCESS_KEY=<chave>
S3_SECRET_KEY=<segredo>
S3_BUCKET=garagem-fotos
```

Na pasta `backend`:

```sh
./mvnw spring-boot:run
```

No PowerShell, use `.\mvnw.cmd spring-boot:run`. Configure `JAVA_HOME` para um JDK 25. A criação inicial da oficina também aceita as variáveis `APP_BOOTSTRAP_*` descritas acima.

## Testes e formatação

Com Java 25 e Docker funcional, na pasta `backend`:

```sh
./mvnw -B -ntp verify
```

O comando executa JUnit 5, integração com PostgreSQL **e MinIO reais via Testcontainers**, build e checagem de formatação. Falta de Docker causa falha explícita, sem ignorar os testes. O CI executa esse mesmo caminho em Ubuntu e também compila a imagem Docker da API.

Para aplicar a formatação antes de verificar:

```sh
./mvnw spotless:apply
```

Alternativa explícita quando não há virtualização: aponte a suíte para um **banco PostgreSQL exclusivo de testes** por `TEST_DATABASE_URL`, `TEST_DATABASE_USER` e `TEST_DATABASE_PASSWORD`. Nesse modo, a API de fotos usa um armazenamento em memória nos testes; o SDK S3 não é validado. Nunca use banco de produção: a suíte executa migrations e insere fixtures. Os dados ficam preservados nesse banco externo; cada teste usa oficinas aleatórias.

## Fluxo mínimo pela API

1. Login; guardar `oficinaId`, `accessToken` e `refreshToken`.
2. Cadastrar cliente em `/api/v1/clientes` e veículo em `/api/v1/veiculos`.
3. Criar OS em `/api/v1/ordens-servico`.
4. Registrar checklist/fotos e diagnóstico nos sub-recursos da OS.
5. Enviar transições para `DIAGNOSTICO` e `ORCAMENTO`, sempre com a `revisao` atual.
6. Criar `/ordens-servico/{id}/orcamento/versoes` com itens; enviar status `AGUARDANDO_APROVACAO`.
7. Emitir `/ordens-servico/{id}/links`; consultar `/publico/{token}`.
8. Registrar `/publico/{token}/decisao` com `versaoId` e `aprovado`. Aprovação inicia manutenção; recusa retorna ao orçamento.
9. Avançar manutenção, eventual espera de peça, teste e pronto; consultar timeline e histórico de versões.

Todos os caminhos acima têm prefixo `/api/v1`. Não enviar JWT nos endpoints públicos. O token do link já é a credencial limitada desse acesso. Nunca registrar senha, JWT ou link em logs de proxy; manter DEBUG desativado em ambientes compartilhados.

## Estado da validação

Veja [aceite e verificações](docs/aceite-fase-1.md). Docker/Testcontainers e MinIO não puderam ser executados nesta máquina porque a virtualização está desabilitada. As verificações locais usaram Java 25 e PostgreSQL 17.11 reais. Os binários portáteis e logs usados estão em `.tools`, ignorada pelo Git.

Detalhes de isolamento, papéis, persistência e limites: [arquitetura](docs/arquitetura.md).

Staging ainda não foi configurado: provedor, destino, segredos e política de deploy precisam ser definidos antes de publicar. As configurações de Compose são para desenvolvimento local.
