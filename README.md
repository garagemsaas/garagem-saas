<div align="center">

# Automotive Operations SaaS

### Plataforma SaaS para gestão de oficinas, serviços automotivos e operações comerciais

Sistema web desenvolvido para centralizar operações automotivas, conectar equipes, organizar processos e transformar dados operacionais em oportunidades de negócio.

![Java](https://img.shields.io/badge/Java-25-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?style=for-the-badge&logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-3178C6?style=for-the-badge&logo=typescript&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)

</div>

---

## Sobre o projeto

Este projeto é uma plataforma SaaS multi-tenant desenvolvida para digitalizar e organizar operações do setor automotivo.

A aplicação centraliza o ciclo completo de atendimento, desde o cadastro do cliente e do veículo até diagnóstico, orçamento, aprovação, execução do serviço, acompanhamento e conclusão.

Além da operação tradicional, a plataforma possui recursos voltados para recuperação de oportunidades comerciais, gestão de assinaturas, controle de planos e acompanhamento operacional.

O sistema foi desenvolvido utilizando uma arquitetura de **monólito modular**, priorizando segurança, separação de responsabilidades, escalabilidade gradual e facilidade de manutenção.

---

## Principais funcionalidades

### Gestão operacional

- Cadastro de clientes
- Cadastro e histórico de veículos
- Ordens de serviço
- Responsáveis e mecânicos
- Checklist técnico
- Diagnósticos
- Registro fotográfico
- Timeline completa da OS
- Controle de status
- Histórico operacional

### Orçamentos

- Criação de orçamentos
- Versionamento imutável
- Histórico de alterações
- Aprovação pública por link
- Rejeição de orçamento
- Controle de validade
- Auditoria das decisões

### Oportunidades comerciais

Módulo destinado à identificação automática de oportunidades que podem representar faturamento perdido.

Entre os cenários monitorados:

- orçamentos sem resposta;
- revisões atrasadas;
- clientes que precisam de nova abordagem;
- serviços pendentes de reavaliação;
- oportunidades recuperadas;
- valores potenciais e efetivamente recuperados.

O objetivo é transformar dados operacionais em ações comerciais.

---

## Dashboard

O dashboard consolida indicadores importantes para a operação:

- ordens de serviço;
- clientes;
- veículos;
- serviços em andamento;
- oportunidades abertas;
- valores potenciais;
- valores recuperados;
- indicadores comerciais.

---

## Multi-tenancy

A aplicação foi projetada para atender múltiplas empresas utilizando a mesma infraestrutura.

Cada empresa possui seu próprio contexto isolado.

```text
Empresa A
├── usuários
├── clientes
├── veículos
├── ordens
├── fotos
├── orçamentos
└── oportunidades

Empresa B
├── usuários
├── clientes
├── veículos
├── ordens
├── fotos
├── orçamentos
└── oportunidades
```

O backend é responsável pela determinação do tenant.

O frontend nunca é considerado autoridade para definir a empresa proprietária de um recurso.

---

## Controle de acesso

Atualmente existem três níveis principais de acesso:

| Papel | Responsabilidade |
|---|---|
| `OWNER` | Administração da empresa e acesso completo |
| `ATENDENTE` | Atendimento e operação administrativa |
| `MECANICO` | Operação técnica relacionada aos serviços |

As permissões são validadas no backend através do Spring Security.

---

## Planos e assinaturas

A plataforma possui infraestrutura própria para comercialização como SaaS.

Recursos implementados:

- planos;
- assinaturas;
- limites por plano;
- quantidade de usuários;
- quantidade de veículos;
- ordens de serviço;
- armazenamento;
- cobrança;
- webhooks;
- inadimplência;
- suspensão;
- reativação;
- métricas de utilização;
- histórico de cobrança.

---

## Segurança

A aplicação possui mecanismos de segurança implementados em diferentes camadas.

Entre eles:

- autenticação baseada em tokens;
- access token e refresh token;
- rotação e revogação de refresh tokens;
- isolamento multi-tenant;
- autorização baseada em papéis;
- rate limiting;
- proteção contra brute force;
- validação backend;
- queries parametrizadas por JPA/Hibernate;
- controle de uploads;
- armazenamento privado de imagens;
- headers de segurança;
- Content Security Policy;
- CORS configurável;
- logs correlacionados por request ID;
- proteção de endpoints administrativos;
- tratamento padronizado de erros.

A aplicação também possui testes específicos para:

- IDOR/BOLA;
- isolamento entre tenants;
- autorização;
- SQL Injection;
- uploads;
- JWT;
- webhooks;
- concorrência;
- duplicidade de operações.

---

## Arquitetura

A aplicação utiliza **monólito modular**.

```text
Frontend
   │
   │ REST / JSON
   ▼
Controllers
   │
   ▼
Application / Services
   │
   ▼
Domain
   │
   ▼
Repositories
   │
   ▼
PostgreSQL
```

Integrações de armazenamento utilizam:

```text
API
 │
 └── MinIO / S3
```

Essa abordagem mantém o deploy simples sem abrir mão da separação entre os módulos da aplicação.

---

## Tecnologias

### Backend

- Java 25
- Spring Boot 3.5
- Spring Web
- Spring Security
- Spring Data JPA
- Hibernate
- Bean Validation
- Flyway
- Spring Actuator
- OpenAPI / Swagger
- Maven

### Frontend

- React 19
- TypeScript
- Vite
- CSS
- Playwright

### Banco de dados

- PostgreSQL 17

### Storage

- MinIO
- API compatível com S3

### Infraestrutura

- Docker
- Docker Compose
- Git
- GitHub
- GitHub Actions

### Testes

- JUnit
- Mockito
- Testcontainers
- Playwright
- axe-core

---

## Estrutura do projeto

```text
.
├── backend/
│   ├── src/main/java/
│   ├── src/main/resources/
│   │   └── db/migration/
│   └── src/test/
│
├── frontend/
│   ├── src/
│   ├── public/
│   ├── scripts/
│   └── tests/
│
├── docs/
│
├── scripts/
│
├── compose.yml
│
└── README.md
```

---

## Banco de dados

O PostgreSQL é utilizado como banco principal.

Alterações de schema são controladas exclusivamente através do Flyway.

```text
V1 → núcleo operacional
V2 → integridade de orçamento
V3 → oportunidades comerciais
V4 → planos e assinaturas
V5 → segurança e ciclo de tokens
...
```

Isso permite que a evolução do banco seja reproduzível e versionada junto ao código.

---

## Armazenamento de arquivos

Fotos relacionadas às operações são armazenadas fora do banco.

A aplicação utiliza MinIO/S3 para armazenamento privado.

O banco mantém apenas os metadados necessários para relacionar o objeto ao:

- tenant;
- veículo;
- ordem de serviço;
- usuário responsável.

---

## Observabilidade

A aplicação registra informações importantes para diagnóstico:

- request ID;
- endpoint;
- método HTTP;
- status;
- duração;
- contexto autenticado;
- falhas de autenticação;
- erros inesperados.

Também possui:

```text
/actuator/health
```

para monitoramento de disponibilidade.

---

## API

A API segue o prefixo:

```text
/api/v1
```

Durante o desenvolvimento, a documentação OpenAPI pode ser acessada através do Swagger.

```text
/swagger-ui.html
```

Em ambiente de produção, sua exposição pode ser desabilitada por configuração.

---

## Executando localmente

### Requisitos

- Java 25
- Docker
- Docker Compose
- Node.js
- npm

Clone o projeto:

```bash
git clone <repository-url>
cd <repository>
```

Crie o arquivo de configuração:

```bash
cp .env.example .env
```

Configure as variáveis necessárias e execute:

```bash
docker compose --env-file .env up -d --build
```

---

## Frontend

```bash
cd frontend
npm install
npm run dev
```

Aplicação local:

```text
http://localhost:5173
```

API:

```text
http://localhost:8080
```

---

## Testes

### Backend

```bash
cd backend
./mvnw verify
```

Os testes de integração utilizam ambientes reais através do Testcontainers.

### Frontend

```bash
cd frontend

npm run typecheck
npm run lint
npm test
npm run build
```

---

## Qualidade e engenharia

O projeto segue princípios como:

- separação de responsabilidades;
- arquitetura modular;
- APIs versionadas;
- DTOs;
- migrations versionadas;
- validação no backend;
- testes automatizados;
- isolamento multi-tenant;
- segurança por padrão;
- revisão de código;
- Conventional Commits;
- desenvolvimento orientado por branches;
- documentação técnica.

---

## Evolução do produto

A arquitetura foi construída para permitir expansão progressiva para outras operações do setor automotivo.

Entre as possibilidades futuras:

- gestão de estoque de veículos;
- leads comerciais;
- propostas;
- reservas;
- vendas;
- veículos recebidos em troca;
- preparação de veículos;
- pós-venda;
- integrações com marketplaces;
- automações comerciais;
- inteligência aplicada aos dados operacionais.

Sem necessidade de migrar prematuramente para microserviços.

---

## Status

```text
Core operacional                 ✅
Frontend integrado               ✅
Multi-tenancy                    ✅
Autenticação e autorização       ✅
Orçamento e aprovação pública    ✅
Oportunidades comerciais         ✅
Planos e assinaturas             ✅
Cobrança e webhooks              ✅
Observabilidade                  ✅
Auditoria de segurança           ✅
Testes automatizados             ✅

Piloto real                      🚧
Infraestrutura de produção       🚧
```

---

## Objetivo técnico

Mais do que implementar funcionalidades, este projeto também representa a construção de uma aplicação SaaS completa utilizando conceitos encontrados em sistemas reais:

- arquitetura;
- backend;
- frontend;
- segurança;
- banco de dados;
- autenticação;
- multi-tenancy;
- armazenamento de arquivos;
- billing;
- observabilidade;
- testes;
- infraestrutura.

---

## Desenvolvedores

<table>
  <tr>
    <td align="center">
      <strong>Cauã Souza</strong><br/>
      Backend · Software Development · Databases
    </td>
    <td align="center">
      <strong>Kauã Orcia</strong><br/>
      Frontend · Software Development · Product
    </td>
  </tr>
</table>

---

<div align="center">

Desenvolvido por **Cauã Souza** & **Kauã Orcia**

</div>
