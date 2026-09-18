# Fase 6 — entrega do Cauã

Validação local em 16/09/2026 (America/Sao_Paulo; manifests em 17/09 UTC).
Escopo: observabilidade, integridade e preparação operacional. Frontend não alterado.
Não houve implantação nem piloto real.

## 1–4. Base, Git e arquivos

Branch conferida **antes de editar**: `feature/fase6-observabilidade-piloto`.
`git status` inicial limpo, sincronizado com origin; HEAD `56d4ad7`
(`feat: implement phase 5 forgotten revenue`). A base contém as entregas da Fase 5
de Cauã e Kauã, conforme confirmação explícita do responsável. O README antigo
ainda descrevia frontend pendente; foi atualizado, sem revalidar a interface nesta tarefa.

Estado final: mesma branch/HEAD, alterações locais sem staging. Nenhum commit, push,
merge, troca/criação de branch ou `git init`. `git diff --check` sem erros.

Arquivos alterados:

- `.env.example`, `.env.staging.example`, `.gitignore`, `README.md`, `compose.yml`.
- `backend/src/main/resources/application.yml`.
- `backend/src/main/java/br/com/garagem/auth/application/AuthService.java`.
- `backend/src/main/java/br/com/garagem/config/SecurityConfig.java`.
- `backend/src/main/java/br/com/garagem/tenancy/TenantRequestFilter.java`.
- `backend/src/main/java/br/com/garagem/shared/error/ApiErrors.java`, `ProblemJson.java`.
- `backend/src/main/java/br/com/garagem/shared/observability/RequestLoggingFilter.java`.
- `backend/src/main/java/br/com/garagem/ordemservico/foto/application/S3FotoStorage.java`.
- `backend/src/test/java/br/com/garagem/shared/observability/RequestLoggingFilterTest.java`.
- `docs/staging.md`.

Arquivos novos:

- `backend/src/test/java/br/com/garagem/Fase6IT.java`.
- `scripts/piloto-common.ps1`, `scripts/backup-piloto.ps1`, `scripts/restore-piloto.ps1`.
- `docs/fase6-caua.md`, `docs/backup-piloto.md`.

Evidências locais e credenciais sintéticas estão em `.tools/`, ignorado. Não são
parte da entrega versionável. Nenhuma migration existente foi alterada.

## 5–10. Erros, lentidão, autenticação e logs

O evento JSON `request_concluido` mantém timestamp, método, status, duração em ms,
`request_id`, `oficina_id`, `usuario_id` e acrescenta `erro_code`/`request_lento`.
Caminhos MVC usam o template; antes do MVC, segmentos livres são mascarados.
O link público e seus sufixos desconhecidos não expõem tokens. Query string, corpos,
headers de credenciais e arquivos não entram no evento. IDs de usuário/oficina
vêm apenas do contexto validado; autenticação recusada fica anônima.

`X-Request-Id` é retornado nas respostas e coincide com `requestId` do Problem
Details e `request_id` do log. IDs recebidos só são reutilizados com formato
restrito; demais são substituídos. MDC é limpo em `finally`, inclusive em exceção.
`ApiErrors`/`ProblemJson` propagam código estável ao MDC. Exceção fora do MVC antes
de resposta comprometida recebe 500/INTERNAL_ERROR no log; o tratamento MVC
inesperado registra classe da exceção sem sua mensagem/dados e sem stack trace ao cliente.

Classificação: **5xx → ERROR**; demais requests com **duração >= 1.000 ms → WARN**;
demais → INFO. `request_lento` também marca 5xx lentos. Variável `SLOW_REQUEST_MS`
configura limiar positivo. 4xx esperados não recebem ERROR. Health indisponível
sem Problem Details usa `HTTP_503` como código de fallback.

| Falha | Categoria segura em `auth_falha` |
|---|---|
| Login/senha, oficina inexistente ou usuário inativo | `LOGIN_RECUSADO` |
| Refresh inválido, expirado ou revogado | `REFRESH_RECUSADO` |
| Bearer inválido ou expirado | `BEARER_INVALIDO_OU_EXPIRADO` |
| Sem sessão | `SESSAO_AUSENTE` |
| Papel insuficiente | `PAPEL_NAO_AUTORIZADO` |
| Sessão com usuário/oficina/papel desatualizado | `SESSAO_DESATUALIZADA_OU_INATIVA` |
| Link público inválido/expirado/revogado | `LINK_PUBLICO_INVALIDO_OU_EXPIRADO` |

As categorias agrupam causas deliberadamente; não enumeram contas nem registram
email/slug de entrada, senha ou token. JWT expirado foi testado com token assinado.
Refresh revogado, logout idempotente, usuário desativado e papel insuficiente foram
revalidados por integração. Não foram introduzidos bloqueio de IP/rate limiting.

Auditoria local de `.tools/fase6-api.log`: **504 linhas JSON, 253 requests,
10 erros correlacionados, 12 requests lentos, 16 respostas 5xx**, estas decorrentes
dos ensaios de indisponibilidade. Checagem dos campos/classificações em todos esses
requests. Zero ocorrências dos valores reais usados no ensaio para senha de banco,
JWT secret, chaves MinIO, senha/email bootstrap, access/refresh/public token,
telefone/nome sintético e marcadores de entrada indevida. Startup, Flyway, conexão,
shutdown, health, autenticação e requests incluídos na captura. JSON UTF-8 legível;
scripts receberam BOM para mensagens corretas no Windows PowerShell 5.1.

Nenhum vazamento desses valores foi encontrado no log operacional. Foi corrigida
a possibilidade de segmentos livres/sufixos de URL chegarem ao log; os testes
cobrem a sanitização. Não há captura geral de PII por regex nem promessa de que
qualquer biblioteca futura será segura em DEBUG: manter níveis atuais e bindings/
wire logs desabilitados. Auditoria não autoriza publicar os arquivos brutos.

HTTP Docker confirmou 400/401/404/409/413/415 com código e correlação; 413 usou
multipart real de 12 MiB. 500 real foi provocado por login com PostgreSQL parado:
`INTERNAL_ERROR`, mesmo requestId, sem stack trace ao cliente e ERROR no log.
403 por papel e demais causas de autenticação estão na suíte automatizada;
403 anônimo no objeto MinIO foi validado por HTTP real.

Medição HTTP em `http://127.0.0.1:18086`, Docker Desktop local, Java 25,
dados sintéticos, três chamadas sequenciais por endpoint, incluindo leitura da resposta:

| Endpoint | N | Média ms | Máximo ms |
|---|---:|---:|---:|
| POST auth/login | 3 | 479,04 | 645,25 |
| GET clientes | 3 | 43,42 | 95,53 |
| GET veiculos | 3 | 27,33 | 43,39 |
| GET ordens-servico | 3 | 38,94 | 90,39 |
| GET ordens-servico/{id} | 3 | 31,05 | 49,79 |
| GET dashboard | 3 | 48,06 | 103,83 |
| GET ordens-servico/{id}/fotos/{id}/conteudo | 3 | 41,99 | 70,30 |
| GET ordens-servico/{id}/orcamento/versoes | 3 | 47,51 | 58,30 |
| GET publico/{token} | 3 | 53,33 | 74,40 |
| GET dinheiro-esquecido/oportunidades | 3 | 32,53 | 50,31 |
| GET dinheiro-esquecido/resumo | 3 | 24,64 | 27,15 |

Não é benchmark nem teste de carga; não define SLA/p95. Os requests lentos reais
foram observados na inicialização e nos testes de falha. Valores registrados em
`.tools/fase6-medicoes.json`; não inventados a partir do limiar.

## 11–15. Persistência, isolamento e saúde

Criado fluxo completo na stack `garagem-fase6`: oficina/usuário bootstrap, cliente,
veículo, OS, checklist, diagnóstico, foto PNG, duas versões de orçamento (2.000 e
1.800), decisão pública aprovada, timeline, próxima revisão, oportunidade, contato,
resultado recuperado de **1.450** e auditoria. Snapshot de todos os retornos e IDs
comparado integralmente após logout/login, restart da API, restart da stack
(`compose restart`, sem remover volumes), recuperação de PG/MinIO e ambos restores.

Foto: upload/download real, metadata preservada, chave pertencente à oficina,
acesso anônimo ao objeto **403**, SHA-256 idêntico em todas as releituras:
`67ab56295fc6fe1e28bc7d81f1c34c26741ae05c5552947599fee6ac74d68978`.
A verificação cobre o objeto da fixture; não é inventário global de órfãos de
ambientes do desenvolvedor. Nenhum objeto foi excluído durante auditoria.

As suítes Fase1/2/3/5 reexecutaram isolamento A/B em clientes, veículos, OS,
checklist, diagnóstico, fotos/downloads, orçamentos, links, timeline, oportunidades,
contatos, resultados e agregados/relatórios/dashboard; leitura, vínculo cruzado,
alteração e exclusão onde disponíveis. O tenant segue do contexto autenticado;
nenhuma nova forma de selecionar oficina via frontend foi introduzida.

Readiness agora inclui `readinessState,db,storage`; liveness permanece independente
das dependências. Somente health é exposto no Actuator, sem detalhes de componentes
ou credenciais. O agregado pode listar nomes dos grupos liveness/readiness.
Hikari tem espera de conexão padrão de 5 s (`DATABASE_CONNECTION_TIMEOUT_MS`),
validação 2 s; S3 limita chamada a 10 s e tentativa a 3 s.

| Condição isolada | Health | Readiness | Liveness |
|---|---|---|---|
| Normal | 200 UP | 200 UP | 200 UP |
| PostgreSQL parado | 503 DOWN (~5.127 ms) | 503 DOWN (~5.049 ms) | 200 UP |
| MinIO parado | 503 DOWN (~4.374 ms) | 503 DOWN (~316 ms) | 200 UP |
| Dependência reiniciada | 200 UP | Sondas retomadas | 200 UP |

Tempos são amostras observadas, não garantias. Apenas containers da stack isolada
foram parados; `garagem-saas` e `garagem-fase4` não foram alterados.

## 16–20. Backups e restauração

Procedimentos pré/pós, comandos, manutenção, variáveis, armazenamento restrito e
aceitação estão em [backup-piloto.md](../operations/backup-piloto.md). Os scripts usam dump custom
PostgreSQL e cópia fria completa MinIO, manifest e checksums. Preservam pré/pós,
não sobrescrevem pastas e recusam origem/destino ocupado/volume existente/arquivos
corrompidos. Testes negativos executados: origem, destino ocupado e dump alterado,
todos recusados antes de escrita no destino.

Ensaios locais, operador `Codex-ensaio-local`, aplicação `56d4ad7+working-tree-fase6`,
schema **1,2,3**, PostgreSQL `17.11-alpine`, MinIO
`RELEASE.2025-09-07T16-13-09Z.hotfix.7aa24e772`:

| Etapa | Pasta em `.tools/fase6-backups/` | Manifest UTC | Restore aceito |
|---|---|---|---|
| Pré | `garagem-piloto-pre-20260917-015617-248` | 2026-09-17T01:56:32.7964095Z | `garagem-fase6-restore-pre` :18087 |
| Pós | `garagem-piloto-pos-20260917-020313-012` | 2026-09-17T02:03:17.2166065Z | `garagem-fase6-restore-pos2` :18088 |

| Etapa/arquivo | Bytes | SHA-256 |
|---|---:|---|
| Pré/postgres.dump | 101410 | `2FDDBC49A1588B6C20949020872A33E118D234D43935E66640E6FACC5A239DAD` |
| Pré/minio.tar.gz | 5878 | `0F090CC2A894C8B8533B43E340223CE95FE7DBA39172F20FC03F02544674D146` |
| Pós/postgres.dump | 102175 | `6B350BE3772A455DEA161B780D7C85D8E5A5A7941A3300F8BD0821646264823C` |
| Pós/minio.tar.gz | 6801 | `705C025713046E1691E30EEAF7637A6D7770FFAC17724A40A6C5E54F1583344A` |

Imagem API registrada: `sha256:986f066e85fa6390b23134a1e23f258a7c616e8499da9f1128f3d7661289d300`.
Cada restore usou volumes próprios, health UP, migrations V1–V3 bem-sucedidas,
login, snapshot completo e download da foto. Um segundo cliente criado após o pré
retornou 404 no restore pré e 200 no pós. Total recuperado permaneceu 1.450.

O primeiro destino pós (`garagem-fase6-restore-pos`) revelou corrida: `pg_isready`
via socket respondia durante bootstrap. Corrigido para TCP `127.0.0.1`, disponível
somente no servidor definitivo da imagem. O teste foi repetido com sucesso em
`pos2`; o destino incompleto foi parado e preservado, sem reutilização/destruição.

Backups estão ignorados, contêm somente dados sintéticos neste ensaio e não foram
adicionados ao Git. Em piloto real, armazenar **fora do checkout**, acesso restrito,
criptografia e cópia fora do host; ensaio local não comprova essas condições reais.

## 21–23. Verificação e documentação

`mvn verify` com Java `.tools/jdk-25.0.4.1` e Maven 3.9.11: **BUILD SUCCESS**,
**15 testes unitários + 95 de integração**, zero falhas/erros/skips.
Integração: Fase1 22, Fase2 27, Fase3 15, Fase5 26, Fase6 5. Novos testes validam
correlação, níveis/campos, limpeza MDC, sanitização, limiar inclusivo, autenticação
e regressões. Evidência `.tools/fase6-verify.log` e relatórios surefire/failsafe.

Docker build e fluxo HTTP completos aprovados na API `:18086`, PG `:15436`,
MinIO `:19008`. Os restores usaram `:18087/:18088`. Nenhum frontend alterado;
nenhum script frontend executado. Fonte Java testada é a mesma da imagem Docker.
Scripts PowerShell foram analisados pelo parser e exercitados em backups/restores
reais locais, inclusive a correção da espera PG. Documentação: este relatório,
runbook de backup, README e staging atualizados, modelos de ambiente com limiares.

## 24–26. Pendências e riscos

Kauã: validar pela interface erros amigáveis e requestId útil ao suporte,
sessão/expiração/relogin, comportamento durante indisponibilidade, fotos e
persistência em nova sessão; executar o roteiro real de piloto e registrar falhas
com horários/requestIds. Nenhuma mudança obrigatória de contrato foi introduzida.

Piloto real: definir ambiente, oficina, operadores, janela e versão aprovada;
configurar secrets, TLS/proxy, acesso restrito, cópia remota e retenção; executar
backup pré + restore aceito antes de liberar; acompanhar logs/health/lentidão;
encerrar janela, comparar indicadores e executar backup pós + restore.

Limitações: sem carga/concurrência de usuários reais, sem SLO, alertas externos,
retenção central de logs ou backup agendado. Restores validados com imagens iguais;
cópia fria exige indisponibilidade e disciplina de escritores externos. SHA-256
não autentica origem. Volumes/segredos dependem da proteção do host. A amostra de
fotos é pequena e o timeout S3 de 10 s deve ser observado com fotos maiores em
rede real. Scripts não fazem rollback automático nem removem destinos parciais.
Stacks de restore foram paradas após a validação; volumes e backups foram mantidos.
A stack de origem do ensaio permanece ativa e saudável em `http://127.0.0.1:18086`.

**FASE 6 — PARTE DO CAUÃ CONCLUÍDA: SIM**

**BACKUP PRÉ-PILOTO REAL EXECUTADO: NÃO**

**BACKUP PÓS-PILOTO REAL EXECUTADO: NÃO**
