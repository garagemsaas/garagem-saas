# Redesign do frontend — etapa 1

> Registro histórico. A integração REST e a validação final posteriores estão em [fase1-finalizacao.md](fase1-finalizacao.md). As referências a mocks e integração pendente abaixo descrevem aquela etapa anterior.


Implementado em 15/09/2026, após aprovação da direção visual.

## Entrega

- Estrutura autenticada demonstrativa com navegação lateral clara, contexto fixo da Oficina Modelo e menu modal para telas menores que 1024 px.
- Busca global local por OS, cliente e veículo; notificações apresentam somente pendências calculadas, sem simular entrega de avisos remotos.
- Dashboard com andamento, aprovação, serviços prontos, prioridades, entradas registradas e acesso às OS.
- Dinheiro Esquecido diferencia potencial ainda não apurado de orçamentos pendentes. Soma somente a versão mais recente, sem decisão, das OS aguardando aprovação.
- Agenda, pátio e catálogo de peças indicam indisponibilidade e oferecem caminhos para recursos existentes.
- Paleta marfim/branco/verde, tokens compartilhados, Inter servida localmente e ícones exclusivamente lucide-react nos componentes.
- Listas existentes adaptadas para celular com rótulos de campos; diagnósticos com ícone e texto.
- Estados de carregamento do dashboard, erro de renderização, vazio e avisos de sucesso; diálogos nomeados, Escape e retorno de foco.
- TypeScript em modo estrito, sem introdução de `any`.

## Limites desta etapa

O frontend continua sendo uma demonstração em memória. Nenhum endpoint, regra Java, autenticação de produção ou migration foi alterado. A oficina demonstrativa é fixa para evitar reapresentar os mesmos dados sob nomes de oficinas diferentes. Alterações são descartadas ao sair/recarregar.

Entradas de hoje são recebimentos efetivos nas OS fictícias, não agendamentos futuros. OS em andamento incluem todas as etapas exceto Pronto; Aguardando aprovação é um subconjunto. OS prontas não permitem inferir ocupação física nem entrega ao cliente. O pátio não exibe uma ocupação calculada a partir de OS.

Recuperação, contatos, revisões futuras e vagas necessitam de fontes e contratos que não existem no backend recebido. O acompanhamento público continua na implementação demonstrativa anterior até sua etapa de integração. O redesenho completo e integração REST ainda não estão concluídos.

## Verificação

Na pasta `frontend`:

```sh
npm run lint
npm run typecheck
npm test
npm run build
npm run dev -- --host 127.0.0.1
```

O teste do dashboard usa o executor nativo do Node com suporte a TypeScript (Node 22.18+ ou 24+).

Foram exercitados em navegador desktop (1440 px), tablet (834 px), celular (390 px) e celular estreito (320 px): login demonstrativo, dashboard, busca global, abertura de OS, lista, retorno à visão geral, consulta da origem dos valores e abertura do formulário de OS. Menu mobile testado com Escape e retorno de foco. Não foram encontrados erros JavaScript nesses fluxos.

Seis testes automatizados cobrem versões de orçamento, decisões, contagem de OS concluídas, prioridades, datas locais e base vazia. Testes Java não executados nesta etapa, que não altera o backend.

## Continuidade

1. Integrar login/sessão e dados operacionais aos contratos REST existentes, com contexto derivado da sessão autenticada e limpeza de dados ao trocar/sair da oficina.
2. Evoluir lista/detalhe de OS, filtros e ações, preservando transições e orçamento imutável no servidor.
3. Integrar acompanhamento público por token e decisão de orçamento.
4. Definir contratos dos módulos adicionais antes de oferecer gravação de dados reais.
