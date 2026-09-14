# Proposta de interface — aguardando validação

React + TypeScript + Vite, em português, com formulário de login por oficina, e-mail e senha.

## Navegação

Menu lateral: Ordens de Serviço, Clientes e Veículos. Dono também acessa cadastro de equipe. Cabeçalho identifica a oficina e o usuário. As ações seguem o papel recebido da sessão.

## Ordens de Serviço

Tela inicial com busca por placa, nome de cliente ou número de OS; tabela com número, identificação do veículo, cliente e status; botão “Abrir OS”. Formulário seleciona veículo e mecânico e registra km, relato e previsão de entrega.

Detalhe com número e status no cabeçalho e abas:

- Resumo: veículo, cliente, relato, responsável e mudança de status válida.
- Checklist: itens, condição, observações e fotos.
- Diagnóstico: itens identificados por texto e cor (verde/amarelo/vermelho), com fotos.
- Orçamento: versões numeradas, itens, totais, decisão e emissão de link. Criar versão abre novo formulário sem alterar a anterior.
- Timeline: eventos ordenados, autor/origem e horário.

## Cadastros

Listas pesquisáveis e formulários de cliente/veículo. Erros ficam junto ao formulário; botão de envio indica andamento e impede envio repetido. Conflito de revisão pede recarga dos dados.

## Página pública

Link abre resumo do veículo, OS e orçamento atual; apresenta itens e total antes de “Aprovar orçamento” e “Recusar orçamento”. Após a decisão, mostra confirmação com data. Link inválido/expirado apresenta mensagem clara. Nenhum dado interno da oficina ou de outros clientes.

Esta proposta ainda não é uma interface implementada. O próximo passo visual depende da validação humana prevista no escopo.
