# Arquitetura da Fase 1

Backend único, organizado por funcionalidade, com API REST `/api/v1`, DTOs e transações nos serviços. Java 25, Spring Boot 3.5.16 e JUnit 5. A linha 3.5 mantém o JUnit 5 exigido e suporta Java 25: https://docs.spring.io/spring-boot/3.5/system-requirements.html.

## Dependências e escolhas

- Spring Security OAuth2 Resource Server/Nimbus: valida assinatura, expiração, emissor e audiência do JWT. Access token dura 15 minutos. Refresh token opaco dura 7 dias, é armazenado como SHA-256 e rotacionado sob lock; logout revoga o refresh. Senhas usam BCrypt com custo 12 e limite de 72 bytes.
- springdoc: gera OpenAPI a partir dos controllers e das validações dos DTOs, disponível em `/v3/api-docs` e `/swagger-ui/index.html`.
- SDK AWS S3: permite fotos privadas em armazenamento compatível com S3. MinIO no Compose é a alternativa local; fornecedor e bucket de produção continuam configuráveis. As credenciais root do MinIO são exclusivas do desenvolvimento; produção exige credencial restrita ao bucket.
- Spotless/Google Java Format: formatação determinística, verificada no mesmo comando do CI.
- Sem Lombok ou mapeador adicional. Entidades usam acesso por campo; a API retorna somente records de DTO, nunca entidades ou senhas.

## Isolamento

`TenantRequestFilter` resolve a oficina antes da transação JPA. `@TenantId` aplica o discriminator automaticamente às consultas JPQL. `TenantRepository` expõe somente leituras com `oficina_id` explícito, além de `save`. Não expor `findById`, native queries ou `EntityManager.find` em serviços de negócio. Chaves estrangeiras `(id, oficina_id)` impedem relações entre oficinas. `open-in-view=false` evita abrir sessão antes de resolver o tenant; contexto e MDC são limpos no `finally`.

Exceções de controle necessárias e auditáveis, todas em JDBC parametrizado:

1. Login resolve oficina por slug + e-mail do usuário.
2. Refresh/logout exigem oficina e hash do token; filtros e joins incluem a oficina.
3. JWT é confrontado com usuário ativo e papel atual na mesma oficina a cada request.
4. Link público resolve apenas seu escopo pelo hash de um token aleatório de 256 bits. Depois disso, todas as leituras e escritas usam o tenant resolvido.
5. Bootstrap provisiona explicitamente uma oficina via configuração do operador. Não há cadastro público nem cobrança neste estágio.
6. Número da OS é alocado por incremento transacional da linha da própria oficina.

A ausência de tenant resolve para um UUID reservado sem dados. Inserções sem contexto falham. Jobs futuros deverão estabelecer e limpar o contexto por oficina.

## Fluxo e orçamento

`RECEBIDO → DIAGNOSTICO → ORCAMENTO → AGUARDANDO_APROVACAO → EM_MANUTENCAO → TESTE → PRONTO`.

`EM_MANUTENCAO ↔ AGUARDANDO_PECA`; revisão ou recusa retorna para `ORCAMENTO`. O cliente precisa aprovar a versão atual antes da manutenção. A aprovação integral registra data, IP direto da conexão, canal e link. Headers de IP enviados pelo cliente não são confiados. Um proxy de produção deve ter política de confiança definida antes de alterar esse comportamento.

Cada versão possui seus próprios itens e preços. Total é soma dos subtotais arredondados por item em HALF_UP para duas casas. Atualizações/exclusões de versões, itens, decisões e eventos são bloqueadas por triggers. A migration V2 impede inserir itens em uma versão criada em outra transação e verifica, no commit, que o total corresponde à soma de pelo menos um item. Os métodos de escrita serializam por lock da OS, incluindo criação de versões, decisão e revogação de links. Atualizações de status também exigem a revisão recebida da interface. Decisão repetida igual é idempotente; decisão diferente e versão substituída retornam 409.

Links duram 7 dias e podem ser revogados. A URL da interface leva token no fragmento, para não enviá-lo a servidores de páginas ou em Referer. A API nunca registra a URL do request, corpo, senha ou token nos logs. O DTO público expõe apenas identificação da OS, veículo, status, previsão e orçamento disponibilizado.

## Fotos

Aceita PNG/JPEG de até 10 MB e 20 megapixels. O backend decodifica e regrava a imagem para validar conteúdo e retirar metadados. A chave contém oficina, OS e UUID, sem utilizar nome de arquivo enviado. Download passa pela autenticação e pelo mesmo isolamento; bucket não é público. Rollback do banco tenta remover o objeto enviado e registra falha de limpeza por ID para acompanhamento operacional. Uma falha de processo entre S3 e commit ainda pode deixar um objeto órfão; reconciliação operacional será necessária em produção.

## Papéis

| Ação | OWNER | ATENDENTE | MECANICO |
|---|---|---|---|
| Consultar cadastros, equipe e OS | sim | sim | sim |
| Cadastrar/editar cliente e veículo | sim | sim | não |
| Abrir OS e atribuir mecânico | sim | sim | não |
| Checklist, fotos e status | sim | sim | sim |
| Diagnóstico | sim | não | sim |
| Versões e links de orçamento | sim | sim | não |
| Cadastrar usuário | sim | não | não |

## Migrações

`V1__nucleo_operacional.sql` cria as tabelas, índices, vínculos e triggers. `V2__selar_itens_e_validar_totais.sql` reforça a imutabilidade dos itens e confere os totais. `ddl-auto=validate` em todos os ambientes. O rollback explícito em `backend/db/rollback` serve apenas para base vazia/descartável: remover schema destrói dados. Para ambiente com dados, restaurar backup validado ou criar uma migration corretiva; jamais editar migration aplicada.

## Limites desta entrega

Sem fases 2–4. Sem cancelamento/reabertura de OS, aprovação parcial, catálogo de peças ou cobrança. Checklist é registrado uma vez; diagnóstico permite acrescentar itens. Correções estruturais de histórico exigem regra de negócio antes de novos endpoints. Interface React aprovada preservada e integrada aos contratos existentes; validação final em [fase1-finalizacao.md](fase1-finalizacao.md). Infraestrutura de staging precisa de destino e estratégia aprovados antes de configurar deploy.
