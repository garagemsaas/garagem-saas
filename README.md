# Garagem SaaS

SaaS para oficinas mecânicas.

O Garagem SaaS organiza a operação da oficina desde o cadastro do cliente e do veículo até o diagnóstico, orçamento, aprovação, execução e conclusão da ordem de serviço.

O projeto utiliza arquitetura de **monólito modular**, API REST, frontend React e isolamento **multi-tenant por oficina**.

---

# Estado atual

## ✅ Fase 1 — Concluída

A Fase 1 foi concluída e integrada.

O núcleo operacional está funcional com:

- autenticação;
- clientes;
- veículos;
- ordens de serviço;
- responsáveis;
- checklist;
- diagnóstico;
- fotos privadas;
- orçamento;
- aprovação pública;
- timeline;
- PostgreSQL;
- MinIO;
- frontend integrado à API real;
- isolamento entre oficinas.

Fluxo principal:

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

## ✅ Fase 2 — Concluída

As duas frentes da Fase 2 foram concluídas.

API, contratos, segurança e infraestrutura

Concluído:

- documentação dos endpoints;
- estabilização dos DTOs;
- padronização das respostas de validação;
- paginação;
- filtros;
- ordenação;
- respostas para dashboard;
- Swagger/OpenAPI;
- validação de login;
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
- isolamento entre oficinas;
- permissões por papel;
- expiração de tokens;
- upload de arquivos;
- CORS;
- secrets;
- logs;
- health checks;
- preparação de staging.

> O ambiente de staging está preparado e documentado. O deploy em servidor externo é uma etapa separada.

Documentação principal da Fase 2:

```text
docs/fase2-caua.md
docs/api-v1.md
docs/api-errors.md
docs/permissoes.md
docs/staging.md
```

---

# ✅ Fase 3 — Primeira integração

A Fase 3 está concluída. A branch da Fase 4 contém as entregas de Cauã e Kauã.

### Kauã — frontend

Implementação entregue nesta branch: login e sessão reais, clientes, veículos,
ordens de serviço e alterações de status; paginação e busca no servidor,
indicadores via `/dashboard`, erros e carregamentos. Não há dados fictícios nas
telas de produção. A validação histórica desta entrega está documentada abaixo;
a auditoria da Fase 4 acrescentou testes HTTP com PostgreSQL/MinIO e smoke de navegador
com API real, registrados em [docs/fase4-caua.md](docs/fase4-caua.md).
Checklist e validações da entrega original em
[docs/fase3-kaua-frontend.md](docs/fase3-kaua-frontend.md).

## API e integração

A Fase 3 foi concluída na branch:

```text
feature/fase3-api-integration
```

Esta etapa teve como objetivo consolidar os contratos necessários para a primeira integração entre frontend e backend.

### Contratos da primeira integração

Foram auditados e preparados os contratos de:

- login;
- clientes;
- veículos;
- listagem de ordens de serviço;
- detalhe de ordem de serviço;
- alteração de status;
- exemplos de requisição e resposta.

A documentação de referência continua sendo:

```text
docs/api-v1.md
```

O Swagger/OpenAPI deve permanecer coerente com a implementação e com essa documentação.

---

## Validações da integração

Durante esta frente foram considerados:

- compatibilidade entre frontend e backend;
- contratos REST;
- autorização por papel;
- isolamento por oficina;
- conflitos de revisão;
- erros de integração;
- testes de regressão para problemas encontrados.

Papéis atuais:

```text
OWNER
ATENDENTE
MECANICO
```

As regras detalhadas estão em:

```text
docs/permissoes.md
```

---

# 🚧 Fase 4 — OS completa e acompanhamento público

A Fase 4 está em andamento na branch compartilhada:

```text
feature/fase4-os-public-tracking
```

A regra desta etapa é **uma branch por fase**: Cauã e Kauã trabalham na mesma branch da Fase 4.  
Não utilizar `main` para o desenvolvimento desta fase e não criar uma branch separada por desenvolvedor.

## ✅ Cauã — API, domínio e acompanhamento público

A parte do Cauã na Fase 4 está concluída.

Checklist entregue:

- [x] Endpoint de checklist
- [x] Endpoint de diagnóstico
- [x] Endpoint de orçamento
- [x] Versionamento imutável
- [x] Aprovação e recusa
- [x] Criação de link público
- [x] Expiração de link
- [x] Revogação de link
- [x] Fotos
- [x] Timeline

A entrega consolidou e validou o fluxo completo da ordem de serviço:

```text
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
Versionamento
  ↓
Link público
  ↓
Aprovação / Recusa
  ↓
Timeline
```

Também foram revisados e validados:

- autorização por papel;
- isolamento multi-tenant por oficina;
- conflitos de revisão;
- imutabilidade das versões de orçamento;
- proteção contra decisões públicas conflitantes;
- expiração e revogação de links;
- segurança de fotos privadas;
- contratos REST;
- Swagger/OpenAPI;
- documentação da API;
- fluxo público no frontend;
- testes de integração e navegador.

Evidência da validação final:

```text
12 testes unitários
64 testes de integração
Maven verify: BUILD SUCCESS
```

A documentação técnica da entrega está em:

```text
docs/fase4-caua.md
```

Os contratos atualizados permanecem em:

```text
docs/api-v1.md
docs/api-errors.md
docs/permissoes.md
```

## Próximo passo — Kauã

A Fase 4 **ainda não deve ser marcada como concluída no geral**.

O Kauã deve continuar a parte dele utilizando **a mesma branch**:

```text
feature/fase4-os-public-tracking
```

Antes de iniciar, deve sincronizar a branch:

```powershell
git switch feature/fase4-os-public-tracking
git pull
git status
```

Ele deve partir do código já entregue pelo Cauã, sem recriar endpoints, reintroduzir dados fictícios ou substituir integrações existentes sem necessidade técnica.

Depois que o Kauã concluir a frente dele:

```text
commit
  ↓
push
  ↓
Fase 4 completa
  ↓
somente então criar a branch da Fase 5 a partir da Fase 4 atualizada
```

Não executar `git init`: o repositório já está inicializado.

---

# Situação das fases

```text
Fase 1
✅ Concluída

Fase 2
✅ Concluída

Fase 3 — Cauã / API e primeira integração
✅ Concluída

Fase 3 geral
✅ Concluída

Fase 4 — OS completa e acompanhamento público
🚧 Em andamento

Fase 4 / parte do Cauã
✅ Concluída — 12 testes unitários + 64 de integração, Maven verify BUILD SUCCESS
```

A auditoria, correções, evidências e pendências da Fase 4 estão em
[docs/fase4-caua.md](docs/fase4-caua.md). O encerramento geral depende também da frente do Kauã.

---

# Contratos da API

A API utiliza:

```text
/api/v1
```

A documentação principal está em:

```text
docs/api-v1.md
```

Com a aplicação em execução:

```text
Swagger:
http://127.0.0.1:8080/swagger-ui.html

OpenAPI:
http://127.0.0.1:8080/v3/api-docs
```

Antes de criar ou alterar chamadas no frontend:

1. verificar `docs/api-v1.md`;
2. conferir o Swagger/OpenAPI;
3. conferir os tipos em `frontend/src/api`;
4. não inventar contratos no frontend.

---

# Regra importante de integração

O backend é a fonte de verdade para:

- autenticação;
- autorização;
- multi-tenancy;
- validações;
- transições de status;
- conflitos de revisão;
- persistência;
- regras de negócio.

O frontend não deve duplicar regras de segurança nem decidir o `oficinaId`.

A oficina é determinada pelo contexto autenticado.

---

# Paginação, filtros e ordenação

Listagens que suportam paginação devem utilizar o backend.

Formato conceitual:

```text
?page=0&size=20
```

Filtros e ordenação devem utilizar exclusivamente os parâmetros suportados pela API.

Não carregar toda a base no frontend para depois filtrar ou paginar localmente quando a API já suporta essas operações.

---

# Ordens de serviço

A listagem de ordens de serviço possui filtros implementados no backend.

Na Fase 2 foi corrigido um problema específico do PostgreSQL envolvendo filtros temporais opcionais.

Não reintroduzir consultas no formato:

```text
(:de IS NULL OR ...)
```

para parâmetros temporais opcionais sem validar o comportamento no PostgreSQL.

A implementação atual utiliza construção dinâmica dos filtros.

Arquivos principais:

```text
backend/src/main/java/br/com/garagem/ordemservico/repository/OrdemServicoRepository.java
backend/src/main/java/br/com/garagem/ordemservico/repository/OrdemServicoRepositoryCustom.java
backend/src/main/java/br/com/garagem/ordemservico/repository/OrdemServicoRepositoryImpl.java
backend/src/main/java/br/com/garagem/ordemservico/application/OsService.java
```

---

# Multi-tenancy

Todo dado operacional pertence a uma oficina.

Uma oficina não pode:

- listar dados de outra;
- consultar recursos de outra;
- alterar recursos de outra;
- vincular recursos pertencentes a outra;
- acessar fotos privadas de outra;
- atribuir usuários de outra oficina.

Nunca utilizar um `oficinaId` enviado pelo frontend como autoridade de segurança.

---

# Erros da API

O padrão de erros está documentado em:

```text
docs/api-errors.md
```

Principais respostas:

```text
400 — dados inválidos
401 — não autenticado
403 — sem permissão
404 — recurso inexistente ou inacessível
409 — conflito
413 — arquivo maior que o permitido
415 — tipo de arquivo não suportado
500 — erro interno inesperado
```

Não expor:

- stack trace;
- SQL;
- senha;
- tokens;
- secrets;
- credenciais;
- detalhes internos sensíveis.

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
Flyway
Actuator
OpenAPI / Swagger
JUnit
Testcontainers
```

## Frontend

```text
React
TypeScript
Vite
```

## Infraestrutura

```text
PostgreSQL 17
MinIO / S3
Docker
Docker Compose
Git
GitHub
GitHub Actions
```

---

# Arquitetura

O projeto utiliza **monólito modular**.

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

Não transformar o projeto em microserviços sem necessidade concreta.

Mais detalhes:

```text
docs/arquitetura.md
```

---

# Executar localmente

Crie o `.env` utilizando:

```text
.env.example
```

No PowerShell:

```powershell
Copy-Item .env.example .env
```

Preencha os valores:

```text
CHANGE_ME
```

Depois:

```powershell
docker compose --env-file .env up -d --build
```

Verifique:

```powershell
docker compose --env-file .env ps -a
```

Health:

```text
http://127.0.0.1:8080/actuator/health
```

---

# Frontend

Dentro de:

```text
frontend/
```

execute:

```powershell
npm ci
npm run dev
```

Desenvolvimento local normalmente utiliza:

```text
Frontend:
http://127.0.0.1:5173

Backend:
http://127.0.0.1:8080
```

---

# Testes

Backend:

```powershell
cd backend
.\mvnw.cmd verify
```

Também existe ambiente portátil em:

```text
.tools/
```

Os testes de integração utilizam PostgreSQL e MinIO por Testcontainers.

Frontend:

```powershell
cd frontend
npm run lint
npm run typecheck
npm test
npm run build
```

Execute apenas scripts realmente existentes no `package.json`.

---

# Staging

A preparação de staging está documentada em:

```text
docs/staging.md
```

Template:

```text
.env.staging.example
```

O deploy externo de staging deve ser tratado separadamente.

Não considerar:

```text
staging preparado
```

como:

```text
staging publicado em produção/servidor externo
```

---

# Documentação

Antes de continuar o desenvolvimento, leia:

| Documento | Conteúdo |
|---|---|
| [docs/api-v1.md](docs/api-v1.md) | Contratos REST utilizados pelo frontend e demais consumidores. |
| [docs/api-errors.md](docs/api-errors.md) | Padrão e códigos de erro da API. |
| [docs/permissoes.md](docs/permissoes.md) | Regras e permissões por papel. |
| [docs/fase2-caua.md](docs/fase2-caua.md) | Entregas da frente de API, segurança e infraestrutura da Fase 2. |
| [docs/fase3-kaua-frontend.md](docs/fase3-kaua-frontend.md) | Entrega e validações do frontend conectado à API na Fase 3. |
| [docs/fase4-caua.md](docs/fase4-caua.md) | Auditoria, correções, testes e evidências da parte do Cauã na Fase 4. |
| [docs/staging.md](docs/staging.md) | Preparação e configuração de staging. |
| [docs/arquitetura.md](docs/arquitetura.md) | Decisões arquiteturais. |
| [docs/fase1-finalizacao.md](docs/fase1-finalizacao.md) | Finalização técnica da Fase 1. |
| [docs/aceite-fase-1.md](docs/aceite-fase-1.md) | Critérios de aceite da Fase 1. |

---

# Para continuar a Fase 4

A parte do Cauã está concluída. O próximo desenvolvimento da Fase 4 deve continuar na mesma branch:

```text
feature/fase4-os-public-tracking
```

Fluxo obrigatório para o próximo desenvolvedor:

```text
1. Entrar na branch feature/fase4-os-public-tracking.
2. Executar git pull antes de alterar qualquer arquivo.
3. Ler este README.
4. Ler docs/fase4-caua.md.
5. Ler docs/api-v1.md.
6. Conferir docs/permissoes.md.
7. Conferir Swagger/OpenAPI.
8. Preservar as entregas já implementadas.
9. Não usar a main durante o desenvolvimento da fase.
10. Não criar uma branch separada por desenvolvedor dentro da mesma fase.
```

Comandos de sincronização:

```powershell
git switch feature/fase4-os-public-tracking
git pull
git status
```

Especialmente na integração frontend/backend:

```text
não inventar campos
não inventar endpoints
não inventar status
não inventar permissões
não reintroduzir mocks ou dados fictícios
não contornar regras do backend
não apagar ou refazer trabalho concluído sem necessidade técnica
```

Quando houver divergência entre frontend e API, investigar o contrato antes de alterar qualquer lado.

Ao terminar a parte do Kauã, fazer `commit` e `push` nessa mesma branch.  
A branch da próxima fase só deve ser criada depois que a Fase 4 estiver completa e sincronizada.

---

# Atenção ao OrderDetail.tsx

O arquivo:

```text
frontend/src/OrderDetail.tsx
```

teve histórico de problema de encoding/mojibake durante fases anteriores.

Sempre utilizar como referência a versão atual versionada no projeto.

Não restaurar automaticamente versões antigas de stash ou branches antigas sobre esse arquivo.

---

# Escopo futuro

Não implementar dentro da Fase 4 sem planejamento específico:

```text
agenda avançada
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
microserviços
Kafka
```

---

# Resumo para o próximo desenvolvedor

O projeto já possui:

```text
Fase 1 concluída
Fase 2 concluída
Fase 3 concluída
API REST estabilizada
autenticação
multi-tenancy
permissões
PostgreSQL
MinIO
paginação
filtros
ordenação
dashboard backend
Swagger/OpenAPI
contrato de erros
contratos da primeira integração
Fase 4 / parte do Cauã concluída
OS completa auditada
versionamento imutável validado
acompanhamento público validado
aprovação e recusa validadas
expiração e revogação de link validadas
fotos privadas validadas
timeline validada
```

A Fase 3 foi concluída na branch:

```text
feature/fase3-api-integration
```

A Fase 4 está sendo desenvolvida por Cauã e Kauã na branch compartilhada:

```text
feature/fase4-os-public-tracking
```

Antes de continuar a Fase 4, leia principalmente:

```text
README.md
docs/fase4-caua.md
docs/api-v1.md
docs/api-errors.md
docs/permissoes.md
docs/fase3-kaua-frontend.md
```

A Fase 4 geral permanece em andamento até a conclusão da frente do Kauã.  
Não utilizar `main` para o desenvolvimento desta fase e não criar uma branch separada por desenvolvedor.
