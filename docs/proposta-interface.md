# Proposta de interface — Fase 1 em avaliação

Atualizada em 15/09/2026 após leitura dos documentos e inspeção de Controllers, DTOs, repositórios e serviços. O frontend contém um **protótipo navegável com dados fictícios em memória**, autorizado para avaliação de UX. A implementação definitiva e a integração com a API continuam pendentes de aprovação.

## Direção de produto

A tela inicial é a lista de Ordens de Serviço, sem dashboard ou métricas comerciais. Fundo claro, menu verde escuro, ações em verde e indicadores discretos. Tabelas privilegiam leitura e operação; texto e cor comunicam situação em conjunto.

Menu: **Ordens de Serviço → Clientes → Veículos**. **Equipe** aparece para o proprietário, seguindo a proposta original. A API permite consulta da equipe por todos os papéis para atribuição de OS; restringir o menu é uma decisão de navegação, não uma regra de autorização do backend.

O cabeçalho identifica oficina, usuário, papel e saída. Login apresenta oficina (slug), e-mail e senha. O seletor de papel pertence exclusivamente à demonstração. Na implementação definitiva, o papel deve vir da sessão.

## Correspondência com a API

Caminhos abaixo relativos a /api/v1:

| Tela / ação | Capacidade real | Apresentação |
|---|---|---|
| Login e saída | POST /auth/login, /refresh, /logout | Fluxo simulado, sem tokens |
| Listagem de OS | GET /ordens-servico com busca, pagina, tamanho | Busca por placa, cliente ou número; tabela paginada |
| Abertura | POST /ordens-servico | Veículo, mecânico opcional, km, relato e previsão opcional |
| Resumo | GET /ordens-servico/{id} | Identificação, cliente, veículo, km, relato, datas, responsável e status |
| Responsável | PUT /ordens-servico/{id}/responsavel | Atribuição a mecânico ativo |
| Status | POST /ordens-servico/{id}/status | Próximas transições válidas e confirmação |
| Checklist | GET/POST /ordens-servico/{id}/checklist | Registro único, itens, condições livres e observações |
| Diagnóstico | GET/POST /ordens-servico/{id}/diagnosticos | Acréscimo de itens classificados |
| Orçamento | GET/POST /ordens-servico/{id}/orcamento/versoes | Versões, itens, valores, total e decisão |
| Fotos | GET/POST /ordens-servico/{id}/fotos; GET .../{fotoId}/conteudo | Galeria, ampliação, finalidade e vínculo opcional |
| Timeline | GET /ordens-servico/{id}/timeline | Eventos cronológicos, origem/autor e horário |
| Links | POST /ordens-servico/{id}/links; DELETE .../links/{linkId} | Emissão/revogação simuladas, sete dias |
| Visão do cliente | GET /publico/{token}; POST .../decisao | Prévia do resumo e decisão integral |
| Clientes | GET/POST /clientes; GET/PUT /clientes/{id} | Lista, busca por nome, cadastro, visualização e edição |
| Veículos | GET/POST /veiculos; GET/PUT /veiculos/{id} | Lista, busca por placa, cadastro, visualização e edição |
| Equipe | GET/POST /usuarios | Lista sem busca e cadastro pelo proprietário |

## Experiência da OS

- Número, veículo/placa, cliente, status, responsável, entrada e previsão na tabela. “Abrir OS” é a ação principal.
- Painel de abertura preserva o contexto da lista; veículo selecionado revela cliente vinculado e quilometragem mínima.
- Detalhe com identificação e abas **Resumo, Checklist, Diagnóstico, Orçamento, Fotos, Timeline**.
- Resumo prioriza relato, contato e andamento. Atribuição fica junto do responsável.
- Checklist não oferece edição após registro. Diagnósticos podem ser acrescentados, mas não alterados ou excluídos.
- Versão, situação e total do orçamento ficam explícitos. Nova versão reutiliza os itens como rascunho, preserva as anteriores e retorna a OS para Orçamento.
- Aprovar/recusar aparece na visão do cliente, com confirmação do valor integral. Aprovação inicia manutenção; recusa retorna para Orçamento.
- “Pronto” conclui a OS e retira alterações. Não existe cancelamento, reabertura ou status de entrega.
- Fotos iniciam vazias. PNG/JPEG pode ser vinculado a um único item de checklist ou diagnóstico.
- Painéis modais com Escape, foco contido e retorno do foco; abas navegáveis por teclado; campos identificados para leitores de tela.
- Notebook/tablet preservam tabelas com rolagem interna. Menu compacto em tablet. Não há rolagem horizontal da página inteira.

## Papéis apresentados

| Ação | Proprietário | Atendente | Mecânico |
|---|---|---|---|
| Consultar OS, clientes e veículos | Sim | Sim | Sim |
| Cadastrar/editar clientes e veículos | Sim | Sim | Não |
| Abrir OS / atribuir responsável | Sim | Sim | Não |
| Checklist, fotos e status | Sim | Sim | Sim |
| Acrescentar diagnóstico | Sim | Não | Sim |
| Criar versões e links | Sim | Sim | Não |
| Menu Equipe / cadastrar usuário | Sim | Não | Não |

Não existe papel GERENTE. A API deve validar todas as ações e o isolamento por oficina, independentemente da visibilidade de botões.

## Limites que afetam a integração

1. **OS retorna IDs:** OsSaida não inclui nomes e placa. Resolver cliente, veículo e responsável com consultas/cache por sessão e oficina; a lista de usuários também é paginada.
2. **Nome da oficina:** a sessão retorna oficinaId, mas não o nome. O protótipo usa oficina fictícia ou o identificador digitado.
3. **Cadastros limitados:** cliente tem nome, telefone e e-mail; veículo tem vínculo, placa, marca, modelo, ano, km e cor. Sem CPF, endereço, chassi ou histórico agregado nos DTOs.
4. **Equipe:** sem edição, desativação, exclusão, redefinição de senha ou busca. Somente papéis OWNER, ATENDENTE e MECANICO.
5. **Buscas:** sem filtros de status/data/responsável na OS, telefone/documento em cliente ou marca/modelo em veículo.
6. **Edição da OS:** sem endpoint geral para alterar relato ou previsão. Responsável exige mecânico ativo e não permite remoção por nulo.
7. **Histórico:** checklist único; diagnóstico aditivo; fotos sem edição/exclusão. Versões, decisões e eventos são imutáveis.
8. **Links:** token retornado uma única vez; sem listagem de links existentes. O protótipo acompanha apenas o último link simulado. Integração deve preservar ID para revogação e oferecer cópia imediata, sem prometer recuperar token.
9. **Recusa pública:** ao retornar a Orçamento, a API omite o orçamento no resumo público. A confirmação imediata da recusa fica no estado local; consulta posterior poderá mostrar “Orçamento em preparação”.
10. **Concorrência:** revisões e decisão sobre versão substituída podem retornar 409. Não há simulação multiusuário; recarga segura e revisão de dados precisam ser implementadas na integração.
11. **Valores:** cálculo local serve à avaliação. Total definitivo é o do backend, com BigDecimal e HALF_UP por item.

## Pendente de validação de produto

Aprovar identidade visual, densidade e ordem das colunas, navegação compacta no tablet, painéis laterais, localização das ações de status, vocabulário das etapas e confirmação do orçamento.

Após aprovação: integrar API, autenticação/refresh/logout, estados de carregamento, erros de rede, 401/403/409, fotos privadas, links reais e isolamento ponta a ponta.

Nenhuma funcionalidade das próximas fases, commit, push, merge ou publicação faz parte desta entrega.
