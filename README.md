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

# ✅ Fase 4 — OS completa e acompanhamento público

Fase 4 concluída pelas frentes de Cauã e Kauã, conforme encerramento informado pelo time.
A branch atual contém ambas as entregas, até `fabe67f`.
Evidências e limites das validações anteriores permanecem em
[docs/fase4-caua.md](docs/fase4-caua.md) e
[docs/fase4-kaua-frontend.md](docs/fase4-kaua-frontend.md).

# 🚧 Fase 5 — Dinheiro Esquecido

Em andamento na branch compartilhada `feature/fase5-dinheiro-esquecido`.
Uma branch por fase; Cauã e Kauã trabalham nela. Sem usar main durante o desenvolvimento.

A frente de backend acrescenta identificação de oportunidades, contatos manuais,
recuperações, auditoria e relatórios, apoiados no domínio existente.
Estado da validação, decisões e pendências de frontend em
[docs/fase5-caua.md](docs/fase5-caua.md).
A parte do Cauã está concluída e validada: `mvn verify` com BUILD SUCCESS, 12 testes
unitários e 90 de integração (26 novos), stack Docker isolada com PostgreSQL e MinIO,
API UP, Swagger acessível e os 13 endpoints exercitados por HTTP. A Fase 5 geral
continua em andamento, aguardando a frente de frontend do Kauã.

# Situação das fases

| Fase | Estado |
|---|---|
| 1 | Concluída |
| 2 | Concluída |
| 3 | Concluída |
| 4 | Concluída |
| 5 | Em andamento |
| 5 / Cauã | Concluída |

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
| [docs/fase5-caua.md](docs/fase5-caua.md) | Regras, persistência, validação e pendências da parte do Cauã na Fase 5. |
| [docs/staging.md](docs/staging.md) | Preparação e configuração de staging. |
| [docs/arquitetura.md](docs/arquitetura.md) | Decisões arquiteturais. |
| [docs/fase1-finalizacao.md](docs/fase1-finalizacao.md) | Finalização técnica da Fase 1. |
| [docs/aceite-fase-1.md](docs/aceite-fase-1.md) | Critérios de aceite da Fase 1. |

---

# Para continuar a Fase 5

Entrega do frontend nos oito itens solicitados por Kauã:
[carteira, contatos, recuperação e validação](docs/fase5-kaua-frontend.md).
O documento distingue a entrega atual das funções adicionais disponíveis na API
e do aceite integrado antes do piloto.

Leia [docs/fase5-caua.md](docs/fase5-caua.md), os contratos da API e a matriz de
permissões. Confira a branch antes de alterar arquivos: deve ser
`feature/fase5-dinheiro-esquecido`. Não crie nem troque de branch automaticamente.

A interface da Fase 5 é a frente do Kauã. Preserve o frontend integrado, use os
novos endpoints e registre conflitos de contrato antes de inventar campos/status.
A entrega atual deve permanecer no working tree para revisão manual, sem commit,
push, merge ou git init.

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

Fora da Fase 5 V1: WhatsApp API, envio automático de mensagens, IA/scoring, CRM
completo, estoque, financeiro/fiscal/pagamentos, agenda avançada, marketplace,
microserviços e Kafka. Staging externo continua uma etapa separada.
