# Protótipo da Fase 1 — registro de avaliação

> Registro histórico. A integração REST e a validação final posteriores estão em [fase1-finalizacao.md](fase1-finalizacao.md). As referências a mocks e integração pendente abaixo descrevem aquela etapa anterior.


Data: 15/09/2026. Escopo: interface local para aprovação, sem alteração de backend.

## Análise realizada

Lidos os três documentos em docs. Não foi encontrado AGENTS.md no repositório nem nos diretórios ancestrais consultados. Inspecionados AuthController/DTOs, ClienteController/DTOs, VeiculoController/DTOs, UsuarioController, OsController/DTOs, FotoController, PublicoController, OsService, FotoService, StatusOs, papéis e consultas de busca.

O frontend inicial era o scaffold Vite. Já estava não rastreado no Git, assim como package.json e package-lock.json da raiz. Esses arquivos da raiz não foram alterados.

## Verificações

- Build TypeScript/Vite e lint: passaram, sem avisos.
- Exercício em Chromium headless local: login, busca por placa/cliente, ausência de resultados, detalhe e versões.
- Aprovação integral, recusa, nova versão e transições até Pronto.
- Abertura de OS, checklist único, acréscimo de diagnóstico e cálculo exibido do orçamento.
- Adição de PNG local, galeria e ampliação.
- Cadastro/edição de cliente, cadastro de veículo e vínculo com cliente.
- Cadastro de usuário, revogação de link e visibilidade de ações para Proprietário, Atendente e Mecânico.
- Larguras de 1440, 1024, 768 e 390 px, com verificação de ausência de rolagem horizontal da página.
- Capturas de login, lista, resumo, orçamento, visão do cliente e layouts menores inspecionadas.

O roteiro técnico e as capturas ficam em .tools/ux-smoke.cjs e .tools/ux-captures (artefatos locais ignorados pelo Git). O roteiro usa o Playwright já instalado nesta máquina; não adiciona dependência ao frontend e não é uma suíte portável de produção.

## Limites desta validação

A interface não chamou o backend nem serviços externos. Não houve validação de segurança multi-tenant. O navegador integrado estava indisponível; as verificações usaram Chromium local sem janela. Não houve auditoria completa de acessibilidade, validação manual em dispositivo físico ou teste de rede/conflitos reais.

O protótipo não guarda senha, não produz token, não persiste cadastro nem envia foto. A emissão de link é uma simulação local; a visão pública abre no painel e não pode ser compartilhada.

Carregamento assíncrono, falhas HTTP e revisão concorrente permanecem trabalho da implementação definitiva. Na demonstração, formulários síncronos apresentam erros de validação, e a seleção de imagem indica processamento.

## Arquivos

Criados: src/model.ts, src/ui.tsx, src/forms.tsx, src/OrderDetail.tsx dentro de frontend; este documento.

Alterados: frontend/src/App.tsx, frontend/src/App.css, frontend/src/index.css, frontend/src/main.tsx (formatação), frontend/index.html, frontend/README.md e docs/proposta-interface.md.

Nenhuma mudança no backend. Nenhum commit, push, merge ou deploy.
