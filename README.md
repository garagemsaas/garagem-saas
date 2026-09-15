# Garagem SaaS

SaaS para oficinas mecânicas.

O Garagem SaaS organiza a operação da oficina desde o cadastro do cliente e do veículo até o diagnóstico, orçamento, aprovação, execução e conclusão da ordem de serviço.

O projeto utiliza arquitetura de monólito modular, API REST, frontend React e isolamento multi-tenant por oficina.

---

## Estado atual

### Fase 2 — Implementação entregue; aceite integrado pendente

Em 15/09/2026, a implementação do escopo de frontend atribuído a **Kauã foi
finalizada**, somando-se à entrega de API, contratos, segurança e infraestrutura
documentada por Cauã na branch `feature/fase2-api-contracts-security`.

O frontend inclui:

- Cliente HTTP único, gerenciamento de sessão, renovação de token e proteção das telas autenticadas.
- Login, listagem, cadastro e edição de clientes e veículos.
- Listagem, abertura e detalhe de OS, checklist, diagnóstico, orçamento versionado, fotos e histórico.
- Página pública do cliente com aprovação/recusa e confirmação da decisão.
- Componentes reutilizáveis de formulário, status, tabela e timeline; estados de carregamento, erro e vazio.
- Responsividade para desktop, tablet e celular, formulários acessíveis, confirmações de ações importantes e ícones exclusivamente Lucide.
- Remoção dos dados demonstrativos do código de produção e correção dos trechos incompatíveis que impediam a compilação.

**Verificações do frontend aprovadas:** lint, TypeScript, build de produção,
18 testes unitários e 12 testes de navegador. Os cenários de navegador exercitam
o fluxo operacional em 1440×1000, 768×1024 e 390×844, com verificações automáticas
de acessibilidade. Usam respostas de API controladas apenas na suíte de testes.

O [relatório do backend](docs/fase2-caua.md) registra uma execução anterior de
`mvn verify` com 12 testes unitários e 39 de integração aprovados. Esses testes
não foram reexecutados nesta entrega do frontend.

**A Fase 2 ainda não está homologada de ponta a ponta.** Falta validar o frontend
atual contra a API com PostgreSQL e MinIO reais: o Docker Engine local não ficou
disponível durante a validação. A implementação da parte de Kauã está concluída;
o encerramento integral da fase depende desse aceite. Staging também permanece
sem deploy externo.

O consumo do endpoint agregado `/dashboard` e dos novos filtros/ordenações no
servidor permanece como evolução documentada; as telas mantêm os cálculos e
buscas locais sobre as páginas carregadas da API.

Confira o [checklist completo de Kauã e as evidências](docs/fase2-kaua-frontend.md)
e as [instruções do frontend](frontend/README.md). Esta entrega está salva
localmente, ainda sem commit ou push das alterações do frontend.

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
```

---

## Documentação

| Documento | Conteúdo |
|---|---|
| [docs/api-v1.md](docs/api-v1.md) | Inventário dos contratos REST: endpoints, DTOs, paginação, filtros, ordenação e dashboard. |
| [docs/api-errors.md](docs/api-errors.md) | Formato único de erro (RFC 7807) e a tabela de códigos estáveis. |
| [docs/permissoes.md](docs/permissoes.md) | Matriz de permissões por papel e o que ela não cobre. |
| [docs/staging.md](docs/staging.md) | Requisitos, variáveis, subida, validação, logs, health e backup de staging. |
| [docs/fase2-caua.md](docs/fase2-caua.md) | Situação dos itens de API, validação funcional e segurança da Fase 2. |
| [docs/fase2-kaua-frontend.md](docs/fase2-kaua-frontend.md) | Checklist do frontend, testes aprovados e pendência de aceite integrado da Fase 2. |
| [frontend/README.md](frontend/README.md) | Execução, testes, componentes e comportamento do frontend. |
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

Para verificar o frontend, a partir da raiz do projeto:

```bash
cd frontend
npm ci
npm run lint
npm run typecheck
npm test
npm run build
npx playwright install chromium
npm run test:ui
```

A suíte de navegador inicia o Vite automaticamente e usa API simulada. Para o
aceite integrado, execute também o fluxo com o backend real e uma oficina de teste
provisionada, conforme [o checklist da entrega](docs/fase2-kaua-frontend.md#limite-da-validação-e-aceite-integrado).

## Configuração

Segredos e parâmetros de ambiente são lidos exclusivamente de variáveis de ambiente; nada sensível
fica no código ou na imagem. `.env.example` e `.env.staging.example` trazem apenas marcadores.
A lista completa está em [docs/staging.md](docs/staging.md#variáveis-de-ambiente).
