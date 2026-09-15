# Fase 1 — Alinhamento do produto e dos contratos

Documento de trabalho para Kauã e Cauã. Esta fase prepara a integração sem alterar contratos, regras ou migrations.

## Decisões conjuntas

- [ ] Confirmar o escopo da primeira versão comercial.
- [ ] Escolher duas ou três oficinas para o piloto.
- [ ] Definir o prazo do piloto.
- [ ] Definir quem será o usuário responsável em cada oficina.
- [ ] Definir o que será considerado sucesso no piloto.
- [ ] Confirmar que a primeira versão não inclui agenda, pátio, peças, cobrança ou automações de WhatsApp.

### Escopo recomendado para a primeira venda

- [ ] Login e sessão reais.
- [ ] Clientes e veículos.
- [ ] Ordens de serviço.
- [ ] Checklist de entrada.
- [ ] Diagnóstico.
- [ ] Orçamento versionado.
- [ ] Aprovação pública.
- [ ] Timeline e fotos.
- [ ] Dashboard operacional básico.

## Inventário atual da API

### Autenticação

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`

### Cadastros

- `GET /api/v1/clientes`
- `GET /api/v1/clientes/{id}`
- `POST /api/v1/clientes`
- `PUT /api/v1/clientes/{id}`
- `GET /api/v1/veiculos`
- `GET /api/v1/veiculos/{id}`
- `POST /api/v1/veiculos`
- `PUT /api/v1/veiculos/{id}`
- `GET /api/v1/usuarios`
- `POST /api/v1/usuarios`

### Ordens de serviço

- `GET /api/v1/ordens-servico`
- `GET /api/v1/ordens-servico/{id}`
- `POST /api/v1/ordens-servico`
- `PUT /api/v1/ordens-servico/{id}/responsavel`
- `POST /api/v1/ordens-servico/{id}/status`
- `POST /api/v1/ordens-servico/{id}/checklist`
- `GET /api/v1/ordens-servico/{id}/checklist`
- `POST /api/v1/ordens-servico/{id}/diagnosticos`
- `GET /api/v1/ordens-servico/{id}/diagnosticos`
- `POST /api/v1/ordens-servico/{id}/orcamento/versoes`
- `GET /api/v1/ordens-servico/{id}/orcamento/versoes`
- `GET /api/v1/ordens-servico/{id}/timeline`

### Fotos e acompanhamento público

- `POST /api/v1/ordens-servico/{osId}/fotos`
- `GET /api/v1/ordens-servico/{osId}/fotos`
- `GET /api/v1/ordens-servico/{osId}/fotos/{fotoId}/conteudo`
- `POST /api/v1/ordens-servico/{id}/links`
- `DELETE /api/v1/ordens-servico/{id}/links/{linkId}`
- `GET /api/v1/publico/{token}`
- `POST /api/v1/publico/{token}/decisao`

## Contratos que Cauã deve publicar primeiro

- [ ] Exemplo completo de login, refresh e logout.
- [ ] Formato da sessão e duração dos tokens.
- [ ] Formato de paginação das listagens.
- [ ] Filtros e ordenação suportados.
- [ ] Campos obrigatórios e limites de cada formulário.
- [ ] Códigos de erro e formato de validação.
- [ ] Formato de conflito de revisão HTTP 409.
- [ ] Exemplo de cliente, veículo e OS.
- [ ] Exemplo de mudança de status.
- [ ] Exemplo de orçamento com versões e itens.
- [ ] Regra para expiração e revogação de link público.

## Implementação que Kauã começa em paralelo

- [ ] Criar camada HTTP com base URL configurável.
- [ ] Criar tipos TypeScript equivalentes aos DTOs publicados.
- [ ] Criar armazenamento de sessão sem colocar refresh token em localStorage.
- [ ] Criar guardas para páginas autenticadas.
- [ ] Criar estados padrão de carregamento, erro e vazio.
- [ ] Mapear mensagens de erro da API para português claro.
- [ ] Preparar a tela de login para o contrato real.
- [ ] Preparar clientes, veículos e OS para receber dados remotos.
- [ ] Manter a oficina derivada da sessão, sem campo editável para trocar de tenant.

## Critérios de encerramento da Fase 1

- [ ] Escopo da primeira versão aprovado pelos dois.
- [ ] Oficinas do piloto escolhidas.
- [ ] Contratos dos fluxos de login, cadastros e OS documentados.
- [ ] Exemplos JSON disponíveis para o frontend.
- [ ] Erros, paginação, filtros e revisão concorrente definidos.
- [ ] Branch de cada pessoa criada.
- [ ] Nenhuma migration existente alterada.
- [ ] Nenhuma regra de isolamento entre oficinas alterada.
- [ ] Próxima tarefa definida para cada pessoa.

## Próximos passos após o alinhamento

1. Cauã publica os contratos básicos.
2. Kauã integra login, clientes, veículos e OS.
3. Cauã corrige os problemas encontrados e adiciona testes de integração.
4. Os dois validam permissões e isolamento entre oficinas.
5. A equipe avança para checklist, diagnóstico, orçamento e acompanhamento público.
