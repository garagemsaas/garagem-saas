# Empresa, módulos e identidade visual

A Plataforma Automotiva usa uma aplicação, uma base de código e um banco compartilhado com isolamento por tenant. A tabela `oficina`, suas chaves `oficina_id`, os claims JWT e os pacotes Java permanecem por compatibilidade; representam a empresa contratante.

## Modelo

`oficina.nome` continua sendo o nome empresarial. `nome_exibicao` é o nome fantasia apresentado, com fallback para `nome`. Telefone, e-mail, contato/endereço, duas cores hexadecimais e revisão de branding ficam na mesma linha. Não há duplicação da entidade tenant. `situacao` controla ATIVA/INATIVA.

`empresa_modulo` representa capacidades combináveis: OFICINA, REVENDA ou ambas. Empresas existentes recebem OFICINA na V6. A empresa nasce com exatamente os módulos contratados — `provisionar_empresa` cria empresa, módulos e trilha numa transação só, e o bootstrap usa essa mesma função. O padrão OFICINA continua existindo, mas como gatilho adiado para o fim da transação: ele só age quando ninguém declarou módulo algum, de modo que uma empresa de revenda nunca chega a existir como oficina. REVENDA é somente capacidade registrada, sem estoque, leads, propostas ou vendas.

A exigência de módulo é declarada no próprio endpoint, com `@RequerModulo(OFICINA)` na classe do controlador ou em um método específico, e aplicada por `ModuloInterceptor`. Não há lista de caminhos: criar uma rota nova de oficina não exige lembrar de atualizar configuração alguma. Controlador que serve qualquer empresa declara `@SemModulo`, e `ContratoDeModuloTest` reprova o build se um `@RestController` não disser uma coisa ou outra — a omissão deixou de ser uma resposta possível. A resposta para módulo indisponível é 404, não 403: para a empresa, a funcionalidade não existe. Clientes, veículos e usuários são cadastros compartilhados, ainda com suas regras atuais. A navegação operacional só é montada depois de carregar os módulos. Uma empresa apenas REVENDA recebe a configuração de identidade, sem telas de operação inexistentes.

## API e autorização

- `GET /api/v1/empresa`: identidade, módulos e situação da empresa autenticada.
- `PUT /api/v1/empresa`: OWNER altera nome de exibição, contato e cores; revisão obrigatória, conflito 409. Campos desconhecidos são ignorados e nunca determinam tenant, módulos ou status.
- `POST /api/v1/empresa/imagens/{logo|favicon}`: multipart `arquivo` e `revisao`, apenas OWNER.
- `GET /api/v1/empresa/imagens/{tipo}/{id}`: imagem privada da empresa da sessão; imagem de outra empresa responde 404.
- `GET /api/v1/publico/{token}/empresa`: somente branding da empresa resolvida pelo token público válido.
- `GET /api/v1/publico/{token}/empresa/imagens/{tipo}/{id}`: mesma restrição de tenant, validade e revogação do token.

Não existe endpoint com empresaId para selecionar ou administrar outro tenant. A versão pública não expõe módulos ou situação. Identidade e imagens usam `Cache-Control: no-store`. O backend mantém requestId, erros padronizados, CORS e headers existentes.

## Imagens

PNG e JPEG de até 2 MiB e 4 megapixels. MIME, extensão permitida e formato decodificado são verificados. SVG, GIF, WebP e arquivos inválidos são recusados nesta versão. WebP poderá ser acrescentado com um decoder mantido e testes específicos; não foi acrescentada dependência nativa nesta fase.

As imagens são decodificadas e regravadas como PNG, sem metadados enviados. O limite também vale para o resultado regravado. Armazenamento em `empresa_imagem.conteudo` (bytea), no máximo duas imagens por empresa, dentro da transação de revisão do branding. Não há caminhos derivados do nome original, URLs externas, objetos órfãos ou bucket público. UUID novo na substituição invalida URLs antigas. A chave composta e a consulta obrigatória por `oficina_id` isolam o conteúdo.

`BrandingProvider` centraliza nome, logo, título, favicon e variáveis de cor; libera object URLs e restaura a identidade neutra ao desmontar. Cores têm formato restrito e texto de botões escolhe preto/branco pelo contraste. Favicon é carregado pela API autenticada ou pelo token público, nunca por ID de empresa fornecido pelo navegador.

## Estado e contratos antigos

Login, refresh e cada requisição autenticada consultam situação ATIVA. Sessões de empresa INATIVA são recusadas com 401; links públicos respondem 404. Não é necessário aguardar expiração do JWT. A inativação não apaga registros. Uma requisição já autorizada e em andamento pode terminar; esta fase não introduz cancelamento distribuído de transações.

O core não consulta assinatura nem cotas comerciais em lugar nenhum: usuários, veículos, ordens e fotos são criados sem passar por cobrança. Limites técnicos de upload, paginação e rate limiting continuam.

Classificação do legado:

| Categoria | Destino |
| --- | --- |
| Interface de assinatura, planos, preços e aviso 402 | Removida do frontend |
| Controllers e serviços de billing/webhook | Removidos do código. `FronteirasDeModuloTest.billingLegadoNaoVoltaAoCodigo` impede que voltem |
| Provedor manual e contratos internos | Retidos para o legado, sem gateway real novo |
| Tabelas plano, assinatura, evento_cobranca e webhook | Preservadas pelas migrations, com histórico e proteções de imutabilidade. Nenhum código as lê |
| Gatilho de criação automática de trial | Desativado por V6, sem apagar contratos |
| Dependências de billing em serviços operacionais | Removidas |

Não habilitar o legado em produção para ativar empresas: use situação e módulos. Os testes do legado criam explicitamente um contrato histórico e continuam verificando HMAC, idempotência e isolamento; os testes de cotas agora comprovam que elas não bloqueiam a operação.

## Próxima fase: veículo

`veiculo.cliente_id` obrigatório ainda representa o responsável no atendimento de oficina. Para revenda será preciso separar identidade física do veículo de propriedade/posse e transações comerciais. Avaliação, aquisição, estoque e venda deverão ter entidades próprias, períodos de titularidade e seus vínculos, sem reescrever histórico de OS. A placa única por tenant exige decidir como tratar veículos sem placa, troca de placa e identificação por chassi. As FKs compostas por tenant devem acompanhar cada novo vínculo. Nenhum desses fluxos foi implementado aqui.
