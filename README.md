# Garagem

Software de gestão para oficinas mecânicas. Organiza o atendimento do recebimento do veículo à
entrega, e ajuda a oficina a retomar contato com serviços que ficaram pelo caminho.

---

## O que faz

**Operação da oficina**
Clientes e veículos, ordens de serviço com controle de status, checklist de entrada, diagnóstico,
registro fotográfico e linha do tempo completa de cada atendimento.

**Orçamento e aprovação**
Orçamento com versões preservadas — nenhuma versão é reescrita depois de enviada. O cliente recebe
um link de acompanhamento, confere a proposta e aprova ou recusa. A decisão fica registrada com data
e origem.

**Dinheiro Esquecido**
Identifica oportunidades a partir dos próprios registros da oficina: orçamento enviado sem resposta,
revisão vencida, reavaliação pendente. Mostra a origem de cada uma e permite registrar o contato e o
resultado. Valor recuperado só entra quando alguém confirma.

**Planos e assinatura**
Cada oficina tem seu plano, com limites de usuários, armazenamento, veículos e ordens de serviço.
Os limites são aplicados no servidor. A área de assinatura mostra o consumo e permite mudar de
plano, cancelar e reativar.

**Fora do escopo atual:** emissão fiscal, controle de estoque, agenda operacional, integração
bancária e envio automático de mensagens.

---

## Tecnologia

| Camada | Stack |
|---|---|
| Backend | Java 25 · Spring Boot · Spring Security · JPA/Hibernate |
| Banco | PostgreSQL 17 com migrations Flyway |
| Arquivos | Armazenamento S3 privado (MinIO) |
| Frontend | React 19 · TypeScript · Vite |
| API | REST em `/api/v1`, documentada em OpenAPI |
| Infraestrutura | Docker Compose |

Monólito modular: um único serviço, organizado em módulos com fronteiras explícitas. Módulos
conversam por portas declaradas, nunca pelo interior um do outro — e há teste automatizado que
recusa a build se essa regra for violada.

---

## Isolamento entre oficinas

Cada oficina enxerga apenas os próprios dados. A separação é garantida em três camadas
independentes: o identificador da oficina vem sempre do token autenticado e nunca da requisição,
toda consulta é filtrada por oficina, e o banco impede relacionamentos entre oficinas por meio de
chaves estrangeiras compostas. Um recurso de outra oficina responde como inexistente.

---

## Qualidade

```text
23 testes unitários + 154 de integração, com PostgreSQL e MinIO reais
Testes de navegador em desktop, tablet e celular, com verificação de acessibilidade WCAG 2.1 AA
Concorrência, cobrança e isolamento entre oficinas cobertos por teste automatizado
```

---

## Documentação

| Pasta | Conteúdo |
|---|---|
| [docs/api/](docs/api/) | Contratos REST e padrão de erros |
| [docs/architecture/](docs/architecture/) | Arquitetura e matriz de permissões |
| [docs/operations/](docs/operations/) | Preparação de ambiente, backup e restauração |
| [docs/security/](docs/security/) | Auditoria de segurança e revisão de proteção de dados |
| [docs/product/](docs/product/) | Termos, privacidade, contrato, SLA e suporte |
| [docs/quality/](docs/quality/) | Dívida técnica em aberto |
| [docs/archive/](docs/archive/) | Registros históricos por etapa de desenvolvimento |

---

## Executando localmente

Requisitos: Docker e Docker Compose.

```bash
cp .env.example .env     # preencha os valores marcados como CHANGE_ME
docker compose up -d
```

A API sobe em `http://localhost:8080` e responde em `/actuator/health`.

Os valores de exemplo servem apenas para desenvolvimento local. Ambientes reais exigem segredos
próprios, gerados por ambiente e nunca reaproveitados; o procedimento está em
[docs/operations/staging.md](docs/operations/staging.md).

---

## Status

Em desenvolvimento ativo. O núcleo operacional e o módulo de assinatura estão implementados e
cobertos por testes. O que permanece em aberto está registrado em
[docs/quality/technical-debt.md](docs/quality/technical-debt.md).
