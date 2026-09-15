# Garagem SaaS — protótipo da Fase 1

Interface local para avaliação de produto e UX, em React + TypeScript + Vite. Dados fictícios em memória, sem conexão à API, autenticação real ou persistência.

## Executar

No PowerShell, a partir da raiz:

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev -- --host 127.0.0.1
```

Abra a URL informada pelo Vite, normalmente **http://127.0.0.1:5173**. Se as dependências já estiverem instaladas, basta executar o comando dev. Nesta máquina, npm.cmd evita o bloqueio do npm.ps1 pela política do PowerShell.

```powershell
npm.cmd run build
npm.cmd run lint
```

## Roteiro de avaliação

1. Entre com os campos fictícios preenchidos. O seletor de papel é exclusivo da demonstração.
2. Busque **FKS2J48**, **Mariana** ou **1048**. Abra a OS #1048, com checklist, diagnóstico e duas versões do orçamento.
3. Em Orçamento, alterne versões, gere o link de demonstração e abra **Visualizar como cliente**. A confirmação simula aprovação integral ou recusa.
4. Abra uma nova OS para experimentar checklist, diagnóstico, fotos e status. Avance de Recebido para Diagnóstico e depois Orçamento antes de criar uma versão.
5. Adicione um PNG/JPEG fictício em Fotos para avaliar a galeria e a ampliação.
6. Cadastre e visualize clientes e veículos. Placa válida e vínculo com cliente são obrigatórios.
7. Saia e entre como Atendente ou Mecânico para comparar as ações. Equipe aparece apenas para Proprietário.

As alterações são descartadas ao sair ou recarregar. Não há URL pública compartilhável: a visão do cliente é uma prévia em painel. Fotos ficam apenas na memória da sessão.

## Organização

| Arquivo | Responsabilidade |
|---|---|
| src/App.tsx | Login de demonstração, navegação, listas, cadastros e estado em memória |
| src/OrderDetail.tsx | Abas da OS, formulários operacionais, versões e prévia do cliente |
| src/forms.tsx | Formulários de cliente, veículo, usuário e abertura de OS |
| src/ui.tsx | Campos, painel modal, indicadores, busca, paginação e ícones |
| src/model.ts | Tipos do protótipo, dados fictícios e transições |
| src/App.css e src/index.css | Identidade visual e adaptação de layout |

Os tipos agrupam sub-recursos para prototipação; não substituem os DTOs nem um cliente de API. Assets antigos do scaffold Vite foram preservados e estão sem uso.

Veja a [proposta de interface](../docs/proposta-interface.md) e o [registro de validação](../docs/prototipo-fase-1.md).
