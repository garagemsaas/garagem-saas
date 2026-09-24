> O tenant representa Empresa. Billing e cotas foram removidos do código. Identidade e imagens são exclusivas do desenvolvedor da plataforma; situação e operação são administrativas. Veja [modelo atualizado](/docs/architecture/empresa-white-label.md) e [provisionamento](/docs/operations/provisionamento-empresa.md). Referências a planos nas fases anteriores são históricas.

# Dívida técnica em aberto

Registro do que **permanece** por fazer. Os 17 achados da auditoria de qualidade de 18/09/2026 foram
corrigidos; o histórico daquela auditoria e das correções está nos commits e em
[../security/security-audit.md](../security/security-audit.md).

Este documento não repete o que já foi resolvido. Se um item some daqui, é porque acabou.

---

## Depende de decisão ou contratação externa

| # | Item | Por que está parado | Onde se aplica |
|---|---|---|---|
| D-03 | Dados societários dos documentos | Depende da empresa | Termos, privacidade e contrato em [../product/](../product/) usam marcadores explícitos |
| D-04 | Números do SLA não aprovados | Depende da empresa | [../product/sla.md](../product/sla.md) marca cada valor como sugestão |
| D-05 | Frontend sem serviço de produção | Decisão de infraestrutura | `compose.yml` não serve o frontend. Sem isso não há onde configurar CSP própria, HSTS, terminação TLS e compressão |
| D-06 | Monitoramento externo de disponibilidade | Não contratado | `/actuator/health` existe e é a sonda correta; falta quem a observe |

---

## Funcionalidade ainda não implementada

| # | Item | Impacto | Observação |
|---|---|---|---|
| F-01 | Exportação completa dos dados da oficina | Atendimento ao titular é operacional, conduzido pela equipe | Previsto em [../product/politica-cancelamento.md](../product/politica-cancelamento.md) |
| F-02 | Eliminação e anonimização por titular | Art. 18, VI da LGPD | Exige decidir o que é eliminável sem quebrar auditoria imutável |
| F-04 | Expurgo por prazo de retenção | Nada é apagado por decurso de prazo | Prazos ainda não definidos ([../product/politica-privacidade.md](../product/politica-privacidade.md) §5) |

---

## Limites técnicos conhecidos

**Teto de requisições é por instância.** A contagem vive em memória. Com mais de uma instância atrás
de um balanceador, cada uma conta separadamente e o teto efetivo se multiplica. Continua muito
melhor do que teto nenhum, mas escalar horizontalmente exige um contador compartilhado.

**Identificação de oportunidades é manual.** `POST /dinheiro-esquecido/identificar` precisa ser
acionado; não há varredura automática. A carteira reflete mudanças após a próxima identificação.

**Não há cobrança no produto.** Assinatura, planos, cotas e o webhook de gateway foram removidos:
a venda é direta e a mensalidade é negociada em contrato, fora do sistema. Reintroduzi-los é uma
decisão de produto, não uma dívida — e `FronteirasDeModuloTest` falha se o código voltar sozinho.

**IP registrado atrás de proxy.** `FORWARD_HEADERS_STRATEGY=none` é o padrão seguro e faz o IP
gravado na aprovação de orçamento ser o do proxy. Publicar atrás de proxy TLS exige ligar
`TRUST_PROXY=true` e `FORWARD_HEADERS_STRATEGY=framework` juntos — sem isso o IP da auditoria fica
incorreto, o teto de requisições trata todos os clientes como um só e o HSTS nunca é emitido.

**Sem Row Level Security no PostgreSQL.** Avaliado e deliberadamente não adotado: já existem três
camadas de isolamento e nenhuma falha foi encontrada. RLS transformaria um filtro esquecido em
conjunto vazio, mas exigiria variável de sessão por transação, interagindo com o pool de conexões e
com o Flyway. Reavaliar se o banco for aberto a algum consumidor fora desta aplicação.

---

## Convenções que valem manter

Quatro testes existem para impedir regressões que não quebram compilação e só apareceriam em
produção:

- `ConfiguracaoDeAmbienteTest` — toda variável documentada chega ao contêiner, e nenhum exemplo
  carrega credencial real. Existe porque onze variáveis, incluindo o segredo do webhook, ficaram
  documentadas sem nunca serem repassadas.
- `FronteirasDeModuloTest` — nenhum ciclo entre módulos e nenhum acesso ao interior de outro módulo.
- `ContratoDeModuloTest` — todo `@RestController` declara se exige módulo da empresa. Existe porque
  a exigência já morou numa regex de caminhos: ela não errava, apenas ficava para trás quando alguém
  criava rota nova sem lembrar dela, e nada acusava.
- `ConcorrenciaOperacionalIT` — criação de usuário, veículo, OS e foto sob concorrência real, sem
  fabricar por SQL o efeito que está sob teste.

Migrations são validadas contra banco **com dados**, não apenas contra banco vazio: uma delas já
derrubou a aplicação por criar restrição antes do preenchimento.
