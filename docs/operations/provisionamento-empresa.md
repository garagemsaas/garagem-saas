# Provisionamento por venda direta

Fluxo: venda acordada → empresa e módulos → identidade inicial → OWNER → entrega do acesso. Uma única implantação atende as empresas; não copiar a aplicação.

O bootstrap existente é a única criação administrativa de empresa/OWNER. Executar uma vez com configuração protegida do operador:

```properties
app.bootstrap.enabled=true
app.bootstrap.slug=empresa-exemplo
app.bootstrap.nome=Empresa Exemplo
app.bootstrap.email=owner@example.com
app.bootstrap.senha=<segredo inicial de pelo menos 12 caracteres>
app.bootstrap.modulos=OFICINA,REVENDA
app.bootstrap.operador=<identificação do operador responsável>
```

`modulos` e `operador` são opcionais: sem eles a empresa nasce com `OFICINA` e a trilha registra
`bootstrap` como responsável. Isso é deliberado — exigir uma variável nova quebraria no start toda
instalação que já vinha definindo as anteriores. Informe o operador real sempre que houver um.

A empresa nasce com exatamente os módulos informados. Uma empresa só de revenda não é criada como
oficina para ser corrigida depois: `app.bootstrap.modulos=REVENDA` a cria já assim, e nenhum evento
da trilha registra o contrário. Módulo desconhecido impede a subida da aplicação.

## Criar empresa fora do bootstrap

Mesma garantia, para quando a implantação já existe e uma segunda empresa é vendida:

```sql
select provisionar_empresa(
 'revenda-exemplo', 'Revenda Exemplo', array['REVENDA'],
 'operador-responsavel', 'Venda direta'
);
```

Devolve o `id` da empresa criada, ou `null` se o slug já existir — nesse caso nada é alterado. O
OWNER continua sendo criado à parte, pela mesma credencial de banco. As mesmas validações e o mesmo
`revoke ... from public` de `administrar_empresa` valem aqui.

Quem insere direto em `oficina` sem declarar módulo algum ainda recebe `OFICINA` ao fim da
transação. Esse padrão é rede de segurança contra empresa sem módulo nenhum, não a forma pretendida
de provisionar.

As propriedades podem ser fornecidas por ambiente Spring (`APP_BOOTSTRAP_*`) ou arquivo de configuração restrito. Não registrar senha em histórico do shell, documentação ou Git. Na infraestrutura Compose, conferir o repasse de variáveis ao contêiner. Desabilitar bootstrap após provisionar. Repetir o mesmo slug não cria outra empresa nem sobrescreve OWNER, senha ou módulos. Empresa, módulos, auditoria e OWNER são criados na mesma transação; falha implica rollback.

O nome empresarial já fornece a identidade inicial; o OWNER pode ajustar nome de exibição, cores, contatos e imagens em Configurações → Identidade da empresa. Entregar o acesso por canal combinado e seguro. Não há contratação pública, seleção de plano ou checkout.

## Mudança administrativa

Sem SUPER_ADMIN HTTP. Um operador com credencial de banco autorizada chama a função transacional, usando parâmetros do cliente SQL:

```sql
select administrar_empresa(
 'empresa-exemplo', 'ATIVA', array['OFICINA','REVENDA'],
 'operador-responsavel', 'Módulos acordados no contrato'
);
```

Para bloquear: status `INATIVA`; para restabelecer: `ATIVA`. A função valida os valores, trava a empresa, substitui o conjunto de módulos e registra antes/depois, operador, motivo e data em `empresa_administracao_evento`. A tabela rejeita UPDATE/DELETE. O privilégio EXECUTE da função foi revogado de PUBLIC; utilizar o proprietário da migration ou conceder explicitamente a um papel operacional restrito. Não entregar credenciais de banco ao cliente. A função usa SECURITY INVOKER, sem escalada de privilégios.

A trilha protege contra alterações acidentais pela aplicação; um DBA proprietário continua tecnicamente capaz de modificar o schema. Como em qualquer operação administrativa direta, separar credenciais e restringir acesso à infraestrutura.

Backups devem incluir as novas tabelas e colunas: logos/favicons estão no PostgreSQL, enquanto as fotos operacionais continuam no storage privado existente. Testar restore antes de produção. Não editar V1–V5 nem reativar o gatilho de trial.
