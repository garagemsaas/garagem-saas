# Garagem SaaS

SaaS para oficinas mecânicas. A Fase 1 organiza clientes, veículos e ordens de serviço, do recebimento ao diagnóstico, orçamento, aprovação e conclusão.

## Estado atual — 15/09/2026

**Fase 1 tecnicamente concluída no ambiente local validado.** Frontend integrado à API real, PostgreSQL e fotos privadas no MinIO. O fluxo até PRONTO foi executado por HTTP e pelo navegador, com isolamento entre oficinas. Decisões comerciais e pilotos ainda dependem de Kauã e Cauã.

| Área | Evidência atual |
|---|---|
| Backend | 5 testes unitários e 12 de integração, sem falhas/ignorados; Maven verify e Spotless aprovados |
| PostgreSQL/MinIO | Testcontainers reais; Flyway V1/V2 e Hibernate validate aprovados |
| Frontend | Login, equipe, clientes, veículos, OS, checklist, diagnóstico, fotos, orçamento e links integrados |
| Qualidade frontend | 13 testes unitários; lint sem avisos; TypeScript e build Vite aprovados |
| Fluxo real | Chromium + Compose: cadastro, foto privada, aprovação pública, PRONTO, timeline e persistência após novo login |
| Conflitos e isolamento | 409 de revisão/versão, decisão idempotente; leituras/escritas cruzadas bloqueadas; MinIO anônimo 403 |
| Docker | Compose build e up aprovados; PostgreSQL healthy, MinIO/API ativos, minio-init Exited (0), health 200 |
| Comercial | Um único plano pago, sem freemium; preço, pacote final, pilotos, responsáveis e prazos a definir |
| CI/deploy | CI de backend configurado; execução remota não acionada/conferida nesta entrega. Sem deploy |

Leia [finalização e quadro de aceite](docs/fase1-finalizacao.md), [32 contratos REST e exemplos JSON](docs/api-contracts.md) e [aceite](docs/aceite-fase-1.md). Os documentos de protótipo/redesign preservam o histórico das etapas anteriores.

## Stack e arquitetura

- Java 25, Spring Boot 3.5.16, Maven Wrapper 3.9.11.
- Spring Web REST/JSON, Security/JWT, Bean Validation e OpenAPI/springdoc.
- PostgreSQL 17.11, JPA/Hibernate e Flyway.
- React 19, TypeScript 6, Vite 8, Oxlint, Inter e lucide-react; versões resolvidas no lockfile do frontend.
- S3FotoStorage com SDK AWS S3; MinIO privado local.
- JUnit 5, Testcontainers, Docker Compose e GitHub Actions.

**Modular Monolith:** um backend organizado por funcionalidade, com Controllers, DTOs, serviços transacionais, domínio e repositories. Não há microserviços nem módulos novos da Fase 2.

Multi-tenancy por oficina: JWT validado e usuário ativo determinam o tenant; queries e repositories incluem oficina, filtro Hibernate aplica isolamento e FKs compostas bloqueiam relacionamentos cruzados. O frontend não decide segurança. Papéis reais: OWNER, ATENDENTE, MECANICO.

```text
RECEBIDO → DIAGNOSTICO → ORCAMENTO → AGUARDANDO_APROVACAO
                                         ↓ aprovação pública
                                   EM_MANUTENCAO → TESTE → PRONTO
```

Manutenção permite espera de peça e retorno. Revisão/recusa retorna a ORCAMENTO. Versões, itens, decisões e eventos preservam histórico. Detalhes em [arquitetura](docs/arquitetura.md).

## Executar localmente

Pré-requisitos: Docker Engine/Desktop com containers Linux e Compose; Node compatível com o package-lock (Node 22.18+ ou 24+ para os testes TypeScript); JDK 25 para executar Maven no host. Dependências/imagens precisam estar disponíveis localmente ou nos registries.

### 1. Configuração

Copie `.env.example` para `.env` **se ainda não houver configuração local**. Substitua os placeholders CHANGE_ME. Não versionar `.env`.

```powershell
Copy-Item .env.example .env
```

| Variáveis | Finalidade |
|---|---|
| DATABASE_PASSWORD | Senha do PostgreSQL no Compose |
| JWT_SECRET | Segredo JWT, no mínimo 32 bytes |
| S3_ACCESS_KEY, S3_SECRET_KEY, S3_BUCKET | Credenciais e bucket privado; root MinIO somente no desenvolvimento local |
| PUBLIC_BASE_URL | Origem do frontend usada nos links `/acompanhar#token` |
| APP_BOOTSTRAP_ENABLED | Ativa explicitamente criação da primeira oficina |
| APP_BOOTSTRAP_SLUG, APP_BOOTSTRAP_NOME | Identificador e nome da oficina |
| APP_BOOTSTRAP_EMAIL, APP_BOOTSTRAP_SENHA | OWNER inicial; senha 12–72 caracteres e até 72 bytes UTF-8 |

Bootstrap cria oficina/OWNER somente quando o slug não existe; não redefine senha existente. Depois de provisionar, desative o bootstrap e retire a senha inicial da configuração do serviço. Não há cadastro público de oficinas nesta fase.

### 2. PostgreSQL, MinIO e API

Na raiz:

```sh
docker compose --env-file .env build
docker compose --env-file .env up -d
docker compose --env-file .env ps -a
```

Na validação local foi usado `--env-file .env.example` em ambiente de desenvolvimento. O arquivo de exemplo contém placeholders e não é configuração de produção.

| Serviço | Porta no host | Estado esperado |
|---|---|---|
| postgres | 127.0.0.1:5432 | healthy |
| minio | 127.0.0.1:9000 / console 9001 | ativo |
| minio-init | — | Exited (0), bucket sem acesso anônimo |
| api | 127.0.0.1:8080 | ativo, health 200 |

O Compose preserva dados em volumes nomeados. Não remover volumes para atualizar a API. O Dockerfile atual instala `unzip`, usa JDK/JRE 25 e executa como usuário não root; seu build pula testes, que devem ser verificados separadamente por Maven verify.

### 3. Frontend

```sh
cd frontend
npm ci
npm run dev -- --host 127.0.0.1 --port 5173
```

No PowerShell, use `npm.cmd` se `npm.ps1` for bloqueado. Acesse a URL informada pelo Vite, normalmente http://127.0.0.1:5173. O proxy `/api` de desenvolvimento e preview aponta para http://127.0.0.1:8080, evitando CORS no ambiente local. Produção precisará servir frontend e API na mesma origem, com encaminhamento de `/api` e fallback SPA para `/acompanhar`; nenhum deploy foi configurado nesta entrega.

Entre com slug/e-mail/senha da oficina provisionada. O papel vem do backend. Tokens ficam apenas em memória; recarregar pede novo login, mas os registros salvos continuam no PostgreSQL. Logout revoga refresh e limpa dados da oficina; JWT já emitido expira em até 15 minutos conforme contrato existente.

### Endereços

- [Health](http://127.0.0.1:8080/actuator/health): 200 quando saudável.
- [Swagger UI](http://127.0.0.1:8080/swagger-ui/index.html).
- [OpenAPI JSON](http://127.0.0.1:8080/v3/api-docs): 32 operações de negócio.
- API: `/api/v1/`; autenticação por Bearer para recursos internos.
- Acompanhamento: `/acompanhar#token`, com resumo limitado e aprovação/recusa integral.

## Fluxos disponíveis

- Clientes/veículos: cadastrar, consultar, editar com revisão; busca por nome/placa.
- Equipe: listar e cadastrar por OWNER; atribuição a mecânico ativo.
- OS: abrir com dados de entrada, atribuir responsável, avançar status e concluir.
- Checklist único e diagnóstico aditivo classificado.
- Fotos PNG/JPEG privadas: upload real, metadados no banco e download autenticado. O navegador busca bytes com Bearer e usa Blob URL temporária; URLs MinIO diretas não são públicas.
- Orçamento: versão e todos os itens criados juntos; totais calculados no backend; histórico imutável.
- Links: emitir, copiar e revogar o último link conhecido; página pública com decisão, conflito e atualização.
- Timeline, dashboard e busca sobre dados reais carregados. “Atualizar dados” recarrega do servidor; não há tempo real.

Limites importantes:

- As listas são carregadas percorrendo páginas de 100; a interface apresenta dez por página e busca localmente. Sem truncamento na primeira página; carga integral e consultas de versões por OS ainda precisam de avaliação de escala.
- Não existem filtros REST de status/data/responsável, edição geral de OS, cancelamento/reabertura, aprovação parcial, edição de checklist ou edição/exclusão de fotos/diagnósticos.
- Sessão não fornece nome da oficina; cabeçalho mostra slug. Não há listagem de links ou recuperação do token após emissão; copie o link imediatamente.
- Equipe não oferece edição/desativação/reset de senha. Aditivo nesta fase é uma nova versão integral antes da execução, conforme estados permitidos.
- Agenda, pátio, estoque completo, Dinheiro Esquecido, IA, voz, WhatsApp API, financeiro, fiscal e pagamentos estão fora da Fase 1. Referências visuais preexistentes não significam implementação desses módulos.

## Testes

Backend, dentro de `backend`, com JDK 25 e Docker funcional:

```sh
./mvnw -B -ntp verify
```

PowerShell: `.\mvnw.cmd -B -ntp verify`. Maven verify executa unitários, integração via Failsafe, empacotamento e Spotless. Testcontainers cria PostgreSQL/MinIO descartáveis e aplica Flyway; ausência de Docker falha, sem ignorar a suíte. Para formatar Java: `./mvnw spotless:apply`.

Nesta máquina foi usado Maven portátil já instalado em `.tools/apache-maven-3.9.11`, JDK em `.tools/jdk-25.0.4.1` e cache `.tools/m2`; o comando e resultados estão em [finalização](docs/fase1-finalizacao.md). Isso não altera o Maven Wrapper versionado.

Variáveis `TEST_DATABASE_URL`, `TEST_DATABASE_USER`, `TEST_DATABASE_PASSWORD` habilitam a alternativa com banco externo **exclusivo de testes**, mas esse modo substitui storage por memória e não comprova S3. A validação final usou Testcontainers, sem essa alternativa. Nunca apontar a suíte a banco operacional.

Frontend, dentro de `frontend`:

```sh
npm run lint
npm run typecheck
npm test
npm run build
```

Roteiros reais adicionais: `tests/e2e-real.mjs` e `tests/public-real.mjs`, com Playwright/Chromium e duas oficinas de teste. Veja [pré-requisitos e fixture](docs/fase1-finalizacao.md#relatórios-e-reprodução). Não fazem parte de `npm test`, pois exigem infraestrutura ativa e criam dados persistentes de teste.

Relatórios: `backend/target/surefire-reports`, `backend/target/failsafe-reports` e `.tools/fase1-e2e` local. O workflow `.github/workflows/ci.yml` executa backend/containers e build Docker; não foi acionado remotamente nesta tarefa.

## Desenvolvimento e segurança operacional

Não trabalhar diretamente na main. Conferir `git status`, branch e histórico antes de alterar. Esta finalização está em `chore/finalize-fase-1` para revisão, sem commit, push ou merge.

Não editar migrations já aplicadas. Scripts em `backend/db/rollback/` servem somente a schema vazio/descartável, não a dados operacionais. Staging/produção exigem destino, TLS, credenciais restritas e estratégia de backup/restauração; nada foi publicado automaticamente.

Não colocar senhas, JWT, refresh, tokens públicos, chaves S3 ou dados privados em Git ou logs. `.env`, `.tools`, dependências, builds e relatórios são ignorados. O token público no fragmento não é enviado ao servidor de páginas, mas permite acesso a quem o possui.
