-- Catálogo de planos da plataforma. Não é multi-tenant: é o mesmo para todas as oficinas.
-- Limites vivem aqui, em uma linha por plano, e não espalhados em constantes pelo código.
create table plano (
 id uuid primary key, criado_em timestamptz not null default now(),
 codigo varchar(40) not null unique check(codigo ~ '^[A-Z][A-Z0-9_]{2,39}$'),
 nome varchar(80) not null, descricao varchar(500) not null,
 valor_centavos bigint not null check(valor_centavos >= 0),
 -- varchar, não char: bpchar quebraria a validação de schema do Hibernate contra o campo String.
 moeda varchar(3) not null default 'BRL' check(moeda ~ '^[A-Z]{3}$'),
 periodicidade varchar(20) not null check(periodicidade in ('MENSAL','ANUAL')),
 max_usuarios integer not null check(max_usuarios > 0),
 max_armazenamento_bytes bigint not null check(max_armazenamento_bytes > 0),
 max_ordens_servico_mes integer check(max_ordens_servico_mes > 0),
 max_veiculos integer check(max_veiculos > 0),
 ativo boolean not null default true,
 ordem integer not null default 0,
 provider_price_id varchar(120),
 revisao bigint not null default 0,
 unique(codigo,id)
);
create index ix_plano_vitrine on plano(ativo,ordem,codigo);

-- Preços e limites são placeholders operacionais até a definição comercial (docs/fase7-caua.md).
-- Alterar valor/limite é um UPDATE nesta tabela ou uma migration nova; nunca uma mudança de código.
insert into plano(id,codigo,nome,descricao,valor_centavos,periodicidade,max_usuarios,max_armazenamento_bytes,max_ordens_servico_mes,max_veiculos,ordem) values
 (gen_random_uuid(),'BASICO','Plano Básico','Oficina que está começando a organizar a operação.',0,'MENSAL',3,1073741824,60,200,1),
 (gen_random_uuid(),'PROFISSIONAL','Plano Profissional','Oficina com equipe e volume constante de ordens de serviço.',0,'MENSAL',10,10737418240,400,2000,2),
 (gen_random_uuid(),'PREMIUM','Plano Premium','Operação com várias frentes e histórico extenso de fotos.',0,'MENSAL',30,53687091200,null,null,3);

-- A assinatura pertence à oficina, nunca ao usuário. Uma por oficina.
create table assinatura (
 id uuid primary key, oficina_id uuid not null references oficina(id), criado_em timestamptz not null default now(),
 unique(id,oficina_id), unique(oficina_id),
 revisao bigint not null default 0,
 plano_id uuid not null references plano(id),
 status varchar(20) not null check(status in ('TRIAL','ATIVA','INADIMPLENTE','SUSPENSA','CANCELADA')),
 provedor varchar(40) not null default 'MANUAL',
 provider_customer_id varchar(120), provider_subscription_id varchar(120),
 periodo_inicio timestamptz not null, periodo_fim timestamptz not null,
 trial_inicio timestamptz, trial_fim timestamptz,
 inadimplente_desde timestamptz, suspensa_em timestamptz,
 cancelada_em timestamptz, cancelamento_efetivo_em timestamptz,
 cancelamento_motivo varchar(500), cancelada_por uuid,
 atualizado_em timestamptz not null default now(),
 check(periodo_fim > periodo_inicio),
 check((trial_inicio is null) = (trial_fim is null)),
 check(trial_fim is null or trial_fim > trial_inicio),
 -- Cancelada exige carimbo, mas não o contrário: carimbo com status vigente é exatamente o
 -- cancelamento agendado, em que a oficina segue operando até o fim do período já pago.
 check(status<>'CANCELADA' or cancelada_em is not null),
 check((cancelada_em is null) = (cancelamento_efetivo_em is null)),
 check((status='SUSPENSA') = (suspensa_em is not null)),
 -- Suspensão só acontece depois da inadimplência: não se pula a tolerância.
 check(suspensa_em is null or inadimplente_desde is not null),
 foreign key(cancelada_por,oficina_id) references usuario(id,oficina_id)
);
create index ix_assinatura_status on assinatura(status,periodo_fim);
create unique index uq_assinatura_provider_subscription on assinatura(provedor,provider_subscription_id)
 where provider_subscription_id is not null;

-- Toda oficina existente entra com assinatura de avaliação, sem inventar cobrança retroativa.
insert into assinatura(id,oficina_id,plano_id,status,periodo_inicio,periodo_fim,trial_inicio,trial_fim)
select gen_random_uuid(), o.id, p.id, 'TRIAL', now(), now()+interval '14 days', now(), now()+interval '14 days'
from oficina o cross join (select id from plano where codigo='PROFISSIONAL') p;

-- oficina.plano já existia sem uso; passa a espelhar o código do plano para leitura simples.
-- Continua sendo um espelho: a fonte de verdade é assinatura.plano_id.
update oficina set plano='PROFISSIONAL';

-- Oficina sem assinatura é estado inválido: não existiria limite a aplicar e toda criação falharia.
-- O invariante é do banco, não de um caminho de cadastro, para valer em bootstrap, migração,
-- importação e teste do mesmo jeito.
create function criar_assinatura_da_oficina() returns trigger language plpgsql as $$
declare padrao uuid;
begin
 select id into padrao from plano where codigo='PROFISSIONAL' and ativo;
 if padrao is null then
  select id into padrao from plano where ativo order by ordem, codigo limit 1;
 end if;
 insert into assinatura(id,oficina_id,plano_id,status,periodo_inicio,periodo_fim,trial_inicio,trial_fim)
 values(gen_random_uuid(),new.id,padrao,'TRIAL',now(),now()+interval '14 days',now(),now()+interval '14 days');
 return new;
end;
$$;
create trigger assinatura_da_oficina after insert on oficina
 for each row execute function criar_assinatura_da_oficina();

create function espelhar_plano_na_oficina() returns trigger language plpgsql as $$
begin
 update oficina set plano=(select codigo from plano where id=new.plano_id) where id=new.oficina_id;
 return new;
end;
$$;
create trigger plano_espelhado after insert or update of plano_id on assinatura
 for each row execute function espelhar_plano_na_oficina();

-- Auditoria financeira. Imutável: um evento de cobrança registrado não se reescreve.
create table evento_cobranca (
 id uuid primary key, oficina_id uuid not null references oficina(id), criado_em timestamptz not null default now(),
 unique(id,oficina_id),
 assinatura_id uuid, usuario_id uuid, tipo varchar(40) not null,
 provedor varchar(40), provider_event_id varchar(120),
 mensagem varchar(1000) not null, metadados varchar(4000),
 foreign key(assinatura_id,oficina_id) references assinatura(id,oficina_id),
 foreign key(usuario_id,oficina_id) references usuario(id,oficina_id)
);
create index ix_evento_cobranca_oficina on evento_cobranca(oficina_id,criado_em desc,id);
create index ix_evento_cobranca_tipo on evento_cobranca(oficina_id,tipo,criado_em desc);
create trigger historico_imutavel before update or delete on evento_cobranca
 for each row execute function impedir_mutacao_historico();

-- Idempotência do webhook. Não é multi-tenant: o evento chega antes de sabermos a oficina.
create table webhook_pagamento (
 id uuid primary key, criado_em timestamptz not null default now(),
 provedor varchar(40) not null, provider_event_id varchar(120) not null,
 tipo varchar(80) not null, payload text not null,
 assinatura_id uuid references assinatura(id), oficina_id uuid references oficina(id),
 processado boolean not null default false, processado_em timestamptz, erro varchar(1000),
 unique(provedor,provider_event_id)
);
create index ix_webhook_pendente on webhook_pagamento(processado,criado_em);
