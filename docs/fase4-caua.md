# Fase 4 — OS completa e acompanhamento público — Cauã

Branch: `feature/fase4-os-public-tracking`. Base auditada: `ff99f9b`.
Working tree inicialmente limpo. Nenhuma branch criada/trocada; nenhum commit, push,
merge ou git init. Alterações locais para revisão. Fase 4 geral permanece em andamento.

## Escopo e estado inicial

As Fases 1–3 já entregavam todos os endpoints desta frente. Checklist, diagnóstico,
fotos privadas, orçamento versionado, links, decisões, timeline e integração React
existiam. Foram preservados login, clientes, veículos, OS/status e paginação do Kauã.
`OrderDetail.tsx` foi inspecionado em UTF-8: sem corrupção atual; não foi modificado.

Já estavam completos: isolamento por tenant, matriz de permissões, total calculado
no servidor, triggers de imutabilidade V1/V2, lock por OS, unicidade de versão/decisão,
armazenamento de hash de tokens aleatórios, upload validado por decodificação, bucket
privado e consulta de eventos persistidos. Os testes antigos foram reutilizados.

Faltavam regressões específicas de decisões contrárias e versões concorrentes,
expiração durante espera do lock, repetição da recusa/revogação e detalhes de contrato.
Foram encontrados os problemas abaixo; não houve reimplementação dos módulos.

## Bugs, reprodução, causa e correção

| Problema reproduzido | Causa raiz | Correção / regressão |
|---|---|---|
| Erro 400 público devolvia a credencial em `instance` | Spring MVC preenchia automaticamente com a URI real | `ApiErrors` define caminho público sem token; teste cobre 400 e 409 |
| Segunda revogação mudava data e duplicava timeline | Método sempre gravava timestamp/evento | Retorno antecipado quando já revogado; data original e evento único preservados |
| Serviço retornava 400 para arquivo acima de 10 MiB | Limite de tamanho compartilhava erro de entrada inválida | 413 `PAYLOAD_TOO_LARGE` no serviço e após regravação; multipart já tratava 413 |
| Decisão aceita depois de expirar enquanto aguardava lock | `now()` PostgreSQL corresponde ao início da transação | Revalidação sob lock com `clock_timestamp()`; teste sincroniza pelo lock real, sem espera de expiração |
| Recusa desaparecia da consulta pública ao recarregar | Resumo só mostrava orçamento a partir de AGUARDANDO_APROVACAO; recusa volta a ORCAMENTO | Mostra versão atual decidida em ORCAMENTO; nova versão não publicada continua oculta |
| Tela pública mantinha orçamento/botões após 404 | Tratamento de erro na atualização/decisão não invalidava dados antigos | Limpa resumo e mostra “Link indisponível”; dois testes × três viewports |

Os testes novos foram executados antes das correções: falhas esperadas ficaram nos logs
locais `.tools/fase4-regression-before.log`, `.tools/fase4-race-before.log` e
`.tools/fase4-ui-before.log`. Uma falha adicional da comparação de snapshots foi
identificada como diferença de precisão Java/PostgreSQL: o teste passou a comparar
duas leituras persistidas da versão, sem relaxar a verificação de imutabilidade.

## Contratos e endpoints

Nenhum endpoint criado/removido. Base `/api/v1`; `O=/ordens-servico/{id}`.

| Método / caminho | Regra |
|---|---|
| POST/GET `O/checklist` | Registro único, 1–100 itens; todos os papéis; sem atualização |
| POST/GET `O/diagnosticos` | Acrescenta itens classificados; escrita OWNER/MECANICO, leitura todos |
| POST/GET `O/fotos` | Multipart PNG/JPEG privado; metadata persistida; todos |
| GET `O/fotos/{fotoId}/conteudo` | Autenticado, vínculo e tenant verificados, bytes pela API |
| POST/GET `O/orcamento/versoes` | Nova versão OWNER/ATENDENTE; histórico consultável por todos |
| POST `O/links` | OWNER/ATENDENTE; URL e token exibidos uma vez |
| DELETE `O/links/{linkId}` | OWNER/ATENDENTE; 204 idempotente; revogação persistida |
| GET `/publico/{token}` | Sem JWT, capability de uma OS; somente resumo público |
| POST `/publico/{token}/decisao` | `{versaoId, aprovado}`; sem comentário/motivo |
| GET `O/timeline` | Todos, eventos persistidos em ordem crescente |

Detalhes, limites, parâmetros, exemplos, DTOs, códigos HTTP e códigos de erro estão em
[api-v1.md](api-v1.md#complementos-da-fase-4--os-e-acompanhamento-público).
Mudanças funcionais: 413 consistente no serviço de fotos, revogação sem novo evento,
comprovante da recusa atual visível e erros públicos sem credencial. Nenhum novo código de erro.

OpenAPI recebeu parâmetro explícito `token`, respostas 413/415/503 de fotos, TTL
configurável e regras de checklist, diagnóstico, versionamento e revogação.
Exemplos genéricos 404 foram retirados de respostas com outros status.

## Versionamento, revisão e concorrência

Total é soma dos subtotais arredondados por item em HALF_UP; frontend não define total.
Versões numeradas sequencialmente sob lock pessimista da OS. Criações concorrentes
geram versões distintas, sem sobrescrever v1. V1 bloqueia UPDATE/DELETE de versões,
itens, decisões e eventos; V2 impede inserção tardia de itens e valida total no commit.
Não foi necessária migration nova; migrations aplicadas permaneceram intactas.

Nova versão é operação de acréscimo, sem campo revisão no contrato. Status e responsável
preservam a revisão otimista; tentativa com revisão antiga responde 409 `CONFLICT`.
Não foi removido controle de concorrência para integração.

Decisão, nova versão e revogação serializam pelo mesmo lock. Decisões iguais são
idempotentes; opostas resultam em um sucesso e um 409, com um único registro/evento.
Versão substituída não pode ser aprovada. Após revogar/expirar, mesmo repetição de
decisão recebe 404. O teste de lock cobre tanto expiração quanto revogação persistida
durante a espera da decisão.

## Link público, decisão e segurança

Token de 256 bits via SecureRandom, 43 caracteres Base64 URL. Só hash SHA-256 é
persistido. Escopo é a OS; não uma versão fixa. O request exige a versão vista pelo
cliente e o backend confronta a versão atual. Criação, expiração e revogação ficam no banco.
TTL configurável com padrão sete dias. URL usa fragmento; credencial não é enviada
ao servidor de páginas. Logs de acesso mascaram token; erros agora também não o ecoam.

Token inválido, desconhecido, revogado e expirado respondem 404. O resumo inclui
número, status, descrição do veículo, previsão e orçamento disponibilizado. A versão
conserva seu ID necessário à decisão e IDs de itens do contrato existente. Não devolve
oficina, cliente, usuário, e-mails internos, IP, fotos ou timeline. Observações do orçamento
são conteúdo destinado ao cliente; observações de checklist/relato/diagnóstico não são públicas.

Aprovação persiste versão, link, timestamp, canal, IP e `aprovado=true`, levando a
EM_MANUTENCAO. Recusa grava `false`, volta a ORCAMENTO e preserva comprovante público
enquanto não houver nova versão. Repetir a mesma decisão retorna 200 sem evento extra;
trocar decisão retorna 409. A oficina consulta a decisão nas versões e na timeline.

## Fotos e timeline

Fotos de até 10 MiB e 20 megapixels; tipo real validado por decodificação. Imagem regravada
para remover metadados, chave formada por UUIDs. Metadata no PostgreSQL, objeto no MinIO/S3.
Download autenticado com `no-store`/`nosniff`. Sem endpoint de exclusão no contrato.
Foto ou vínculo inexistente: 404; objeto perdido/storage indisponível: 503.
Rollback tenta limpar objeto; falha de processo entre storage e commit ainda pode deixar órfão.

Timeline: OS_ABERTA, RESPONSAVEL_ALTERADO, CHECKLIST_REGISTRADO, DIAGNOSTICO_REGISTRADO,
FOTO_ADICIONADA, ORCAMENTO_VERSIONADO, LINK_CRIADO, LINK_REVOGADO, ORCAMENTO_APROVADO,
ORCAMENTO_RECUSADO, STATUS_ALTERADO. Não foram inventados tipos novos. Eventos persistidos
na transação, com timestamp, origem e autor; cliente usa origem LINK_PUBLICO e autor nulo.
Consulta ordena por timestamp e ID; frontend renderiza dados da API.

## Autorização e multi-tenancy

Matriz preservada. Todos registram checklist/fotos e consultam recursos internos;
diagnóstico apenas OWNER/MECANICO; versões e links apenas OWNER/ATENDENTE.
Checklist não possui papel autenticado proibido: validação negativa é ausência de sessão.
Revogação recebeu cobertura específica de 401/403. O tenant vem do JWT validado ou
do hash de capability público. Não se confia em oficinaId do cliente.

Testes existentes cobrem leituras/escritas cruzadas, associações, fotos, versões,
criação/revogação de links e timeline nos dois sentidos. FKs compostas reforçam isolamento.

## Testes e evidências

Novos métodos em `Fase1IT`, reutilizando fixtures/helpers e PostgreSQL/MinIO existentes:

- `fase4ErrosPublicosNaoExponhemToken`
- `fase4RevogacaoRepetidaPreservaDataEEvento`
- `fase4UploadAcimaDoLimiteResponde413`
- `fase4VersoesConcorrentesPreservamHistorico`
- `fase4DecisoesContrariasConcorrentesNaoSeSobrescrevem`
- `fase4RecusaRepetidaEContrariaEExpiracao`
- `fase4ExpiracaoERevogacaoDuranteEsperaDoLockImpedemDecisao`
- `fase4ValidacaoDeChecklistDiagnosticoERevogacao`
- `fase4TimelinePersistidaCronologicaESemCredenciais`
- `fase4OpenApiDescreveFotosETokenPublico`

O fluxo HTTP real existente foi ampliado para v1 → v2, comparação do snapshot v1,
consulta pública sem autenticação e aprovação de v2, além de login, cliente, veículo,
OS, checklist, diagnóstico, upload/download, metadata, bucket privado, status e timeline.
Fase2IT mantém matriz de papéis/isolamento; Fase3IT mantém integração anterior;
RegrasOsTest mantém cálculos e máquina de estados.

Frontend: novo `tests/browser/public-tracking.spec.mjs`, com dois cenários em desktop,
tablet e mobile. As fixtures permanecem restritas a testes; produção não ganhou mocks.

Frontend: lint, typecheck, 23 testes unitários e build passaram. Nos 24 cenários de
navegador, 21 passaram na primeira execução; três cenários antigos falharam por timeout
ou foco durante execução concorrente com Java/Docker. Reexecutados com `--last-failed
--workers=1`, os três passaram sem mudar testes ou código dessas telas. As seis regressões
novas passaram na primeira execução. Isso não elimina o risco de instabilidade da suíte
sob contenção de recursos.

Primeira execução completa do backend: 12 unitários e 64 de integração verdes; falha
somente no Spotless devido à última edição de OpenAPI. Formatação aplicada.
**Execução final: BUILD SUCCESS**, 12 unitários + 64 de integração, zero falhas/erros/skips,
em 4min55s, incluindo Spotless. Comando executado com JAVA_HOME no JDK portátil:

```powershell
C:\Projetos\garagem-saas\.tools\apache-maven-3.9.11\bin\mvn.cmd -B -ntp -f C:\Projetos\garagem-saas\backend\pom.xml -Dmaven.repo.local=C:\Projetos\garagem-saas\.tools\m2 verify
```

Evidência local: `.tools/fase4-verify-final.log`. Testcontainers usou PostgreSQL e MinIO
reais na Fase1IT; não foi usado TEST_DATABASE_URL com storage falso nessa execução.

Docker Compose: build do Dockerfile existente concluído, projeto `garagem-fase4`, portas
isoladas 18084 (API), 15434 (PostgreSQL), 19004/19005 (MinIO). PostgreSQL e API healthy;
minio-init terminou com exit 0; health HTTP 200 UP; Swagger e OpenAPI HTTP 200.
Volumes próprios `garagem-fase4_postgres-data` e `garagem-fase4_fotos-data`.
Nenhum volume do desenvolvedor foi destruído ou reutilizado.

Smoke HTTP real no Compose passou: login → cliente → veículo → OS → checklist → diagnóstico
→ upload/download → v1/v2 com snapshot histórico preservado → link → consulta → aprovação
ou recusa → repetição e conflito → revogação → timeline. Acesso anônimo ao objeto MinIO
retornou 403. O primeiro acesso ocorreu durante a inicialização e falhou por socket fechado;
reexecutado com a API healthy, passou integralmente.

Navegador com API real, sem interceptação/mocks: login e listagem de OS persistidas;
comprovantes públicos de aprovação e recusa visíveis após reload, sem botões de nova decisão.
Logs locais: `.tools/fase4-compose.log`, `.tools/fase4-runtime.log`,
`.tools/fase4-browser-real.log`. Scripts de smoke e credenciais descartáveis de teste ficam
apenas em `.tools`, ignorado pelo Git. Stack isolada deixada disponível para revisão em
`http://127.0.0.1:18084`; nenhum deploy externo.

## PENDÊNCIA PARA KAUÃ e riscos restantes

- Aceite de produto da frente própria da Fase 4; esta entrega não declara encerramento geral.
- Gerenciamento de links após recarregar a sessão: contrato atual só devolve token na criação,
  não lista links emitidos. A interface preserva o link apenas em memória. Planejar listagem
  de metadata/revogação de links anteriores na frente de produto, sem recuperar token pelo hash.
- Atualização automática da oficina após decisão pública: hoje há recarga manual dos dados.
- Staging externo continua separado; não houve deploy externo.
- Reconciliação de objetos órfãos do storage continua risco operacional conhecido.
- Suíte de navegador apresenta sensibilidade a contenção de recursos; três cenários antigos
  precisaram de reexecução serial. Não foi alterada a lógica desses testes para fazê-los passar.

## Arquivos e estado do Git

Modificados: README; docs/api-v1.md; docs/permissoes.md; OpenApiConfig; PublicoController;
OsController; OsService; FotoController; FotoService; ApiErrors; Fase1IT; PublicOrder.tsx.
Novos: este documento e frontend/tests/browser/public-tracking.spec.mjs.
Nenhuma migration; nenhum arquivo de autenticação ou integração da Fase 3 substituído.
Logs/ambiente Docker temporários em `.tools` ignorado, sem credenciais versionadas.

Estado final: 12 arquivos rastreados modificados e 2 novos, sem staging; HEAD continua
`ff99f9b`, na mesma branch. `git diff --check` passou. Nenhum commit/push/merge.

Checklist, diagnóstico, orçamento, imutabilidade, decisões, links/expiração/revogação,
fotos, timeline, papéis, tenants, concorrência, documentação/OpenAPI e integração passaram
nas validações descritas. A frente do Cauã está concluída; Fase 4 geral em andamento,
com as pendências de produto do Kauã explicitadas acima.

FASE 4 — PARTE DO CAUÃ CONCLUÍDA: SIM
