# Garagem SaaS

SaaS para oficinas mecânicas.

O Garagem SaaS organiza a operação da oficina desde o cadastro do cliente e do veículo até o diagnóstico, orçamento, aprovação, execução e conclusão da ordem de serviço.

O projeto utiliza arquitetura de **monólito modular**, API REST, frontend React e isolamento **multi-tenant por oficina**.

---

# Estado atual

## ✅ Fase 1 — Concluída

A Fase 1 do Garagem SaaS foi concluída e validada.

O núcleo operacional está funcional com:

- frontend integrado à API real;
- PostgreSQL;
- MinIO privado para fotos;
- autenticação;
- usuários e permissões;
- clientes;
- veículos;
- ordens de serviço;
- checklist;
- diagnóstico;
- fotos;
- orçamento versionado;
- aprovação pública;
- timeline;
- isolamento entre oficinas;
- Docker Compose;
- testes de integração.

Fluxo principal validado:

```text
Login
  ↓
Cliente
  ↓
Veículo
  ↓
Ordem de Serviço
  ↓
Checklist
  ↓
Diagnóstico
  ↓
Fotos
  ↓
Orçamento
  ↓
Aprovação
  ↓
Execução / Status
  ↓
Timeline
  ↓
Conclusão
```

---

## 🚧 Fase 2 — Em andamento

A Fase 2 geral ainda não está encerrada.

A frente de **API, contratos, validação funcional, segurança e infraestrutura**, sob responsabilidade de Cauã, foi concluída, testada e integrada à `main`.

PR integrado:

```text
#7 — feature/fase2-api-contracts-security
```

Commit principal da entrega:

```text
feat: finalize phase 2 api security and infrastructure
```

A `main` já contém essa implementação.

---

# ✅ Fase 2 — Frente de API, Segurança e Infraestrutura

Esta frente está concluída.

## API e contratos

Foram concluídos:

- inventário dos endpoints existentes;
- estabilização dos contratos REST;
- revisão de DTOs;
- respostas de erro padronizadas;
- validações padronizadas;
- paginação;
- filtros;
- ordenação;
- allowlist de campos de ordenação;
- endpoints/respostas para dashboard;
- atualização do Swagger/OpenAPI.

O inventário atual possui:

```text
33 operações
23 caminhos
```

A API utiliza o prefixo:

```text
/api/v1
```

---

## Paginação

As principais listagens utilizam paginação no backend.

Formato:

```text
?page=0&size=20
```

Envelope padrão:

```json
{
  "itens": [],
  "pagina": 0,
  "tamanho": 20,
  "total": 0,
  "totalPaginas": 0
}
```

O frontend não deve carregar toda a base apenas para implementar paginação visual.

---

## Filtros

Os filtros são executados pelo backend e respeitam obrigatoriamente a oficina autenticada.

Foram implementados filtros para os recursos aplicáveis, incluindo ordens de serviço.

Filtros opcionais não são enviados ao PostgreSQL como predicados nulos desnecessários.

A listagem de ordens de serviço utiliza construção dinâmica da consulta no repositório.

---

## Ordenação

Campos enviados pelo cliente não são concatenados livremente na consulta.

A ordenação utiliza uma **allowlist** de campos permitidos.

Formato conceitual:

```text
?sort=numero,desc
```

---

# Correção importante da Fase 2

Durante a validação foi identificado um erro no endpoint:

```text
GET /api/v1/ordens-servico
```

O PostgreSQL retornava:

```text
SQLState: 42P18
ERROR: could not determine data type of parameter $22
```

O parâmetro `$22` correspondia ao filtro opcional:

```text
de
```

utilizado no recorte inferior de período por `criadoEm`.

A implementação anterior utilizava:

```text
(:de IS NULL OR ...)
```

Quando `de` era `null`, o PostgreSQL não conseguia determinar o tipo do parâmetro temporal.

A consulta foi corrigida para incluir o filtro somente quando o valor é informado.

Cadeia atual:

```text
OsController
  ↓
OsService
  ↓
OrdemServicoRepositoryCustom
  ↓
OrdemServicoRepositoryImpl
```

Arquivos relacionados:

```text
backend/src/main/java/br/com/garagem/ordemservico/repository/OrdemServicoRepository.java
backend/src/main/java/br/com/garagem/ordemservico/repository/OrdemServicoRepositoryCustom.java
backend/src/main/java/br/com/garagem/ordemservico/repository/OrdemServicoRepositoryImpl.java
backend/src/main/java/br/com/garagem/ordemservico/application/OsService.java
```

Não voltar para o padrão:

```text
(:param IS NULL OR ...)
```

para filtros temporais opcionais sem antes validar o comportamento no PostgreSQL.

---

# Validação funcional

Foram testados na Fase 2:

- login;
- refresh token;
- logout;
- clientes;
- veículos;
- ordens de serviço;
- responsáveis;
- checklist;
- diagnóstico;
- fotos;
- orçamentos;
- aprovação pública;
- timeline;
- paginação;
- filtros;
- ordenação;
- erros;
- isolamento entre oficinas;
- permissões.

---

# Segurança

## Multi-tenancy

O isolamento é sempre responsabilidade do backend.

Cada usuário autenticado pertence a uma oficina.

Uma oficina não pode:

- consultar dados de outra;
- alterar dados de outra;
- associar recursos pertencentes a outra;
- acessar fotos de outra;
- utilizar usuários ou entidades pertencentes a outro tenant.

Nunca confiar no frontend para definir `oficinaId`.

A oficina deve ser determinada pelo contexto autenticado.

---

## Papéis

Papéis atuais:

```text
OWNER
ATENDENTE
MECANICO
```

A matriz completa está em:

```text
docs/permissoes.md
```

---

## Autenticação

O backend possui:

- login;
- access token;
- refresh token;
- logout;
- revogação;
- expiração;
- verificação de usuário ativo;
- contexto de oficina;
- autorização por papel.

Tokens e credenciais nunca devem aparecer em logs ou no Git.

---

## Uploads

Fotos são privadas.

O backend valida o upload antes do armazenamento.

Tipos de arquivo não suportados utilizam:

```text
415 Unsupported Media Type
```

Requests inválidos utilizam:

```text
400 Bad Request
```

Arquivos acima do limite devem utilizar:

```text
413 Payload Too Large
```

O MinIO não deve possuir acesso anônimo ao bucket privado.

---

## Respostas de erro

O backend utiliza resposta padronizada baseada em Problem Details / RFC 7807.

Exemplo conceitual:

```json
{
  "type": "https://garagem.com.br/erros/validation_error",
  "title": "Bad Request",
  "status": 400,
  "detail": "nome: não deve estar em branco",
  "instance": "/api/v1/clientes",
  "code": "VALIDATION_ERROR",
  "timestamp": "2026-09-15T12:00:00Z",
  "requestId": "..."
}
```

Erros de validação também podem possuir estrutura detalhada de campos.

Documentação:

```text
docs/api-errors.md
```

Nunca retornar ao cliente:

```text
stack trace
SQL interno
senha
hash
JWT secret
access token
refresh token
credenciais de storage
detalhes internos desnecessários
```

---

# Dashboard

Foi criada estrutura própria no backend para o dashboard.

Arquivos:

```text
backend/src/main/java/br/com/garagem/dashboard/api/DashboardController.java
backend/src/main/java/br/com/garagem/dashboard/api/DashboardDtos.java
backend/src/main/java/br/com/garagem/dashboard/application/DashboardService.java
```

O frontend deve utilizar os contratos do dashboard em vez de carregar toda a base e calcular todos os indicadores localmente.

---

# Stack

## Backend

```text
Java 25
Spring Boot 3.5.x
Maven
Spring Web
Spring Security
Spring Data JPA
Hibernate
Bean Validation
Spring Boot Actuator
Flyway
OpenAPI / Swagger
JUnit 5
Testcontainers
```

## Frontend

```text
React
TypeScript
Vite
```

## Dados e infraestrutura

```text
PostgreSQL 17
MinIO / S3
Docker
Docker Compose
GitHub Actions
```

---

# Arquitetura

O Garagem SaaS utiliza **monólito modular**.

Não transformar o projeto em microserviços sem necessidade arquitetural concreta.

Estrutura conceitual:

```text
Controller
  ↓
DTO
  ↓
Service / Application
  ↓
Domain
  ↓
Repository
  ↓
PostgreSQL
```

O backend é a fonte de verdade para:

```text
regras de negócio
autenticação
autorização
multi-tenancy
validação
status
conflitos
versionamento
persistência
```

O frontend deve consumir essas regras, não duplicá-las como regras de segurança.

Mais detalhes:

```text
docs/arquitetura.md
```

---

# Fluxo da Ordem de Serviço

Fluxo principal:

```text
RECEBIDO
   ↓
DIAGNOSTICO
   ↓
ORCAMENTO
   ↓
AGUARDANDO_APROVACAO
   ↓
EM_MANUTENCAO
   ↓
TESTE
   ↓
PRONTO
```

Recusa ou necessidade de revisão pode retornar a OS ao fluxo de orçamento conforme as regras existentes.

Orçamentos são versionados e o histórico deve permanecer preservado.

---

# Funcionalidades disponíveis

## Clientes

```text
cadastro
consulta
edição
busca
paginação
filtros
ordenação
```

## Veículos

```text
cadastro
consulta
edição
vínculo com cliente
busca por placa
paginação
filtros
ordenação
```

## Equipe

```text
listagem
cadastro
papéis
atribuição de responsável
controle de acesso
```

## Ordem de Serviço

```text
abertura
consulta
listagem
responsável
status
paginação
filtros
ordenação
controle de revisão
timeline
```

## Checklist

```text
criação
consulta
vínculo com OS
```

## Diagnóstico

```text
registro
classificação
histórico
```

## Fotos

```text
upload real
MinIO privado
metadata no PostgreSQL
download autenticado
isolamento por oficina
```

## Orçamento

```text
versionamento
itens
cálculo no backend
histórico imutável
conflito de revisão
```

## Aprovação pública

```text
link público
token
expiração
aprovação
recusa
proteção contra decisão duplicada
```

## Timeline

```text
eventos persistidos
ordenação cronológica
histórico da OS
```

---

# Documentação

Antes de continuar o desenvolvimento, leia principalmente:

| Documento | Conteúdo |
|---|---|
| [docs/fase2-caua.md](docs/fase2-caua.md) | Estado detalhado da frente da Fase 2 e o que já foi concluído. |
| [docs/api-v1.md](docs/api-v1.md) | Contratos REST, endpoints, DTOs, filtros, paginação, ordenação e dashboard. |
| [docs/api-errors.md](docs/api-errors.md) | Contrato de erros e códigos estáveis. |
| [docs/permissoes.md](docs/permissoes.md) | Matriz de permissões por papel. |
| [docs/staging.md](docs/staging.md) | Configuração, variáveis, health, logs e preparação de staging. |
| [docs/arquitetura.md](docs/arquitetura.md) | Decisões arquiteturais. |
| [docs/fase1-finalizacao.md](docs/fase1-finalizacao.md) | Evidências e validações da Fase 1. |
| [docs/aceite-fase-1.md](docs/aceite-fase-1.md) | Critérios de aceite da Fase 1. |

Com a aplicação no ar:

```text
http://127.0.0.1:8080/swagger-ui.html
```

OpenAPI:

```text
http://127.0.0.1:8080/v3/api-docs
```

---

# Executar localmente

## 1. Atualize a main

Antes de começar qualquer trabalho:

```bash
git switch main
git pull origin main
git status
```

O esperado é:

```text
nothing to commit, working tree clean
```

Nunca desenvolver diretamente na `main`.

Depois crie uma nova branch:

```bash
git switch -c feature/nome-da-tarefa
```

---

## 2. Variáveis de ambiente

Copie:

```text
.env.example
```

para:

```text
.env
```

Linux/macOS:

```bash
cp .env.example .env
```

PowerShell:

```powershell
Copy-Item .env.example .env
```

Preencha os valores marcados com:

```text
CHANGE_ME
```

Nunca versionar `.env`.

Para staging existe:

```text
.env.staging.example
```

---

## 3. Subir backend e infraestrutura

Na raiz:

```bash
docker compose --env-file .env up -d --build
```

Verifique:

```bash
docker compose --env-file .env ps -a
```

Health:

```text
http://127.0.0.1:8080/actuator/health
```

Resposta esperada:

```json
{
  "status": "UP"
}
```

---

## 4. Bootstrap da primeira oficina

Para criar a primeira oficina:

```text
APP_BOOTSTRAP_ENABLED=true
```

Defina também os dados da oficina e do OWNER.

Depois da primeira criação, volte para:

```text
APP_BOOTSTRAP_ENABLED=false
```

Não deixar bootstrap permanentemente ativo em staging ou produção.

---

# Frontend

Dentro de:

```text
frontend/
```

execute:

```bash
npm ci
npm run dev
```

O frontend utiliza o proxy do Vite no desenvolvimento local para acessar a API.

Normalmente:

```text
Frontend:
http://127.0.0.1:5173

API:
http://127.0.0.1:8080
```

---

# Testes

## Backend

Com Docker disponível:

```bash
cd backend
./mvnw verify
```

No Windows também pode ser utilizado o Maven portátil configurado em `.tools`.

A última validação completa da frente da Fase 2 terminou com:

```text
12 testes unitários
39 testes de integração
0 falhas
BUILD SUCCESS
```

A suíte específica:

```text
Fase2IT
```

possui:

```text
27 testes
0 falhas
```

Os testes de integração utilizam PostgreSQL e MinIO via Testcontainers.

---

## Frontend

Dentro de `frontend`:

```bash
npm run lint
npm run typecheck
npm test
npm run build
```

Antes de abrir PR, o código alterado deve passar pelas verificações aplicáveis.

---

# Docker e infraestrutura

Foram validados:

```text
PostgreSQL healthy
MinIO ativo
API healthy
/actuator/health HTTP 200
Swagger HTTP 200
CORS permitido somente para origem configurada
origens não permitidas retornando 403
```

A validação da Fase 2 foi feita em ambiente Docker isolado para não destruir volumes locais existentes.

Não remover volumes locais de outro desenvolvedor apenas para executar testes.

---

# Staging

A configuração para staging está preparada e documentada.

Arquivo:

```text
docs/staging.md
```

Template:

```text
.env.staging.example
```

O deploy efetivo de staging **ainda não foi realizado**.

Isso depende de:

```text
servidor
domínio
HTTPS
credenciais
storage
banco
autorização para publicação
```

Não interpretar "staging preparado" como "staging publicado".

---

# CORS

CORS é configurável por ambiente.

Produção e staging devem utilizar apenas origens autorizadas explicitamente.

Não utilizar:

```text
*
```

indiscriminadamente em ambiente autenticado.

---

# Secrets

Nunca adicionar ao Git:

```text
.env
senhas
JWT secrets
access tokens
refresh tokens
tokens públicos
S3 secret keys
credenciais do PostgreSQL
credenciais reais de staging
dados privados de clientes
```

Secrets devem entrar exclusivamente por configuração de ambiente.

---

# Logs

A aplicação possui identificação de requests e contexto operacional.

Os logs devem ajudar no diagnóstico sem expor dados sensíveis.

Não registrar:

```text
senha
access token
refresh token
JWT secret
credenciais S3
conteúdo privado desnecessário
```

---

# Health checks

Endpoint principal:

```text
GET /actuator/health
```

Existe também verificação relacionada ao storage.

Não expor endpoints administrativos adicionais do Actuator publicamente sem necessidade.

---

# Git e colaboração

A `main` é a base estável do projeto.

Fluxo obrigatório:

```text
main atualizada
    ↓
branch própria
    ↓
desenvolvimento
    ↓
testes
    ↓
commit
    ↓
push
    ↓
Pull Request
    ↓
revisão pelo outro desenvolvedor
    ↓
merge
    ↓
main
```

Não trabalhar diretamente na `main`.

Padrões de branch:

```text
feature/...
fix/...
test/...
docs/...
chore/...
```

Antes de iniciar uma nova tarefa:

```bash
git switch main
git pull origin main
git status
git switch -c feature/nome-da-tarefa
```

---

# Para quem continuar a Fase 2

A frente de API, contratos, segurança e infraestrutura já está concluída.

**Não refazer essa implementação sem primeiro verificar o que já existe.**

Antes de desenvolver a próxima parte:

```text
1. Atualizar a main.
2. Ler docs/fase2-caua.md.
3. Ler docs/api-v1.md.
4. Conferir docs/permissoes.md.
5. Conferir o Swagger da API.
6. Identificar exatamente qual frente ainda está pendente.
7. Criar uma nova branch.
8. Trabalhar somente nessa branch.
9. Abrir PR para revisão.
```

O arquivo mais importante para entender o que foi entregue e o que ainda falta é:

```text
docs/fase2-caua.md
```

---

# O que não faz parte desta entrega

A conclusão da frente de Cauã **não significa que toda a Fase 2 acabou**.

Ainda não considerar automaticamente implementados:

```text
agenda avançada
gestão de capacidade
pátio
estoque completo
fornecedores avançados
Dinheiro Esquecido
IA
voz
WhatsApp API
financeiro
fiscal
pagamentos
marketplace
app mobile nativo
```

Esses módulos devem ser tratados em etapas futuras ou conforme planejamento conjunto.

---

# Escopo comercial

O Garagem SaaS terá inicialmente **um único plano pago**.

Não existe freemium definido para esta etapa.

Ainda precisam ser fechados entre os responsáveis pelo produto:

```text
preço
pacote comercial
oficinas piloto
responsáveis pelo piloto
datas
prazo do piloto
critérios comerciais de sucesso
```

---

# Estado final desta entrega

```text
Fase 1
✅ concluída

Frontend + backend da Fase 1
✅ integrados

PostgreSQL
✅ funcionando

MinIO privado
✅ funcionando

API REST
✅ funcionando

Multi-tenancy
✅ validado

Permissões
✅ validadas

Paginação
✅ implementada

Filtros
✅ implementados

Ordenação
✅ implementada

Dashboard backend
✅ implementado

Contrato de erros
✅ padronizado

Swagger / OpenAPI
✅ atualizado

Testes Fase 2
✅ 27/27

Maven verify
✅ BUILD SUCCESS

Docker
✅ validado

CORS
✅ validado

Health
✅ validado

Documentação Fase 2
✅ concluída

PR da frente do Cauã
✅ mergeado na main

Staging configurado/documentado
✅

Deploy efetivo de staging
⏳ pendente

Fase 2 geral
🚧 em andamento
```

---

# Resumo para o próximo desenvolvedor

A `main` atual já contém a Fase 1 e a frente de API/segurança/infraestrutura da Fase 2.

Não parta de branches antigas.

Comece sempre por:

```bash
git switch main
git pull origin main
```

Depois leia:

```text
docs/fase2-caua.md
docs/api-v1.md
docs/permissoes.md
```

e crie uma branch nova para a próxima tarefa.

A `main` deve permanecer estável.
