-- Evolução aditiva: ano continua sendo o ano de fabricação para compatibilidade da API.
alter table veiculo alter column cliente_id drop not null;
alter table veiculo alter column placa drop not null;
alter table veiculo add column propriedade varchar(20) not null default 'CLIENTE';
alter table veiculo add column versao varchar(100);
alter table veiculo add column ano_modelo integer;
alter table veiculo add column chassi varchar(30);
alter table veiculo add column renavam varchar(20);
alter table veiculo add column combustivel varchar(40);
alter table veiculo add column cambio varchar(40);
alter table veiculo add column observacoes varchar(4000);
update veiculo set ano_modelo=ano;
alter table veiculo add constraint veiculo_propriedade check
 ((propriedade='CLIENTE' and cliente_id is not null) or
  (propriedade in ('EMPRESA','NAO_INFORMADA') and cliente_id is null));
alter table veiculo add constraint veiculo_identificado check
 (nullif(trim(placa),'') is not null or nullif(trim(chassi),'') is not null);
alter table veiculo add constraint veiculo_ano_modelo check(ano_modelo between 1886 and 2200);
create unique index uq_veiculo_chassi on veiculo(oficina_id,chassi) where chassi is not null;
create unique index uq_veiculo_renavam on veiculo(oficina_id,renavam) where renavam is not null;

create table veiculo_propriedade_historico (
 id bigint generated always as identity primary key,
 oficina_id uuid not null references oficina(id), veiculo_id uuid not null,
 propriedade_anterior varchar(20), cliente_anterior_id uuid,
 propriedade_atual varchar(20) not null, cliente_atual_id uuid,
 autor_id uuid, motivo varchar(500) not null, criado_em timestamptz not null default now(),
 foreign key(veiculo_id,oficina_id) references veiculo(id,oficina_id),
 foreign key(cliente_anterior_id,oficina_id) references cliente(id,oficina_id),
 foreign key(cliente_atual_id,oficina_id) references cliente(id,oficina_id),
 foreign key(autor_id,oficina_id) references usuario(id,oficina_id)
);
insert into veiculo_propriedade_historico(oficina_id,veiculo_id,propriedade_atual,cliente_atual_id,motivo,criado_em)
 select oficina_id,id,'CLIENTE',cliente_id,'Propriedade existente preservada na migração',criado_em from veiculo;
create index ix_propriedade_historico on veiculo_propriedade_historico(oficina_id,veiculo_id,id desc);
create trigger historico_imutavel before update or delete on veiculo_propriedade_historico
 for each row execute function impedir_mutacao_historico();

-- OS interna é a mesma OS, com destinatário empresarial explícito, sem cliente fictício.
alter table ordem_servico alter column cliente_id drop not null;
alter table ordem_servico add column tipo varchar(10) not null default 'CLIENTE';
alter table ordem_servico add constraint os_destinatario check
 ((tipo='CLIENTE' and cliente_id is not null) or (tipo='INTERNA' and cliente_id is null));

create table revenda_avaliacao (
 id uuid primary key, oficina_id uuid not null references oficina(id), unique(id,oficina_id),
 veiculo_id uuid not null, cliente_id uuid, avaliador_id uuid not null,
 data date not null, km bigint not null check(km>=0),
 valor_estimado numeric(14,2) not null check(valor_estimado>=0),
 valor_oferecido numeric(14,2) not null check(valor_oferecido>=0),
 validade timestamptz not null, observacoes varchar(4000),
 status varchar(20) not null default 'ABERTA' check(status in ('ABERTA','ACEITA','RECUSADA','EXPIRADA','CANCELADA')),
 revisao bigint not null default 0, criado_em timestamptz not null default now(),
 foreign key(veiculo_id,oficina_id) references veiculo(id,oficina_id),
 foreign key(cliente_id,oficina_id) references cliente(id,oficina_id),
 foreign key(avaliador_id,oficina_id) references usuario(id,oficina_id)
);
create index ix_avaliacao_lista on revenda_avaliacao(oficina_id,status,criado_em desc,id);
create index ix_avaliacao_veiculo on revenda_avaliacao(oficina_id,veiculo_id);

create table revenda_estoque (
 id uuid primary key, oficina_id uuid not null references oficina(id), unique(id,oficina_id),
 veiculo_id uuid not null, avaliacao_id uuid, responsavel_id uuid not null,
 entrada date not null, origem varchar(20) not null check(origem in ('COMPRA','TROCA','AQUISICAO_DIRETA','OUTRO')),
 valor_aquisicao numeric(14,2) not null check(valor_aquisicao>=0),
 preco_anunciado numeric(14,2) not null check(preco_anunciado>=0),
 preco_minimo numeric(14,2) not null check(preco_minimo>=0 and preco_minimo<=preco_anunciado),
 status varchar(20) not null check(status in ('EM_AVALIACAO','EM_PREPARACAO','DISPONIVEL','RESERVADO','VENDIDO')),
 ordem_servico_id uuid, observacoes varchar(4000),
 revisao bigint not null default 0, criado_em timestamptz not null default now(),
 foreign key(veiculo_id,oficina_id) references veiculo(id,oficina_id),
 foreign key(avaliacao_id,oficina_id) references revenda_avaliacao(id,oficina_id),
 foreign key(responsavel_id,oficina_id) references usuario(id,oficina_id),
 foreign key(ordem_servico_id,oficina_id) references ordem_servico(id,oficina_id),
 unique(oficina_id,avaliacao_id), unique(oficina_id,ordem_servico_id)
);
create unique index uq_estoque_ativo_veiculo on revenda_estoque(oficina_id,veiculo_id) where status<>'VENDIDO';
create index ix_estoque_lista on revenda_estoque(oficina_id,status,entrada desc,id);
create index ix_estoque_preco on revenda_estoque(oficina_id,preco_anunciado);

create table revenda_custo (
 id uuid primary key, oficina_id uuid not null references oficina(id), unique(id,oficina_id),
 estoque_id uuid not null, descricao varchar(500) not null, categoria varchar(60) not null,
 fornecedor varchar(160), valor numeric(14,2) not null check(valor>=0), data date not null,
 observacoes varchar(2000), autor_id uuid not null, ordem_servico_id uuid, orcamento_versao_id uuid,
 criado_em timestamptz not null default now(),
 foreign key(estoque_id,oficina_id) references revenda_estoque(id,oficina_id),
 foreign key(autor_id,oficina_id) references usuario(id,oficina_id),
 foreign key(ordem_servico_id,oficina_id) references ordem_servico(id,oficina_id),
 foreign key(orcamento_versao_id,oficina_id) references orcamento_versao(id,oficina_id),
 unique(oficina_id,ordem_servico_id)
);
create index ix_custo_estoque on revenda_custo(oficina_id,estoque_id,criado_em,id);
create trigger historico_imutavel before update or delete on revenda_custo for each row execute function impedir_mutacao_historico();

create table revenda_lead (
 id uuid primary key, oficina_id uuid not null references oficina(id), unique(id,oficina_id),
 cliente_id uuid not null, veiculo_id uuid, vendedor_id uuid not null,
 origem varchar(20) not null check(origem in ('PRESENCIAL','TELEFONE','WHATSAPP','SITE','INSTAGRAM','FACEBOOK','INDICACAO','MARKETPLACE','OUTRO')),
 status varchar(30) not null default 'NOVO' check(status in ('NOVO','CONTATO_REALIZADO','INTERESSADO','PROPOSTA','NEGOCIACAO','VENDIDO','PERDIDO')),
 observacoes varchar(4000), revisao bigint not null default 0, criado_em timestamptz not null default now(),
 foreign key(cliente_id,oficina_id) references cliente(id,oficina_id),
 foreign key(veiculo_id,oficina_id) references veiculo(id,oficina_id),
 foreign key(vendedor_id,oficina_id) references usuario(id,oficina_id)
);
create index ix_lead_lista on revenda_lead(oficina_id,status,criado_em desc,id);
create index ix_lead_vendedor on revenda_lead(oficina_id,vendedor_id,criado_em desc);
create index ix_lead_veiculo on revenda_lead(oficina_id,veiculo_id);

create table revenda_proposta (
 id uuid primary key, oficina_id uuid not null references oficina(id), unique(id,oficina_id),
 estoque_id uuid not null, cliente_id uuid not null, vendedor_id uuid not null, lead_id uuid,
 status varchar(20) not null default 'RASCUNHO' check(status in ('RASCUNHO','ENVIADA','ACEITA','RECUSADA','EXPIRADA','CANCELADA')),
 numero_versao integer not null default 1 check(numero_versao>0),
 revisao bigint not null default 0, criado_em timestamptz not null default now(),
 foreign key(estoque_id,oficina_id) references revenda_estoque(id,oficina_id),
 foreign key(cliente_id,oficina_id) references cliente(id,oficina_id),
 foreign key(vendedor_id,oficina_id) references usuario(id,oficina_id),
 foreign key(lead_id,oficina_id) references revenda_lead(id,oficina_id)
);
create index ix_proposta_lista on revenda_proposta(oficina_id,status,criado_em desc,id);
create index ix_proposta_estoque on revenda_proposta(oficina_id,estoque_id);
create index ix_proposta_cliente on revenda_proposta(oficina_id,cliente_id);
create index ix_proposta_vendedor on revenda_proposta(oficina_id,vendedor_id);

create table revenda_proposta_versao (
 id uuid primary key, oficina_id uuid not null references oficina(id), unique(id,oficina_id),
 proposta_id uuid not null, numero integer not null check(numero>0),
 preco_anunciado numeric(14,2) not null check(preco_anunciado>=0),
 valor_negociado numeric(14,2) not null check(valor_negociado>=0 and valor_negociado<=preco_anunciado),
 entrada numeric(14,2) not null check(entrada>=0),
 avaliacao_troca_id uuid, valor_troca numeric(14,2) not null default 0 check(valor_troca>=0),
 validade timestamptz not null, observacoes varchar(4000), autor_id uuid not null,
 criado_em timestamptz not null default now(), unique(proposta_id,oficina_id,numero),
 check(entrada+valor_troca<=valor_negociado), check(avaliacao_troca_id is not null or valor_troca=0),
 foreign key(proposta_id,oficina_id) references revenda_proposta(id,oficina_id),
 foreign key(avaliacao_troca_id,oficina_id) references revenda_avaliacao(id,oficina_id),
 foreign key(autor_id,oficina_id) references usuario(id,oficina_id)
);
alter table revenda_proposta add constraint proposta_versao_atual
 foreign key(id,oficina_id,numero_versao) references revenda_proposta_versao(proposta_id,oficina_id,numero) deferrable initially deferred;
create trigger historico_imutavel before update or delete on revenda_proposta_versao for each row execute function impedir_mutacao_historico();

create table revenda_reserva (
 id uuid primary key, oficina_id uuid not null references oficina(id), unique(id,oficina_id),
 estoque_id uuid not null, cliente_id uuid not null, vendedor_id uuid not null,
 validade timestamptz not null, observacoes varchar(2000),
 status varchar(20) not null default 'ATIVA' check(status in ('ATIVA','CANCELADA','EXPIRADA','CONCLUIDA')),
 revisao bigint not null default 0, criado_em timestamptz not null default now(),
 foreign key(estoque_id,oficina_id) references revenda_estoque(id,oficina_id),
 foreign key(cliente_id,oficina_id) references cliente(id,oficina_id),
 foreign key(vendedor_id,oficina_id) references usuario(id,oficina_id)
);
create unique index uq_reserva_ativa on revenda_reserva(oficina_id,estoque_id) where status='ATIVA';
create index ix_reserva_lista on revenda_reserva(oficina_id,status,validade);

create table revenda_venda (
 id uuid primary key, oficina_id uuid not null references oficina(id), unique(id,oficina_id),
 estoque_id uuid not null, cliente_id uuid not null, vendedor_id uuid not null,
 proposta_id uuid, proposta_versao_id uuid, reserva_id uuid, estoque_troca_id uuid,
 preco_anunciado numeric(14,2) not null check(preco_anunciado>=0),
 valor_vendido numeric(14,2) not null check(valor_vendido>=0 and valor_vendido<=preco_anunciado),
 entrada numeric(14,2) not null check(entrada>=0), valor_troca numeric(14,2) not null check(valor_troca>=0),
 custo_acumulado numeric(18,2) not null check(custo_acumulado>=0),
 margem_bruta numeric(18,2) not null,
 observacoes varchar(4000), criado_em timestamptz not null default now(),
 unique(oficina_id,estoque_id), unique(oficina_id,proposta_id), unique(oficina_id,estoque_troca_id),
 check(entrada+valor_troca<=valor_vendido), check(estoque_troca_id is not null or valor_troca=0),
 check(margem_bruta=valor_vendido-custo_acumulado), check(estoque_troca_id is null or estoque_troca_id<>estoque_id),
 foreign key(estoque_id,oficina_id) references revenda_estoque(id,oficina_id),
 foreign key(cliente_id,oficina_id) references cliente(id,oficina_id),
 foreign key(vendedor_id,oficina_id) references usuario(id,oficina_id),
 foreign key(proposta_id,oficina_id) references revenda_proposta(id,oficina_id),
 foreign key(proposta_versao_id,oficina_id) references revenda_proposta_versao(id,oficina_id),
 foreign key(reserva_id,oficina_id) references revenda_reserva(id,oficina_id),
 foreign key(estoque_troca_id,oficina_id) references revenda_estoque(id,oficina_id)
);
create index ix_venda_periodo on revenda_venda(oficina_id,criado_em desc,id);
create index ix_venda_vendedor on revenda_venda(oficina_id,vendedor_id,criado_em desc);
create index ix_venda_cliente on revenda_venda(oficina_id,cliente_id,criado_em desc);
create trigger historico_imutavel before update or delete on revenda_venda for each row execute function impedir_mutacao_historico();

create table revenda_foto (
 id uuid primary key, oficina_id uuid not null references oficina(id), unique(id,oficina_id),
 avaliacao_id uuid, estoque_id uuid, finalidade varchar(20) not null check(finalidade in ('AVALIACAO','ESTOQUE','PREPARACAO')),
 descricao varchar(500), objeto varchar(300) not null unique, content_type varchar(40) not null,
 tamanho bigint not null check(tamanho>0 and tamanho<=10485760), autor_id uuid not null,
 criado_em timestamptz not null default now(), check(num_nonnulls(avaliacao_id,estoque_id)=1),
 foreign key(avaliacao_id,oficina_id) references revenda_avaliacao(id,oficina_id),
 foreign key(estoque_id,oficina_id) references revenda_estoque(id,oficina_id),
 foreign key(autor_id,oficina_id) references usuario(id,oficina_id)
);
create index ix_foto_avaliacao on revenda_foto(oficina_id,avaliacao_id,criado_em,id);
create index ix_foto_estoque on revenda_foto(oficina_id,estoque_id,criado_em,id);

create table revenda_evento (
 id bigint generated always as identity primary key, oficina_id uuid not null references oficina(id),
 estoque_id uuid, avaliacao_id uuid, lead_id uuid, proposta_id uuid, reserva_id uuid, venda_id uuid,
 tipo varchar(50) not null, descricao varchar(4000) not null, versao integer, autor_id uuid not null,
 criado_em timestamptz not null default now(), check(num_nonnulls(estoque_id,avaliacao_id,lead_id,proposta_id,reserva_id,venda_id)=1),
 foreign key(estoque_id,oficina_id) references revenda_estoque(id,oficina_id),
 foreign key(avaliacao_id,oficina_id) references revenda_avaliacao(id,oficina_id),
 foreign key(lead_id,oficina_id) references revenda_lead(id,oficina_id),
 foreign key(proposta_id,oficina_id) references revenda_proposta(id,oficina_id),
 foreign key(reserva_id,oficina_id) references revenda_reserva(id,oficina_id),
 foreign key(venda_id,oficina_id) references revenda_venda(id,oficina_id),
 foreign key(autor_id,oficina_id) references usuario(id,oficina_id)
);
create index ix_evento_estoque on revenda_evento(oficina_id,estoque_id,id);
create index ix_evento_avaliacao on revenda_evento(oficina_id,avaliacao_id,id);
create index ix_evento_lead on revenda_evento(oficina_id,lead_id,id);
create index ix_evento_proposta on revenda_evento(oficina_id,proposta_id,versao,id);
create index ix_evento_reserva on revenda_evento(oficina_id,reserva_id,id);
create index ix_evento_venda on revenda_evento(oficina_id,venda_id,id);
create trigger historico_imutavel before update or delete on revenda_evento for each row execute function impedir_mutacao_historico();

create function registrar_propriedade_veiculo() returns trigger language plpgsql as $$
begin
 if tg_op='INSERT' or (old.propriedade,old.cliente_id) is distinct from (new.propriedade,new.cliente_id) then
   if new.propriedade<>'EMPRESA' and exists(select 1 from revenda_estoque where oficina_id=new.oficina_id and veiculo_id=new.id and status<>'VENDIDO') then
     raise exception 'Veículo com estoque ativo não pode ser transferido diretamente' using errcode='23514';
   end if;
   insert into veiculo_propriedade_historico(oficina_id,veiculo_id,propriedade_anterior,cliente_anterior_id,propriedade_atual,cliente_atual_id,autor_id,motivo)
   values(new.oficina_id,new.id,case when tg_op='UPDATE' then old.propriedade end,
     case when tg_op='UPDATE' then old.cliente_id end,new.propriedade,new.cliente_id,
     nullif(current_setting('app.autor_propriedade',true),'')::uuid,
     coalesce(nullif(current_setting('app.motivo_propriedade',true),''),'Cadastro de veículo atualizado'));
 end if;
 return new;
end $$;
create trigger propriedade_auditada after insert or update of propriedade,cliente_id on veiculo
 for each row execute function registrar_propriedade_veiculo();

create function validar_propriedade_estoque() returns trigger language plpgsql as $$
begin
 if new.status<>'VENDIDO' and not exists(select 1 from veiculo where id=new.veiculo_id and oficina_id=new.oficina_id and propriedade='EMPRESA') then
   raise exception 'Estoque ativo exige propriedade da empresa' using errcode='23514';
 end if;
 return new;
end $$;
create trigger estoque_propriedade before insert or update on revenda_estoque
 for each row execute function validar_propriedade_estoque();
