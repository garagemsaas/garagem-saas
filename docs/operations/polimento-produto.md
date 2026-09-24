# Administração, operação exclusiva e agenda de retornos

## Migração e implantação

Aplicar backend e frontend desta versão juntos. O Flyway executa a **V10**, sem modificar V1–V9. Antes da implantação, fazer backup e validar a restauração conforme o procedimento operacional existente. A versão antiga da aplicação não é compatível com as colunas retiradas; rollback exige restaurar o backup, não apenas trocar o executável.

A V10 arquiva os dados de apresentação retirados em `empresa_configuracao_arquivada`, sem API de leitura, preserva logotipo e favicon e remove as colunas de cores e publicação. Dados operacionais e históricos permanecem intactos. Empresas antes híbridas passam a REVENDA quando têm registro de estoque; caso contrário, OFICINA. A decisão fica registrada em `empresa_administracao_evento`. Conferir a classificação antes de liberar acesso e ajustar pela administração quando necessário.

Cada empresa tem uma operação. A preparação comercial usa custos registrados na revenda. Ordens internas e custos importados anteriormente continuam preservados no histórico; os caminhos de criação/importação via oficina foram retirados. Para estoque antigo ainda em preparação, conferir os custos já importados antes de registrar custos adicionais e disponibilizar o carro.

Os retornos abertos da oficina são copiados para a agenda, com data e responsável existentes. Quando não há responsável, usa-se um proprietário/atendente ativo. Sem pessoa elegível, o registro original permanece preservado; após regularizar o acesso, usar **Identificar contatos pendentes**. Esse comando respeita a operação atual e não duplica uma mesma origem, mesmo após cancelamento ou conclusão. Concluir um retorno não registra receita nem presume venda.

## Primeiro acesso da plataforma

Preparar as variáveis somente no ambiente seguro do backend:

- `APP_PLATAFORMA_BOOTSTRAP_ENABLED=true`
- `APP_PLATAFORMA_BOOTSTRAP_NOME`: nome do desenvolvedor responsável.
- `APP_PLATAFORMA_BOOTSTRAP_EMAIL`: e-mail individual.
- `APP_PLATAFORMA_BOOTSTRAP_SENHA`: senha exclusiva, de pelo menos 12 caracteres e até 72 bytes UTF-8.

Iniciar uma vez e remover a senha e a habilitação do bootstrap do ambiente após confirmar o acesso. O bootstrap é idempotente por e-mail e não redefine senha existente. Nenhuma conta ou senha padrão é criada. Acesse `/administracao`; não há atalho dessa área na interface dos clientes. A sessão administrativa dura 30 minutos, fica apenas em memória e exige nova autenticação ao recarregar. Sair revoga as sessões administrativas dessa pessoa.

Contas da plataforma vivem em `plataforma_usuario`, separadas de `usuario`. Para provisionar administradores adicionais, o operador de infraestrutura deve cadastrar conta individual com hash BCrypt de custo 12 e papel `ADMIN_PLATAFORMA`, pelo procedimento de acesso privilegiado da organização. Não usar credenciais compartilhadas nem copiar hashes de clientes. Alterar papel, desativar a conta ou incrementar `versao_sessao` invalida seu acesso em todas as requisições.

## Permissões

| Perfil | Permissões |
| --- | --- |
| Desenvolvedor da plataforma | Cadastrar/administrar empresas, consultar auditoria e editar identidade/imagens. Não entra nas APIs operacionais com a sessão administrativa. |
| Administrador da plataforma | Cadastrar empresas com primeiro proprietário, editar nome empresarial, situação e operação; consultar auditoria. Não edita identidade visual. |
| Proprietário | Operação da própria empresa e administração de usuários. Não altera o próprio perfil nem desativa o próprio acesso. |
| Atendente | Clientes, veículos e operações comerciais do modo autorizado, incluindo retornos. Não administra usuários. |
| Mecânico | Operação técnica da oficina conforme as permissões existentes. Não administra usuários nem acessa a agenda comercial; não é oferecido para novas contas de revenda. |

Situações SUSPENSA e INATIVA bloqueiam login, renovação, requisições autenticadas e links públicos, preservando os registros. Alterações administrativas da empresa invalidam as sessões existentes. Edição de usuário, redefinição de senha e mudança de situação também incrementam sua versão de sessão, além de revogar refresh tokens.

O tenant vem do JWT validado contra o banco, nunca de campos enviados pelo navegador. Relacionamentos usam chaves compostas por empresa. Os módulos são validados no backend pelas anotações dos endpoints. A agenda acrescenta filtro pela operação atual. Tentativas de acessar entidades de outra empresa retornam 404; ações sem o perfil necessário retornam 403.

## Identidade e agenda

Na administração, **Identidade** está disponível somente ao desenvolvedor: nome exibido, telefone, e-mail, contato e logotipo. PNG/JPEG até 2 MB e 4 milhões de pixels, decodificados e regravados sem metadados. Upload, substituição e remoção usam revisão otimista; atualização desatualizada retorna 409. Sem imagem, a interface exibe o nome. O favicon já cadastrado continua funcionando. Não existe personalização de cores.

Na empresa, **Usuários** reúne cadastro, edição, redefinição de senha e ativação/desativação. O proprietário entrega a senha pelo canal privado combinado; não há recuperação pública de senha nesta versão.

**Retornos** mostra cliente, telefone, veículo opcional, motivo, data/hora, responsável, prioridade, situação e observações no mesmo contexto. Ações: agendar, editar/reagendar, concluir com resultado e cancelar com motivo. Busca, situação, responsável atual e paginação reduzem a lista. A agenda usa o horário local da pessoa na tela e instantes UTC na API. Alterações são auditadas e encerramentos não podem ser sobrescritos.

## Configuração externa

A publicação anterior do frontend não identificou uma API pública de produção. Esta implementação não inventa uma URL nem altera secrets ou configurações de produção. Para liberar uso externo, configurar `VITE_API_BASE_URL` com a API HTTPS real e autorizar a origem real do frontend em CORS, além de banco, armazenamento privado e demais variáveis do backend. Build e testes locais não substituem essa configuração nem confirmam disponibilidade pública.

## Verificação

Frontend: `npm run typecheck`, `npm run lint`, `npm test`, `npm run build`, `npm run test:ui`. Backend: `mvn verify` com Java 25 e Docker para PostgreSQL/MinIO. Os testes de integração verificam persistência, concorrência, autenticação e isolamento; os testes de navegador usam API controlada para validar interação e acessibilidade em três tamanhos de tela. O resultado final das execuções deve acompanhar a entrega, sem confundir mocks de interface com testes em produção.

Também foi executado um fluxo local com navegador, API e PostgreSQL reais: cadastro de empresa, identidade e upload de logotipo, login e recarregamento, cadastro/edição/desativação de usuário e agendamento/reagendamento/conclusão de retorno. A leitura pela API confirmou a persistência. Esse fluxo encontrou e permitiu corrigir a renovação duplicada de sessão durante a montagem da interface: chamadas simultâneas agora compartilham uma única rotação, e respostas antigas não substituem nem encerram um login posterior. Há testes de regressão para os dois casos.

O ambiente dessa verificação foi temporário, com credenciais aleatórias removidas ao término. O armazenamento de fotos foi validado separadamente pela suíte de integração com MinIO real. No Docker Desktop, a primeira formatação do MinIO pode ultrapassar um minuto; o teste aguarda até três minutos pela prontidão, mantendo todas as verificações de upload e leitura.

Resultado em **24/09/2026**: 26 testes unitários e 165 testes de integração do backend passaram. Após a última limpeza de `OsService`, as 50 integrações de `Fase1IT`, `Fase2IT` e `PreparacaoIT` foram reexecutadas, junto dos unitários, com `spotless:apply verify` aprovado. No frontend, passaram 26 testes unitários, 87 cenários de navegador (desktop, tablet e celular), TypeScript, lint e build de produção. `git diff --check` também passou. As verificações são locais e não representam uma implantação em produção.
