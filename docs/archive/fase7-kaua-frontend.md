# Fase 7 — frente comercial e onboarding (Kauã)

## Base e escopo

Entrega na branch `feature/fase6-observabilidade-piloto`, conforme solicitado, após
atualização com `fa3a760` e `origin/main` em `df11aae`. Não foi criada outra branch.
O commit recebido do colaborador tratava da fase 6; nenhum contrato adicional de fase 7
estava presente na base inspecionada. Backend, autenticação e regras de negócio preservados.

## Checklist de entrega

- [x] Página institucional responsiva em `/institucional`, com produto, demonstração e oferta.
- [x] Apresentação comercial editável em PPTX, com 6 slides.
- [x] Vídeo de demonstração WebM, aproximadamente 59 segundos, sem áudio e legendado.
- [x] Roteiro de venda, diagnóstico da rotina, objeções e próximos passos.
- [x] Procedimento de onboarding assistido da oficina.
- [x] Ajuda pública em `/ajuda`, acessível também pelo login e guia interno.
- [x] Preparação inicial conectada à API existente, com atalhos e estados de consulta/erro.
- [x] Textos de uma oferta V1 sob consulta, sem preço ou diferenciação de planos inventados.

Todos os materiais e instruções estão no [kit comercial](../product/comercial/README.md).

## Comportamento do frontend

O login continua em `/` e os links públicos em `/acompanhar`. As novas páginas públicas
não consultam dados nem endpoints autenticados. Nenhum formulário de leads ou checkout
foi criado. O vídeo carrega sob demanda e tem imagem de capa, legendas e descrição textual.

Preparação inicial faz somente consultas paginadas aos recursos já existentes. OWNER
pode conferir equipe; ATENDENTE consulta cliente/veículo/OS; MECANICO mantém orientações
operacionais, sem esse fluxo. Consultas são canceladas logicamente ao fechar o guia e
o cliente de API existente continua protegendo troca/expiração de sessão. Não há armazenamento
de progresso ou de dados da oficina no navegador. “Registro encontrado” não significa
cadastro validado, operação completa ou aceite comercial.

O guia encaminha às telas existentes para efetuar cadastros com as validações originais.
O primeiro tenant/OWNER ainda depende de provisionamento pela equipe responsável.
Não foi inventado endpoint de configuração de empresa nem cadastro público automático.

## Materiais e privacidade

PPTX e gravação contêm apenas informações do produto e exemplos fictícios. O roteiro
de gravação intercepta as chamadas de API no próprio navegador de teste e não integra
o código de produção. A gravação não prova resultados financeiros nem integração real.
Não houve envio a prospects, publicação em domínio externo ou contratação de serviços.

## Validação local

Lint, typecheck e build aprovados. 23 testes unitários e 60 testes de navegador passaram
em Chromium nos tamanhos desktop, tablet e mobile (12 novos casos comerciais).
As páginas públicas foram verificadas sem chamadas à API, com downloads e reprodução
do vídeo. Preparação inicial testada com três perfis, erro/repetição e registros existentes.
Verificações axe e de overflow horizontal passaram. Capturas das três larguras,
os seis slides e oito cenas do vídeo foram inspecionados visualmente.

PPTX validado estruturalmente e reimportado pelo gerador. Não foi inspecionado dentro
do Microsoft PowerPoint. Testes usam API simulada e não substituem validação integrada,
Safari/iOS real, piloto com usuários ou operação do ambiente externo.

## Pendências para lançamento

- Definir contato comercial oficial, domínio e hospedagem autorizada.
- Aprovar preço, periodicidade, usuários, armazenamento, implantação e suporte.
- Concluir as validações reais de piloto da fase 6 e critérios de liberação.
- Formalizar termos e condições pelos responsáveis.

Essas decisões não foram substituídas por textos fictícios. A interface orienta contato
com quem apresentou o produto até existir um canal comercial oficial.
