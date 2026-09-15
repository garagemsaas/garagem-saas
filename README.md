# Garagem SaaS

SaaS para oficinas mecânicas.

O Garagem SaaS organiza a operação da oficina desde o cadastro do cliente e do veículo até o diagnóstico, orçamento, aprovação, execução e conclusão da ordem de serviço.

O projeto utiliza arquitetura de monólito modular, API REST, frontend React e isolamento multi-tenant por oficina.

---

## Estado atual

### ✅ Fase 1 — Concluída

A Fase 1 do Garagem SaaS foi concluída e validada.

O núcleo operacional está funcional com frontend integrado à API real, PostgreSQL e armazenamento privado de fotos no MinIO.

O fluxo principal foi validado com backend, banco de dados e frontend funcionando em conjunto.

Fluxo validado:

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

---

## Documentação

| Documento | Conteúdo |
|---|---|
| [docs/api-v1.md](docs/api-v1.md) | Inventário dos contratos REST: endpoints, DTOs, paginação, filtros, ordenação e dashboard. |
| [docs/api-errors.md](docs/api-errors.md) | Formato único de erro (RFC 7807) e a tabela de códigos estáveis. |
| [docs/permissoes.md](docs/permissoes.md) | Matriz de permissões por papel e o que ela não cobre. |
| [docs/staging.md](docs/staging.md) | Requisitos, variáveis, subida, validação, logs, health e backup de staging. |
| [docs/fase2-caua.md](docs/fase2-caua.md) | Situação dos itens de API, validação funcional e segurança da Fase 2. |
| [docs/arquitetura.md](docs/arquitetura.md) | Decisões de arquitetura. |

A documentação viva do contrato fica em `/swagger-ui.html` com a aplicação no ar.

## Executar localmente

```bash
cp .env.example .env     # preencha os CHANGE_ME
docker compose up -d --build
curl http://127.0.0.1:8080/actuator/health
```

Swagger em <http://127.0.0.1:8080/swagger-ui.html>. O frontend roda com `npm run dev` em
`frontend/` e alcança a API pelo proxy do Vite, sem precisar de CORS.

Para criar a primeira oficina, ligue `APP_BOOTSTRAP_ENABLED=true` no `.env` antes da primeira
subida e volte para `false` depois.

## Testes

```bash
cd backend && ./mvnw verify
```

Roda testes unitários e de integração. Os testes de integração sobem PostgreSQL e MinIO reais via
Testcontainers, portanto exigem Docker disponível. `verify` também checa a formatação (Spotless) e
aplica as migrations do Flyway.

## Configuração

Segredos e parâmetros de ambiente são lidos exclusivamente de variáveis de ambiente; nada sensível
fica no código ou na imagem. `.env.example` e `.env.staging.example` trazem apenas marcadores.
A lista completa está em [docs/staging.md](docs/staging.md#variáveis-de-ambiente).
