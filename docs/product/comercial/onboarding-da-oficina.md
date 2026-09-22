> Atualização Fase 9: o tenant representa Empresa. Billing e cotas históricas estão desativados e fora do core. OWNER edita somente a própria identidade; situação e módulos são administrativos. Veja [modelo atualizado](/docs/architecture/empresa-white-label.md) e [provisionamento](/docs/operations/provisionamento-empresa.md). Referências a planos nas fases anteriores são históricas.

# Onboarding da oficina

## Antes de liberar acesso — equipe Plataforma Automotiva

- [ ] Confirmar escopo e condições aceitos por escrito.
- [ ] Confirmar identificador da oficina e responsável autorizado.
- [ ] Provisionar oficina e primeiro OWNER pelo processo administrativo existente.
- [ ] Verificar isolamento, acesso, backup e restauração no ambiente aprovado.
- [ ] Entregar credenciais pelo procedimento seguro acordado. Não colocar senhas em documentos compartilhados.
- [ ] Definir contato de suporte, horários e limites na proposta; não presumir atendimento 24 horas.
- [ ] Combinar o primeiro acompanhamento, sem prometer prazo não aprovado.

O frontend não possui cadastro público de oficina, checkout ou edição dos dados gerais
da empresa. Não usar a tela de usuários para simular provisionamento de outro tenant.

## Primeiro acesso — com o responsável da oficina

1. Entrar com o identificador, e-mail e senha da oficina correta.
2. Conferir o nome da oficina e o perfil exibidos na navegação.
3. Abrir **Primeiros passos**. A seção **Preparação inicial da oficina** consulta a API
   e mostra se já existem clientes, veículos, OS e usuários adicionais ativos.
4. O OWNER pode abrir **Equipe**, cadastrar usuários necessários e definir seus perfis.
   Oficina com uma pessoa não precisa criar outro usuário. Não compartilhar o mesmo login.
5. Usar o atalho para Clientes, conferir a busca e cadastrar um contato real autorizado.
6. Abrir Veículos e vincular o veículo ao proprietário correto.
7. Abrir a OS e registrar o relato do atendimento. Retornar ao guia para consultar a situação atual.

“Registro encontrado” significa existência na API, não qualidade de cadastro, configuração
completa ou aceite comercial. Não há progresso fictício salvo no navegador.
ATENDENTE não recebe configuração de equipe. MECANICO recebe orientação operacional,
sem o fluxo de cadastro e contratação. A API continua sendo a autoridade de permissão.

## Primeiro atendimento acompanhado

- [ ] Conferir checklist e fotos sem dados pessoais desnecessários.
- [ ] Registrar diagnóstico com classificação correta.
- [ ] Criar e revisar a versão do orçamento.
- [ ] Disponibilizar, gerar link, conferir destinatário e enviar manualmente.
- [ ] Confirmar que o cliente consegue consultar o link em celular.
- [ ] Explicar que versões e decisões têm histórico e não devem ser apagadas para esconder alterações.
- [ ] Mostrar Dinheiro Esquecido e diferenciar valor potencial, valor recuperado e pagamento.
- [ ] Mostrar "Primeiros passos", dentro da conta, e como relatar erro sem senha, token ou link de cliente.

## Encerramento do onboarding

```text
Oficina (identificador interno): __
Responsável: __  Data: __  Versão do sistema: __
Primeira OS validada: sim / não
Link conferido no celular: sim / não
Perfis e limites compreendidos: sim / não
Pendências, responsável e próximo contato: __
Aceite do responsável da oficina: __
```

Registrar problemas pelo roteiro da fase 6. Bloqueio de operação ou mistura de dados
impede avançar para uso pago até correção e reteste. Guardar fichas preenchidas fora do Git.
