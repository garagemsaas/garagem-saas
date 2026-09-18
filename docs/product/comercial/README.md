# Kit comercial — Garagem SaaS

## Materiais prontos

- Página institucional: `/institucional` no mesmo servidor do frontend.
- Ajuda pública: `/ajuda`, sem login ou consulta de dados de oficinas.
- [Apresentação editável, 6 slides](../../frontend/public/materiais/garagem-apresentacao.pptx).
- [Vídeo WebM sem áudio, legendado, com dados fictícios](../../frontend/public/materiais/garagem-demonstracao.webm).
- [Legendas em português](../../frontend/public/materiais/garagem-demonstracao.vtt).
- [Roteiro de venda e objeções](roteiro-de-venda.md).
- [Onboarding assistido da oficina](onboarding-da-oficina.md).
- [Textos da oferta e decisões pendentes](textos-dos-planos.md).

No sistema autenticado, **Primeiros passos** inclui a consulta de preparação inicial
e atalhos para os cadastros existentes. O login permanece em `/` e o acompanhamento
do cliente permanece em `/acompanhar`, sem alteração dos links existentes.

## Uso do vídeo

O vídeo usa a interface real com respostas de API simuladas somente no processo de
gravação. Nenhum exemplo entra no código de produção ou no banco. Os valores, placa,
cliente e oficina são fictícios. É uma demonstração comercial, não evidência de receita,
cliente atendido ou teste de integração. Não há locução nem música.

Sequência: painel, preparação inicial, clientes, carteira de oportunidades, origem,
registro de contato e limites da proposta. O vídeo não apresenta um atendimento completo
nem faz envio real de mensagem. Descrição textual também disponível na página institucional.

Para gravar novamente, com dependências de desenvolvimento instaladas e Chromium do
Playwright disponível: iniciar Vite em `127.0.0.1:5181`, entrar na pasta `frontend` e
executar `node scripts/record-commercial-demo.mjs`. O script escreve o vídeo e as legendas
em `public/materiais` apenas ao concluir o roteiro. Revisar a gravação antes de publicar.
Para ajustar a apresentação, editar o PPTX em software compatível e revisar todas as telas.

## Publicação e limites

Esta entrega versiona os materiais e prepara o frontend. Não publica domínio ou servidor
externo e não ativa checkout, CRM, captura de leads, cobrança ou criação pública de oficinas.
O servidor de hospedagem deve servir os arquivos de `dist`, fazer fallback para `index.html`
em `/institucional`, `/ajuda` e `/acompanhar`, e manter `/api` separado do fallback.
Os arquivos estáticos em `/materiais` devem ser servidos com tipos MIME corretos
(`video/webm`, `text/vtt` e o tipo PPTX). O vídeo usa carregamento sob demanda.

Sem contato comercial oficial informado, a chamada orienta procurar quem apresentou
o produto. Substituir por um canal confirmado antes de iniciar campanhas abertas.
Não inventar telefone, e-mail, depoimentos, quantidade de clientes ou garantias de retorno.

## Antes de vender

- [ ] Concluir os critérios do piloto da fase 6 e corrigir bloqueadores reais.
- [ ] Aprovar preço, limites, implantação, suporte e condições de uso.
- [ ] Definir contato comercial oficial e domínio público.
- [ ] Publicar e testar as rotas e os arquivos no ambiente externo autorizado.
- [ ] Revisar os materiais depois de qualquer mudança de escopo.

O nome da branch continua `feature/fase6-observabilidade-piloto` por solicitação dos sócios.
