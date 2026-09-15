# Garagem SaaS

SaaS profissional para oficinas mecânicas. A Fase 1 organiza clientes, veículos e ordens de serviço, do recebimento ao diagnóstico, orçamento, aprovação e conclusão.

## Visão do produto

A interface atende à operação diária de proprietários, atendentes e mecânicos, com foco em clareza e rapidez. O escopo atual é o núcleo operacional da oficina. Recuperação de clientes, IA, voz, pós-venda, cobrança, financeiro empresarial, estoque complexo, emissão fiscal e WhatsApp API não fazem parte desta fase.

## Estado atual do projeto

| Área | Estado |
|---|---|
| Backend da Fase 1 | Implementado: autenticação, cadastros, OS, checklist, diagnóstico, orçamento versionado, aprovação pública, fotos e timeline |
| Docker | Validação bem-sucedida de PostgreSQL, MinIO, API e minio-init informada pelo responsável pelo projeto |
| Health da API | HTTP 200 confirmado em `http://127.0.0.1:8080/actuator/health` na revisão deste README, em 15/09/2026 |
| Frontend | Protótipo React + TypeScript + Vite, com dados fictícios em memória |
| Integração frontend ↔ API | Pendente; login, sessão, cadastros e operações da interface ainda são simulações locais |
| Interface como produto | Experiência para avaliação; não representa uma funcionalidade de produção concluída |
| CI | Workflow de backend e build de imagem configurado; resultado remoto não conferido nesta revisão |
| Staging / deploy | Ainda não configurado |
| Próximo grande trabalho | Integrar o frontend aprovado com a API real, gradualmente |

### Situação do checkout e das evidências

Na revisão de 15/09/2026, a branch local é `main`. O diretório `frontend/`, `docs/prototipo-fase-1.md` e os arquivos npm da raiz ainda estão **não rastreados pelo Git**; `docs/proposta-interface.md` tem alteração local. Um clone não recebe arquivos não commitados/publicados. Antes do handoff, a equipe precisa disponibilizar o trabalho pela branch e pelo Pull Request apropriados. Esta revisão documental não faz commit, push ou merge.

Existem diferenças entre o histórico e o checkout:

- [Aceite de 14/09](docs/aceite-fase-1.md) registra indisponibilidade de virtualização e Docker pendente; esse é o registro anterior à validação bem-sucedida informada pelo responsável.
- Foi informado que o build exigiu instalar `unzip` para executar o Maven Wrapper. **O [Dockerfile presente neste checkout](backend/Dockerfile) não contém essa instalação.** Conferir a versão usada na validação e sua disponibilização no repositório antes de assumir que um build limpo reproduz aquele resultado.
- O health foi conferido nesta revisão, mas o inventário dos containers não pôde ser consultado por falta de acesso ao Docker Engine nesta sessão. HTTP 200 confirma a API em execução, não comprova que sua imagem corresponde ao Dockerfile atual.
- A documentação mais antiga ainda menciona a interface como não implementada. Agora existe o protótipo descrito em [proposta de interface](docs/proposta-interface.md) e [registro do protótipo](docs/prototipo-fase-1.md). A aprovação final e a integração permanecem pendentes nesses documentos.

## Stack

| Camada | Tecnologias presentes |
|---|---|
| Backend | Java 25, Spring Boot 3.5.16, Maven Wrapper 3.9.11 |
| API e segurança | Spring Web REST/JSON, Spring Security/JWT, Bean Validation, OpenAPI via springdoc |
| Persistência | PostgreSQL 17.11 no Compose, Spring Data JPA/Hibernate e Flyway |
| Testes | JUnit 5 e Mockito via spring-boot-starter-test, Spring Security Test e Testcontainers |
| Frontend | React 19, TypeScript 6 e Vite 8; versões/resoluções em frontend/package.json e package-lock.json |
| Fotos | Armazenamento privado compatível com S3; SDK AWS S3 e MinIO local |
| Infraestrutura | Docker, Docker Compose e GitHub Actions |
| Qualidade | Spotless/Google Java Format no backend; TypeScript e Oxlint no frontend |

## Arquitetura

**Modular Monolith:** um único backend organizado por funcionalidade, com Controllers, DTOs, serviços transacionais, domínio e repositórios. API REST/JSON sob `/api/v1/`.

O sistema é multi-tenant por oficina (workshop). O backend é a fonte de verdade para autorização, isolamento, revisões, transições, totais e decisões. Filtros Hibernate e chaves estrangeiras compostas reforçam o isolamento. Ocultar botões no frontend não substitui segurança no servidor.

Papéis reais: `OWNER`, `ATENDENTE`, `MECANICO`. Não existe papel `GERENTE`.

Fluxo principal:

```text
RECEBIDO → DIAGNOSTICO → ORCAMENTO → AGUARDANDO_APROVACAO
                                          ↓ aprovação do cliente
                                    EM_MANUTENCAO → TESTE → PRONTO
```

Manutenção permite espera de peça e retorno. Revisão ou recusa retorna a Orçamento. Versões, itens, decisões e eventos preservam histórico; o total definitivo é calculado pelo backend.

As migrations ficam em `backend/src/main/resources/db/migration/`. O Hibernate usa `ddl-auto=validate`. Não editar migrations já aplicadas. Os scripts em `backend/db/rollback/` são destinados a base vazia/descartável; não são um procedimento seguro de rollback sobre dados de produção.

Detalhes: [arquitetura](docs/arquitetura.md).

## Estrutura do repositório

```text
garagem-saas/
├── README.md
├── compose.yml
├── .env.example
├── .github/workflows/         # CI de backend e imagem Docker
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   ├── mvnw / mvnw.cmd
│   ├── .mvn/wrapper/
│   ├── db/rollback/
│   └── src/
│       ├── main/java/br/com/garagem/
│       │   ├── auth/ cliente/ veiculo/ usuario/ oficina/
│       │   ├── ordemservico/
│       │   └── tenancy/ shared/ config/
│       ├── main/resources/   # application.yml e migrations
│       └── test/java/
├── frontend/
│   ├── package.json / package-lock.json
│   ├── vite.config.ts
│   ├── README.md
│   └── src/
│       ├── App.tsx           # Login, listas, navegação e estado local
│       ├── OrderDetail.tsx   # Abas e operações simuladas da OS
│       ├── forms.tsx / ui.tsx
│       └── model.ts          # Tipos e dados fictícios
└── docs/
    ├── aceite-fase-1.md
    ├── arquitetura.md
    ├── proposta-interface.md
    └── prototipo-fase-1.md
```

A estrutura descreve o workspace atual; observe a pendência de versionamento indicada acima. `.tools/`, dependências, artefatos compilados e `.env` são ignorados pelo Git. Os arquivos npm da raiz não oferecem scripts da aplicação: execute os comandos do frontend dentro de `frontend/`.

Não foi encontrado `AGENTS.md` nesta revisão. Caso exista na branch recebida, leia-o antes de alterar o projeto.

## Pré-requisitos

- Git e acesso ao repositório.
- Docker Engine/Desktop funcional com containers Linux e Docker Compose para a infraestrutura local e Testcontainers.
- Node.js e npm para o frontend. O Vite instalado declara Node `^20.19.0 || >=22.12.0`; respeite também as exigências das demais dependências do lockfile.
- JDK 25 e `JAVA_HOME` configurado somente para executar/testar o backend no host. Maven global não é necessário: use o Wrapper.
- Acesso aos registries de imagens e dependências no primeiro build.

O Compose não executa o frontend. Ele precisa ser iniciado separadamente com npm.

## Como executar

### 1. Preparar a configuração local

Use [.env.example](.env.example) como referência. Copie-o para `.env`, caso ainda não tenha configuração local, e substitua os placeholders `CHANGE_ME`. Não versionar `.env` nem colocar credenciais no README.

PowerShell:

```powershell
Copy-Item .env.example .env
```

Shell POSIX:

```sh
cp .env.example .env
```

Para a primeira oficina, configure `APP_BOOTSTRAP_ENABLED` e os campos `APP_BOOTSTRAP_*`. O bootstrap cria oficina e OWNER apenas quando o slug ainda não existe; não redefine senha nem altera uma oficina existente. Após provisionar, desative o bootstrap e retire a senha inicial da configuração usada pelo serviço.

### 2. Subir a infraestrutura e a API

Na raiz, usando a configuração local:

```sh
docker compose --env-file .env up -d --build
docker compose --env-file .env ps -a
```

A forma com o arquivo de exemplo, informada no fluxo de validação local, é:

```sh
docker compose --env-file .env.example up -d --build
docker compose --env-file .env.example ps -a
```

`.env.example` contém placeholders e serve para desenvolvimento descartável. Prefira `.env` com valores próprios. Use o mesmo arquivo de ambiente nos comandos de gerenciamento. Confira a divergência do Dockerfile descrita em **Estado atual do projeto** se o build falhar no Wrapper.

### 3. Conferir a API

Abra [health](http://127.0.0.1:8080/actuator/health) e confirme HTTP 200. A documentação interativa está no [Swagger UI](http://127.0.0.1:8080/swagger-ui/index.html).

### 4. Iniciar o frontend em outro terminal

```sh
cd frontend
npm ci
npm run dev -- --host 127.0.0.1
```

O lockfile permite `npm ci`; `npm install` também instala as dependências, podendo atualizar o lockfile. O script `npm run dev` chama Vite. A configuração atual não fixa porta, host ou proxy de API; o argumento acima fixa o host local.

Abra a URL exibida pelo Vite, normalmente **http://127.0.0.1:5173**. Se a porta estiver ocupada, ele poderá escolher outra. No PowerShell, use `npm.cmd` no lugar de `npm` se a política de execução bloquear `npm.ps1`.

O protótipo pode ser visualizado sem Docker. Subir a API não faz o frontend passar a consumir dados reais automaticamente.

## Backend

### Recursos disponíveis

- Login por oficina/e-mail/senha; access token, refresh rotativo e logout.
- Clientes e veículos com cadastro, consulta, edição e revisão.
- OS, responsável, status, checklist de entrada e diagnóstico.
- Orçamentos imutáveis por versão, com aprovação/recusa integral.
- Fotos privadas, timeline e links públicos com expiração/revogação.
- Listagem de usuários e cadastro pelo OWNER.

### Endereços locais

| Recurso | Endereço |
|---|---|
| API | http://127.0.0.1:8080/api/v1/ |
| Health | http://127.0.0.1:8080/actuator/health |
| Swagger UI | http://127.0.0.1:8080/swagger-ui/index.html |
| OpenAPI JSON | http://127.0.0.1:8080/v3/api-docs |

Use `POST /api/v1/auth/login` com a oficina provisionada para obter a sessão. Endpoints protegidos usam o access token; refresh e logout recebem os campos definidos nos DTOs. O acesso público usa o token do link e não exige JWT.

### Executar no host

Com PostgreSQL e armazenamento S3/MinIO disponíveis, exporte as variáveis necessárias no processo. **Spring Boot não carrega o arquivo .env automaticamente.** Ao acessar os serviços do Compose a partir do host, utilize os endereços locais, não os nomes internos `postgres` e `minio`.

Dentro de `backend/`:

```sh
./mvnw spring-boot:run
```

No PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

Não execute uma segunda API na mesma porta de uma API já ativa. O Wrapper baixa Maven 3.9.11 conforme suas propriedades.

## Frontend

Os scripts reais em [frontend/package.json](frontend/package.json):

| Comando, dentro de frontend/ | Função |
|---|---|
| npm run dev | Servidor Vite de desenvolvimento |
| npm run build | TypeScript seguido do build Vite |
| npm run lint | Oxlint |
| npm run preview | Prévia local do build gerado |

A aplicação mantém dados fictícios em memória. Sair ou recarregar descarta alterações. Não há autenticação real, persistência, envio de fotos à API nem integração de tokens. O seletor de papel no login serve apenas para avaliar a interface; em produção, o papel deverá vir da sessão.

## Docker

[compose.yml](compose.yml) define:

| Serviço | Papel | Porta no host |
|---|---|---|
| postgres | PostgreSQL com volume e healthcheck | 127.0.0.1:5432 |
| minio | Armazenamento de fotos e console | 127.0.0.1:9000 / 9001 |
| minio-init | Cria o bucket e desabilita acesso anônimo | Sem porta |
| api | Build do backend; espera banco saudável e inicialização do bucket | 127.0.0.1:8080 |

`minio-init` é um serviço de inicialização: **Exited (0)** é o resultado esperado após sucesso. Ele não precisa permanecer em execução. PostgreSQL e MinIO usam volumes nomeados para persistência.

O Dockerfile usa estágios JDK 25 para build e JRE 25 para execução, com usuário não root. O build da imagem usa `-DskipTests`; a aprovação dos testes vem do comando `verify`, não apenas do build Docker.

O Compose é de desenvolvimento local, com portas vinculadas ao localhost; não é configuração de produção ou deploy. A instalação de `unzip` mencionada na validação precisa ser conciliada com o Dockerfile atual, sem presumir que já está versionada.

## Variáveis de ambiente

Somente nomes e finalidade são apresentados aqui. Valores devem ser configurados localmente a partir de [.env.example](.env.example).

| Variáveis | Finalidade |
|---|---|
| DATABASE_PASSWORD | Senha do PostgreSQL usada pelo Compose e pela API |
| JWT_SECRET | Segredo de assinatura; ao menos 32 bytes conforme configuração da aplicação |
| S3_ACCESS_KEY, S3_SECRET_KEY | Credenciais do armazenamento; no Compose também inicializam o MinIO |
| S3_BUCKET | Nome do bucket privado |
| PUBLIC_BASE_URL | Base da URL de acompanhamento gerada pelo backend |
| APP_BOOTSTRAP_ENABLED | Ativa explicitamente o provisionamento inicial |
| APP_BOOTSTRAP_SLUG, APP_BOOTSTRAP_NOME | Identificador e nome da oficina inicial |
| APP_BOOTSTRAP_EMAIL, APP_BOOTSTRAP_SENHA | Conta OWNER inicial; senha de ao menos 12 caracteres e até 72 bytes UTF-8 |

Variáveis adicionais reconhecidas pelo código/configuração:

| Variáveis | Uso |
|---|---|
| DATABASE_URL, DATABASE_USER | Conexão JDBC; o Compose já fornece valores internos à API |
| S3_ENDPOINT | Endpoint S3; o Compose fornece o endereço interno do MinIO |
| S3_REGION | Região do armazenamento; configurável em application.yml |
| TEST_DATABASE_URL, TEST_DATABASE_USER, TEST_DATABASE_PASSWORD | Alternativa explícita de banco externo exclusivo para testes |

Não colocar senhas, tokens, segredos JWT ou chaves S3 em commits, documentação ou logs. A URL real gerada usa `/acompanhar#token`; o protótipo ainda não implementa essa rota pública integrada. Sua prévia do cliente fica em painel local, sem link compartilhável.

## Testes

Com JDK 25 e Docker funcional, dentro de `backend/`:

```sh
./mvnw -B -ntp verify
```

PowerShell:

```powershell
.\mvnw.cmd -B -ntp verify
```

O comando executa testes unitários, integração via Failsafe, empacotamento e Spotless. A suíte de integração usa PostgreSQL e MinIO via Testcontainers quando `TEST_DATABASE_URL` não está definida. Ausência de Docker causa falha nesse caminho, sem ignorar os testes.

Para aplicar a formatação Java:

```sh
./mvnw spotless:apply
```

A alternativa `TEST_DATABASE_*` exige um banco exclusivo de testes. Nesse modo, fotos usam armazenamento em memória, sem validar S3/MinIO. A suíte aplica migrations e grava fixtures; não usar base operacional ou de produção.

Relatórios: `backend/target/surefire-reports/` e `backend/target/failsafe-reports/`. O histórico de aceite registra quatro testes unitários e dez de integração aprovados no caminho local com PostgreSQL.

Para o frontend:

```sh
cd frontend
npm run build
npm run lint
```

Build, lint e navegação em Chromium foram verificados na etapa de prototipação; veja [registro](docs/prototipo-fase-1.md). O roteiro em `.tools/` depende de instalação local, é ignorado pelo Git e não constitui uma suíte portável. Não existe script `npm test` no frontend.

O [CI](.github/workflows/ci.yml) configura Java 25, roda `verify`, compila a imagem da API e publica relatórios. Ainda não há job de frontend ou deploy nesse workflow. Validação local do Compose não deve ser confundida com execução aprovada do CI/Testcontainers.

## Protótipo da interface

Disponível no workspace:

- Login e menu com OS, Clientes, Veículos e Equipe conforme papel.
- Lista de OS com busca e detalhe com **Resumo, Checklist, Diagnóstico, Orçamento, Fotos e Timeline**.
- Formulários em painéis laterais.
- Orçamento com versões, valores, total e situação.
- Prévia da aprovação/recusa integral pelo cliente.
- Galeria de fotos locais e permissões visuais.

Para avaliar, entre com os campos fictícios preenchidos e abra a **OS #1048**. Ela reúne checklist, diagnóstico e duas versões de orçamento. Para comparar permissões, saia e entre como outro papel. O backend continua responsável pela segurança quando a integração for feita.

## Limitações conhecidas

- `OsSaida` retorna IDs de cliente, veículo e mecânico, sem nomes/placa; a interface integrada precisa resolver essas relações respeitando oficina e paginação.
- A sessão informa `oficinaId`, mas não o nome da oficina necessário ao cabeçalho.
- Checklist é registrado uma única vez, sem edição; diagnóstico permite acréscimo, sem edição/exclusão.
- Equipe dispõe de listagem e cadastro pelo OWNER, sem busca, edição, desativação, exclusão ou redefinição de senha.
- Busca de OS: placa, cliente ou número. Clientes: nome. Veículos: placa. Não inventar filtros ausentes na API.
- OS não tem edição geral de relato/previsão, cancelamento ou reabertura. Responsável deve ser mecânico ativo.
- Fotos não têm edição/exclusão. Links são emitidos com token exibido uma vez, sem endpoint de listagem.
- Aprovação é integral e vinculada à versão atual; erros de revisão/decisão conflitante retornam 409.
- Após recusa, o resumo público pode omitir o orçamento porque a OS retorna à etapa Orçamento.
- O protótipo não valida rede, concorrência real, autenticação, isolamento ou armazenamento privado ponta a ponta.

Consulte o detalhamento em [proposta de interface](docs/proposta-interface.md). Nenhuma regra crítica deve ser implementada somente no frontend.

## Fluxo de desenvolvimento

Manter `main` estável. Não trabalhar diretamente nela. Antes de trocar de branch, execute `git status` e preserve alterações locais; não descarte trabalho existente.

Com a árvore de trabalho preparada:

```sh
git switch main
git pull origin main
git switch -c feature/integracao-autenticacao
```

Use prefixos `feature/...`, `fix/...` ou `chore/...`, conforme a tarefa. A branch acima é um exemplo para o próximo trabalho, não uma branch criada nesta revisão.

Após implementar, revisar o diff e executar as verificações pertinentes:

```sh
git status
git diff
git add .
git diff --cached
git commit -m "feat(auth): integrar login com a API"
git push -u origin feature/integracao-autenticacao
```

Revise todos os arquivos antes de adicionar alterações, especialmente arquivos novos e configuração local. Usamos **Conventional Commits**, por exemplo `feat(...)`, `fix(...)`, `chore(...)` e `docs(...)`.

Depois, abra Pull Request para `main`, descreva mudança e validação, aguarde revisão e confira o CI antes do merge. Os comandos acima documentam o fluxo da equipe; não foram executados como ações de commit/publicação nesta tarefa.

## Próximos passos

O próximo grande trabalho é **integrar o frontend aprovado com a API real**, em etapas revisáveis:

1. Autenticação real.
2. Sessão, access token, refresh e logout; limpar dados ao trocar/sair da oficina.
3. Clientes.
4. Veículos.
5. Ordens de Serviço.
6. Checklist.
7. Diagnóstico.
8. Orçamento e versões.
9. Aprovação pública.
10. Fotos privadas.
11. Timeline.
12. Usuários/equipe conforme capacidades e papéis reais.

Em cada etapa, substituir mocks, tratar carregamento e erros, validar permissões no servidor e lidar com revisões/409. Não alterar arquitetura ou stack sem discussão. Não implementar funcionalidades das fases posteriores.

## Documentação

| Documento | Conteúdo |
|---|---|
| [Aceite da Fase 1](docs/aceite-fase-1.md) | Evidências e pendências históricas de 14/09; ler junto das atualizações de estado deste README |
| [Arquitetura](docs/arquitetura.md) | Isolamento, segurança, papéis, persistência e regras |
| [Proposta de interface](docs/proposta-interface.md) | UX, correspondência com endpoints e limites |
| [Protótipo da Fase 1](docs/prototipo-fase-1.md) | Escopo, verificações e limitações da avaliação |
| [README do frontend](frontend/README.md) | Execução e roteiro visual |
| [Compose](compose.yml) / [ambiente de exemplo](.env.example) | Infraestrutura e nomes de configuração local |

## Continuando o desenvolvimento

1. Clone pela URL fornecida pela equipe ou atualize o repositório existente. Não há URL de clone fixada neste documento.
2. Confira `git status`, branch e tarefa. Verifique se recebeu o frontend e os documentos citados: arquivos locais não rastreados não acompanham um clone.
3. Leia este README, `AGENTS.md` se existir, e os quatro documentos de `docs/` listados acima.
4. Prepare o ambiente local e suba Docker com o Compose. Confira `ps -a`; `minio-init` deve concluir com código zero.
5. Suba o frontend em terminal separado, dentro de `frontend/`.
6. Confirme HTTP 200 em `/actuator/health` e abra o Swagger para consultar o contrato real.
7. Explore o protótipo sabendo que ele ainda usa mocks e perde dados ao recarregar.
8. Confirme a aprovação da interface aplicável à tarefa e comece pela integração frontend ↔ API, inicialmente autenticação/sessão.
9. Preserve arquitetura, stack e segurança multi-tenant; discuta alterações estruturais antes de implementá-las.
10. Trabalhe em branch própria, mantenha o escopo na Fase 1 e entregue a mudança via Pull Request.
