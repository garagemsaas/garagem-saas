<div align="center">

# Plataforma SaaS de Gestão Automotiva

### Gestão operacional, relacionamento com clientes e recuperação de oportunidades para empresas do setor automotivo.

Sistema web multi-tenant desenvolvido para centralizar operações, organizar atendimentos, acompanhar serviços e transformar dados operacionais em oportunidades comerciais.

![Java](https://img.shields.io/badge/Java-25-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?style=flat-square&logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-3178C6?style=flat-square&logo=typescript&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-4169E1?style=flat-square&logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white)

</div>

---

## Sobre o projeto

A plataforma foi criada para empresas do setor automotivo que precisam concentrar em um único ambiente informações que normalmente ficam espalhadas entre sistemas, planilhas, mensagens e controles manuais.

O fluxo acompanha toda a jornada operacional, desde o cadastro do cliente e do veículo até diagnóstico, orçamento, aprovação, execução, acompanhamento e conclusão do serviço.

Além da gestão operacional, o sistema identifica oportunidades comerciais a partir dos próprios dados da empresa, permitindo acompanhar contatos, retornos e valores recuperados.

---

## Principais recursos

### Operação

- clientes e veículos;
- ordens de serviço;
- responsáveis e equipe técnica;
- checklist;
- diagnóstico;
- registro fotográfico;
- controle de status;
- linha do tempo do atendimento.

### Orçamentos e acompanhamento

- criação e versionamento de orçamentos;
- preservação do histórico;
- aprovação ou recusa por acompanhamento digital;
- registro de decisões;
- rastreabilidade do atendimento.

### Oportunidades comerciais

O módulo de oportunidades identifica situações que podem representar receita não recuperada, como:

- orçamentos sem resposta;
- revisões atrasadas;
- reavaliações pendentes;
- clientes que precisam de novo contato;
- oportunidades em acompanhamento;
- valores potenciais e recuperados.

### Planos e assinaturas

A aplicação possui infraestrutura para operação como SaaS, incluindo:

- planos;
- assinaturas;
- limites de utilização;
- controle de usuários;
- controle de veículos e ordens;
- armazenamento;
- cobrança;
- webhooks;
- inadimplência;
- suspensão e reativação;
- métricas de utilização.

---

## Multi-tenancy

Cada empresa utiliza um contexto isolado.

O isolamento é aplicado no backend e no banco de dados, impedindo que uma empresa consulte ou relacione recursos pertencentes a outra.

O tenant é determinado pelo contexto autenticado; o frontend não é tratado como autoridade para definir a empresa proprietária de um recurso.

---

## Segurança

A plataforma possui mecanismos de segurança e proteção operacional em diferentes camadas, incluindo:

- autenticação e autorização;
- controle de acesso por papel;
- isolamento multi-tenant;
- rotação e revogação de sessão;
- rate limiting;
- proteção contra brute force;
- validação de dados;
- uploads controlados;
- armazenamento privado de arquivos;
- headers de segurança;
- CORS configurável;
- tratamento padronizado de erros;
- logs correlacionados por request ID;
- proteção contra duplicidade e concorrência em operações críticas.

---

## Arquitetura

A aplicação utiliza **monólito modular**.

Os módulos possuem fronteiras explícitas e se comunicam por contratos definidos, mantendo o deploy simples sem abrir mão da separação entre responsabilidades.

```text
Frontend
   │
   ▼
REST API
   │
   ▼
Application / Domain
   │
   ▼
Persistence
   │
   ├── PostgreSQL
   └── MinIO / S3
```

---

## Tecnologia

| Área | Tecnologias |
|---|---|
| Backend | Java 25 · Spring Boot · Spring Security · JPA/Hibernate |
| Frontend | React 19 · TypeScript · Vite |
| Banco | PostgreSQL 17 · Flyway |
| Arquivos | MinIO / S3 |
| API | REST · OpenAPI |
| Infraestrutura | Docker · Docker Compose · GitHub Actions |
| Testes | JUnit · Testcontainers · Playwright · axe-core |

---

## Qualidade

A base atual possui:

```text
23 testes unitários
154 testes de integração
33 testes de navegador
PostgreSQL e MinIO reais nos testes de integração
Cenários de concorrência, billing e isolamento multi-tenant
Testes de interface em desktop, tablet e mobile
Verificações de acessibilidade WCAG 2.1 AA
```

A arquitetura também possui testes que impedem regressões nas fronteiras entre módulos.

---

## Documentação

| Área | Local |
|---|---|
| API | `docs/api/` |
| Arquitetura | `docs/architecture/` |
| Operação | `docs/operations/` |
| Segurança | `docs/security/` |
| Produto | `docs/product/` |
| Qualidade | `docs/quality/` |
| Histórico | `docs/archive/` |

---

## Evolução

A arquitetura foi preparada para permitir expansão gradual para outras operações do setor automotivo.

Entre as possibilidades previstas:

- estoque de veículos;
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

---

## Status

| Área | Situação |
|---|---|
| Núcleo operacional | ✅ |
| Frontend integrado | ✅ |
| Multi-tenancy | ✅ |
| Autenticação e autorização | ✅ |
| Orçamentos e acompanhamento | ✅ |
| Oportunidades comerciais | ✅ |
| Planos e assinaturas | ✅ |
| Billing e webhooks | ✅ |
| Segurança e observabilidade | ✅ |
| Testes automatizados | ✅ |
| Piloto real | 🚧 |
| Infraestrutura pública de produção | 🚧 |

---

## Desenvolvedores

<div align="center">

<table>
<tr>
<td align="center" width="320">

### Cauã Souza

Backend · Arquitetura · Banco de Dados  
Infraestrutura · Segurança

[GitHub](https://github.com/cauahpsouza)

</td>
<td align="center" width="320">

### Kauã Orcia

Frontend · Produto · Experiência  
Interface · Desenvolvimento

</td>
</tr>
</table>

</div>

---

<div align="center">

Desenvolvido por **Cauã Souza** & **Kauã Orcia**

**Produto proprietário. Todos os direitos reservados.**

</div>
