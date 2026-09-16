# Matriz de permissões

Escrito para o time de desenvolvimento e para quem for revisar segurança.

Fonte de verdade: as anotações `@PreAuthorize` dos Controllers, `SecurityConfig` e
`TenantRequestFilter`. Cada linha desta tabela é verificada em
`Fase2IT#matrizDePermissoes`, que exercita as três roles contra cada operação.
Revisado em 15/09/2026.

## Papéis

São três, definidos em `usuario/domain/Papel.java`. Não há papel novo na Fase 2.

| Papel | Quem é |
|---|---|
| `OWNER` | Dono da oficina. Única role que administra a equipe. |
| `ATENDENTE` | Recepção e orçamento: cadastros, abertura de OS, orçamento e link do cliente. |
| `MECANICO` | Serviço: diagnóstico, checklist, fotos e andamento do status. |

O papel vem da claim `papel` do JWT e vira `ROLE_<PAPEL>` no Spring Security. A cada requisição o
`TenantRequestFilter` reconfere no banco que o usuário continua ativo, que a oficina continua ativa
e que **o papel do token ainda é o papel gravado**. Trocar o papel de alguém ou desativar a conta
tem efeito imediato, sem esperar o access token expirar.

## Matriz

| Operação | Método e caminho | Owner | Atendente | Mecânico |
|---|---|:--:|:--:|:--:|
| **Sessão** | | | | |
| Entrar | `POST /auth/login` | — | — | — |
| Renovar sessão | `POST /auth/refresh` | — | — | — |
| Sair | `POST /auth/logout` | — | — | — |
| **Equipe** | | | | |
| Listar equipe | `GET /usuarios` | ✅ | ✅ | ✅ |
| Cadastrar usuário | `POST /usuarios` | ✅ | ❌ | ❌ |
| **Clientes** | | | | |
| Listar / consultar | `GET /clientes`, `GET /clientes/{id}` | ✅ | ✅ | ✅ |
| Cadastrar | `POST /clientes` | ✅ | ✅ | ❌ |
| Atualizar | `PUT /clientes/{id}` | ✅ | ✅ | ❌ |
| **Veículos** | | | | |
| Listar / consultar | `GET /veiculos`, `GET /veiculos/{id}` | ✅ | ✅ | ✅ |
| Cadastrar | `POST /veiculos` | ✅ | ✅ | ❌ |
| Atualizar | `PUT /veiculos/{id}` | ✅ | ✅ | ❌ |
| **Ordens de serviço** | | | | |
| Listar / consultar | `GET /ordens-servico`, `GET /ordens-servico/{id}` | ✅ | ✅ | ✅ |
| Abrir OS | `POST /ordens-servico` | ✅ | ✅ | ❌ |
| Atribuir responsável | `PUT /ordens-servico/{id}/responsavel` | ✅ | ✅ | ❌ |
| Mudar status | `POST /ordens-servico/{id}/status` | ✅ | ✅ | ✅ |
| **Checklist** | | | | |
| Consultar | `GET /ordens-servico/{id}/checklist` | ✅ | ✅ | ✅ |
| Registrar | `POST /ordens-servico/{id}/checklist` | ✅ | ✅ | ✅ |
| **Diagnóstico** | | | | |
| Consultar | `GET /ordens-servico/{id}/diagnosticos` | ✅ | ✅ | ✅ |
| Registrar | `POST /ordens-servico/{id}/diagnosticos` | ✅ | ❌ | ✅ |
| **Orçamento** | | | | |
| Consultar versões | `GET /ordens-servico/{id}/orcamento/versoes` | ✅ | ✅ | ✅ |
| Criar versão | `POST /ordens-servico/{id}/orcamento/versoes` | ✅ | ✅ | ❌ |
| **Link público** | | | | |
| Emitir link | `POST /ordens-servico/{id}/links` | ✅ | ✅ | ❌ |
| Revogar link | `DELETE /ordens-servico/{id}/links/{linkId}` | ✅ | ✅ | ❌ |
| **Fotos** | | | | |
| Listar | `GET /ordens-servico/{osId}/fotos` | ✅ | ✅ | ✅ |
| Enviar | `POST /ordens-servico/{osId}/fotos` | ✅ | ✅ | ✅ |
| Baixar conteúdo | `GET /ordens-servico/{osId}/fotos/{fotoId}/conteudo` | ✅ | ✅ | ✅ |
| **Timeline** | | | | |
| Consultar | `GET /ordens-servico/{id}/timeline` | ✅ | ✅ | ✅ |
| **Dashboard** | | | | |
| Indicadores | `GET /dashboard` | ✅ | ✅ | ✅ |

Legenda: ✅ permitido · ❌ responde 403 · — não exige sessão.

## O que a matriz não cobre

Papel é só a primeira porta. Passar por ela não dá acesso a nada de outra oficina:

- **Oficina.** Toda consulta e escrita é filtrada pela oficina do token. Um `OWNER` da oficina B tem
  papel suficiente para `PUT /clientes/{id}`, e ainda assim recebe **404** ao apontar para um
  cliente da oficina A. É o que `Fase2IT#matrizDePermissoes` afirma na última verificação, e o que
  `isolamentoEntreOficinas` e `isolamentoNoSentidoInverso` cobrem recurso por recurso.
- **Referência cruzada.** Não basta o recurso destino existir: ele precisa ser da mesma oficina.
  Veículo não aceita cliente de outra oficina, OS não aceita mecânico de outra oficina, e os filtros
  que recebem `clienteId`/`mecanicoId` de outra oficina devolvem página vazia, nunca o registro.
- **Responsável pela OS.** Além da oficina, `OsService#validarMecanico` exige que o usuário esteja
  **ativo** e tenha o papel `MECANICO`. Indicar um `ATENDENTE` como responsável responde 400.
- **Estado da OS.** Permissão não vence regra de negócio: OS em `PRONTO` não aceita alteração, e a
  revisão enviada precisa bater com a atual, sob pena de 409.

## Endpoints sem sessão

| Caminho | Como é protegido |
|---|---|
| `POST /api/v1/auth/**` | São os endpoints de sessão. Login não revela qual credencial falhou; refresh e logout conferem o hash do token junto com a oficina. |
| `GET /api/v1/publico/{token}` e `POST /api/v1/publico/{token}/decisao` | O token de 43 caracteres **é** a credencial, escopada a uma única OS, com validade configurável (padrão sete dias). Formato errado, token inexistente, expirado ou revogado respondem 404 igualmente. O resumo devolve número da OS, status, descrição do veículo, previsão e orçamento disponibilizado; a recusa atual permanece consultável até nova versão. Nunca fotos, dados do cliente ou histórico privado. |
| `GET /actuator/health` e sondas | Só `status`. `show-details: never`, e nenhum outro endpoint do Actuator está exposto. |
| `/v3/api-docs`, `/swagger-ui/**` | Documentação do contrato. Não expõe dado de oficina. Em staging e produção, restrinja no proxy se não quiser a documentação pública. |

## Ao mexer nas permissões

1. Altere o `@PreAuthorize` do Controller.
2. Ajuste a linha correspondente em `Fase2IT#matrizDePermissoes` — a lista `regras` é a matriz.
3. Atualize esta tabela.
4. Rode `mvn verify`. A matriz falha em qualquer divergência entre código e tabela.

## Fase 5 — Dinheiro Esquecido

Acrescenta permissões comerciais sem mudar a matriz anterior. OWNER e ATENDENTE
já operam orçamento e relações com clientes; MECANICO continua restrito ao trabalho
operacional e não recebe acesso ao módulo de recuperação financeira.

| Operação | OWNER | ATENDENTE | MECANICO |
|---|:--:|:--:|:--:|
| Identificar/reconciliar oportunidades | ✅ | ✅ | ❌ |
| Listar, detalhe e históricos | ✅ | ✅ | ❌ |
| Registrar contato manual e próximo contato | ✅ | ✅ | ❌ |
| Atribuir responsável comercial | ✅ | ✅ | ❌ |
| Agendar, encerrar como perdida/descartada | ✅ | ✅ | ❌ |
| Registrar recuperação efetiva | ✅ | ✅ | ❌ |
| Resumo/relatórios | ✅ | ✅ | ❌ |
| Consultar/programar próxima revisão da OS concluída | ✅ | ✅ | ❌ |
| Consultar/programar reavaliação da versão recusada | ✅ | ✅ | ❌ |

Responsável comercial deve ser OWNER ou ATENDENTE ativo da mesma oficina; não se
confunde com o mecânico responsável da OS. Oficina sempre do contexto autenticado;
404 para leitura/escrita/vínculo alheio, página vazia para filtros alheios.
Programar revisão é exceção limitada após PRONTO: apenas data e revisão da OS,
com auditoria, sem reabrir nem alterar sua execução. Resultados e contatos imutáveis.

A matriz nova e os dois tenants são exercitados em `Fase5IT`, incluindo todos os
métodos para MECANICO e ausência de sessão, operações permitidas ao ATENDENTE,
atribuição incompatível/inativa, referências cruzadas e relatórios isolados.
