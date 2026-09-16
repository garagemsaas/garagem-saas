-- A programação pertence à OS que originou a necessidade, não a um cadastro duplicado.
alter table ordem_servico add column proxima_revisao_em date;

create table acompanhamento_orcamento (
 id uuid primary key, oficina_id uuid not null references oficina(id),
 criado_em timestamptz not null default now(), unique(id,oficina_id),
 orcamento_versao_id uuid not null, publicado_em timestamptz, reavaliar_em date,
 revisao bigint not null default 0,
 unique(oficina_id,orcamento_versao_id),
 foreign key(orcamento_versao_id,oficina_id) references orcamento_versao(id,oficina_id)
);

-- Recupera a PRIMEIRA disponibilização comprovada pela timeline, dentro da vida da versão.
-- Não usa criação do orçamento nem emissão de link como substituto para publicação.
insert into acompanhamento_orcamento(id,oficina_id,orcamento_versao_id,publicado_em)
select gen_random_uuid(), v.oficina_id, v.id, min(e.criado_em)
from orcamento_versao v
join orcamento b on b.id=v.orcamento_id and b.oficina_id=v.oficina_id
join evento_ordem_servico e on e.ordem_servico_id=b.ordem_servico_id and e.oficina_id=v.oficina_id
where e.tipo='STATUS_ALTERADO' and e.descricao='ORCAMENTO → AGUARDANDO_APROVACAO'
  and e.criado_em>=v.criado_em
  and not exists(select 1 from orcamento_versao n where n.oficina_id=v.oficina_id
    and n.orcamento_id=v.orcamento_id and n.numero>v.numero and n.criado_em<=e.criado_em)
group by v.oficina_id,v.id;

create table oportunidade_recuperacao (
 id uuid primary key, oficina_id uuid not null references oficina(id),
 criado_em timestamptz not null default now(), unique(id,oficina_id),
 revisao bigint not null default 0,
 tipo varchar(30) not null check(tipo in ('ORCAMENTO_ESQUECIDO','REVISAO_ATRASADA','REAVALIACAO_PENDENTE')),
 status varchar(20) not null check(status in ('ABERTA','EM_CONTATO','AGENDADA','RECUPERADA','PERDIDA','DESCARTADA')),
 ordem_servico_id uuid not null, orcamento_versao_id uuid,
 cliente_id uuid not null, veiculo_id uuid not null, responsavel_id uuid,
 valor_potencial numeric(19,2) check(valor_potencial>=0), elegivel_desde timestamptz not null,
 ultimo_contato_em timestamptz, proximo_contato_em timestamptz, encerrada_em timestamptz,
 check((tipo='REVISAO_ATRASADA' and orcamento_versao_id is null and valor_potencial is null)
    or (tipo<>'REVISAO_ATRASADA' and orcamento_versao_id is not null and valor_potencial is not null)),
 check((status in ('RECUPERADA','PERDIDA','DESCARTADA')) = (encerrada_em is not null)),
 foreign key(ordem_servico_id,oficina_id) references ordem_servico(id,oficina_id),
 foreign key(orcamento_versao_id,oficina_id) references orcamento_versao(id,oficina_id),
 foreign key(cliente_id,oficina_id) references cliente(id,oficina_id),
 foreign key(veiculo_id,oficina_id) references veiculo(id,oficina_id),
 foreign key(responsavel_id,oficina_id) references usuario(id,oficina_id)
);
-- Unicidade também após encerrar: uma origem resolvida não reaparece na próxima varredura.
create unique index uq_oportunidade_origem on oportunidade_recuperacao
 (oficina_id,tipo,coalesce(orcamento_versao_id,ordem_servico_id));
create unique index uq_oportunidade_ativa on oportunidade_recuperacao(oficina_id,tipo,ordem_servico_id)
 where status in ('ABERTA','EM_CONTATO','AGENDADA');
create index ix_oportunidade_status on oportunidade_recuperacao(oficina_id,status);
create index ix_oportunidade_tipo on oportunidade_recuperacao(oficina_id,tipo);
create index ix_oportunidade_elegivel on oportunidade_recuperacao(oficina_id,elegivel_desde);
create index ix_oportunidade_contato on oportunidade_recuperacao(oficina_id,proximo_contato_em);
create index ix_oportunidade_responsavel on oportunidade_recuperacao(oficina_id,responsavel_id);

create table contato_oportunidade (
 id uuid primary key, oficina_id uuid not null references oficina(id),
 criado_em timestamptz not null default now(), unique(id,oficina_id),
 oportunidade_id uuid not null, usuario_id uuid not null,
 canal varchar(20) not null check(canal in ('TELEFONE','WHATSAPP','EMAIL','PRESENCIAL','OUTRO')),
 resultado varchar(25) not null check(resultado in ('SEM_RESPOSTA','CONTATO_REALIZADO','INTERESSADO','NAO_INTERESSADO','AGENDADO')),
 observacao varchar(4000), proximo_contato_em timestamptz,
 foreign key(oportunidade_id,oficina_id) references oportunidade_recuperacao(id,oficina_id),
 foreign key(usuario_id,oficina_id) references usuario(id,oficina_id)
);
create index ix_contato_oportunidade on contato_oportunidade(oficina_id,oportunidade_id,criado_em,id);

create table resultado_oportunidade (
 id uuid primary key, oficina_id uuid not null references oficina(id),
 criado_em timestamptz not null default now(), unique(id,oficina_id),
 oportunidade_id uuid not null, usuario_id uuid not null, ordem_servico_id uuid,
 valor_recuperado numeric(19,2) not null check(valor_recuperado>=0), observacao varchar(4000),
 unique(oficina_id,oportunidade_id),
 foreign key(oportunidade_id,oficina_id) references oportunidade_recuperacao(id,oficina_id),
 foreign key(usuario_id,oficina_id) references usuario(id,oficina_id),
 foreign key(ordem_servico_id,oficina_id) references ordem_servico(id,oficina_id)
);
create index ix_resultado_periodo on resultado_oportunidade(oficina_id,criado_em);

create table evento_oportunidade (
 id uuid primary key, oficina_id uuid not null references oficina(id),
 criado_em timestamptz not null default now(), unique(id,oficina_id),
 oportunidade_id uuid not null, usuario_id uuid not null, tipo varchar(50) not null,
 anterior varchar(4000), novo varchar(4000), observacao varchar(4000),
 foreign key(oportunidade_id,oficina_id) references oportunidade_recuperacao(id,oficina_id),
 foreign key(usuario_id,oficina_id) references usuario(id,oficina_id)
);
create index ix_evento_oportunidade on evento_oportunidade(oficina_id,oportunidade_id,criado_em,id);
create trigger historico_imutavel before update or delete on contato_oportunidade for each row execute function impedir_mutacao_historico();
create trigger historico_imutavel before update or delete on resultado_oportunidade for each row execute function impedir_mutacao_historico();
create trigger historico_imutavel before update or delete on evento_oportunidade for each row execute function impedir_mutacao_historico();
create trigger oportunidade_sem_exclusao before delete on oportunidade_recuperacao for each row execute function impedir_mutacao_historico();

create function validar_origem_recuperacao() returns trigger language plpgsql as $$
begin
 if not exists(select 1 from ordem_servico os where os.id=new.ordem_servico_id
    and os.oficina_id=new.oficina_id and os.cliente_id=new.cliente_id and os.veiculo_id=new.veiculo_id)
 or (new.orcamento_versao_id is not null and not exists(
    select 1 from orcamento_versao v join orcamento b on b.id=v.orcamento_id and b.oficina_id=v.oficina_id
    where v.id=new.orcamento_versao_id and v.oficina_id=new.oficina_id and b.ordem_servico_id=new.ordem_servico_id)) then
   raise exception 'Origem inconsistente' using errcode='23000';
 end if;
 if tg_op='UPDATE' and (new.oficina_id,new.tipo,new.ordem_servico_id,new.orcamento_versao_id,
      new.cliente_id,new.veiculo_id,new.valor_potencial,new.elegivel_desde,new.criado_em)
   is distinct from (old.oficina_id,old.tipo,old.ordem_servico_id,old.orcamento_versao_id,
      old.cliente_id,old.veiculo_id,old.valor_potencial,old.elegivel_desde,old.criado_em) then
   raise exception 'Origem imutavel' using errcode='23000';
 end if;
 return new;
end;
$$;
create trigger conferir_origem before insert or update on oportunidade_recuperacao
 for each row execute function validar_origem_recuperacao();

create function validar_resultado_recuperacao() returns trigger language plpgsql as $$
declare op uuid; tenant uuid; recuperada boolean; tem_resultado boolean;
begin
 if tg_table_name='resultado_oportunidade' then op=new.oportunidade_id; else op=new.id; end if;
 tenant=new.oficina_id;
 select status='RECUPERADA' into recuperada from oportunidade_recuperacao where id=op and oficina_id=tenant;
 select exists(select 1 from resultado_oportunidade where oportunidade_id=op and oficina_id=tenant) into tem_resultado;
 if recuperada is distinct from tem_resultado then
   raise exception 'Resultado inconsistente' using errcode='23000';
 end if;
 return new;
end;
$$;
create constraint trigger conferir_recuperacao after insert or update on oportunidade_recuperacao
 deferrable initially deferred for each row execute function validar_resultado_recuperacao();
create constraint trigger conferir_resultado after insert on resultado_oportunidade
 deferrable initially deferred for each row execute function validar_resultado_recuperacao();

create function preservar_publicacao_orcamento() returns trigger language plpgsql as $$
begin
 if new.oficina_id<>old.oficina_id or new.orcamento_versao_id<>old.orcamento_versao_id
    or new.publicado_em is distinct from old.publicado_em then
   raise exception 'Publicacao imutavel' using errcode='23000';
 end if;
 return new;
end;
$$;
create trigger publicacao_preservada before update on acompanhamento_orcamento
 for each row execute function preservar_publicacao_orcamento();
create trigger publicacao_sem_exclusao before delete on acompanhamento_orcamento
 for each row execute function impedir_mutacao_historico();

-- Visão sem relógio e sem dados pessoais; TODA consulta deve aplicar oficina_id.
create view origem_recuperacao as
select os.oficina_id, os.id ordem_servico_id, v.id orcamento_versao_id, os.cliente_id, os.veiculo_id,
 'ORCAMENTO_ESQUECIDO'::varchar tipo, v.total valor_potencial,
 a.publicado_em + interval '168 hours' elegivel_desde
from ordem_servico os
join orcamento b on b.ordem_servico_id=os.id and b.oficina_id=os.oficina_id
join orcamento_versao v on v.orcamento_id=b.id and v.oficina_id=b.oficina_id
join acompanhamento_orcamento a on a.orcamento_versao_id=v.id and a.oficina_id=v.oficina_id
where os.status='AGUARDANDO_APROVACAO' and a.publicado_em is not null
 and not exists(select 1 from aprovacao_orcamento d where d.oficina_id=v.oficina_id and d.orcamento_versao_id=v.id)
 and not exists(select 1 from orcamento_versao n where n.oficina_id=v.oficina_id and n.orcamento_id=b.id and n.numero>v.numero)
union all
select os.oficina_id, os.id, v.id, os.cliente_id, os.veiculo_id,
 'REAVALIACAO_PENDENTE', v.total,
 coalesce(a.reavaliar_em::timestamp at time zone 'America/Sao_Paulo', d.criado_em + interval '720 hours')
from ordem_servico os
join orcamento b on b.ordem_servico_id=os.id and b.oficina_id=os.oficina_id
join orcamento_versao v on v.orcamento_id=b.id and v.oficina_id=b.oficina_id
join aprovacao_orcamento d on d.orcamento_versao_id=v.id and d.oficina_id=v.oficina_id and not d.aprovado
left join acompanhamento_orcamento a on a.orcamento_versao_id=v.id and a.oficina_id=v.oficina_id
where os.status in ('ORCAMENTO','AGUARDANDO_APROVACAO')
 and not exists(select 1 from orcamento_versao n
   join aprovacao_orcamento nd on nd.orcamento_versao_id=n.id and nd.oficina_id=n.oficina_id
   where n.oficina_id=v.oficina_id and n.orcamento_id=b.id and n.numero>v.numero)
union all
select os.oficina_id, os.id, null::uuid, os.cliente_id, os.veiculo_id,
 'REVISAO_ATRASADA', null::numeric, os.proxima_revisao_em::timestamp at time zone 'America/Sao_Paulo'
from ordem_servico os
where os.status='PRONTO' and os.proxima_revisao_em is not null
 and not exists(select 1 from ordem_servico n where n.oficina_id=os.oficina_id and n.veiculo_id=os.veiculo_id
   and n.id<>os.id and n.status='PRONTO' and n.criado_em>os.concluida_em);
