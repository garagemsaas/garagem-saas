create table oficina (
 id uuid primary key, nome varchar(160) not null, slug varchar(80) not null unique,
 plano varchar(40) not null default 'INICIAL', situacao varchar(30) not null default 'ATIVA',
 proximo_numero_os bigint not null default 1, criado_em timestamptz not null default now()
);

create table usuario (
 id uuid primary key, oficina_id uuid not null references oficina(id), criado_em timestamptz not null default now(),
 unique(id,oficina_id),
 nome varchar(160) not null, email varchar(254) not null, senha_hash varchar(100) not null, papel varchar(20) not null check (papel in ('OWNER','MECANICO','ATENDENTE')), ativo boolean not null default true, unique(oficina_id,email)
);
create index ix_usuario_oficina on usuario(oficina_id);

create table refresh_token (
 id uuid primary key, oficina_id uuid not null references oficina(id), criado_em timestamptz not null default now(),
 unique(id,oficina_id),
 usuario_id uuid not null, token_hash varchar(64) not null unique, expira_em timestamptz not null, revogado_em timestamptz, foreign key(usuario_id,oficina_id) references usuario(id,oficina_id)
);
create index ix_refresh_token_oficina on refresh_token(oficina_id);

create table cliente (
 id uuid primary key, oficina_id uuid not null references oficina(id), criado_em timestamptz not null default now(),
 unique(id,oficina_id),
 revisao bigint not null default 0, nome varchar(160) not null, telefone varchar(30) not null, email varchar(254)
);
create index ix_cliente_oficina on cliente(oficina_id);

create table veiculo (
 id uuid primary key, oficina_id uuid not null references oficina(id), criado_em timestamptz not null default now(),
 unique(id,oficina_id),
 revisao bigint not null default 0, cliente_id uuid not null, placa varchar(7) not null, marca varchar(80) not null, modelo varchar(100) not null, ano integer not null check(ano between 1886 and 2200), km bigint not null check(km >= 0), cor varchar(60) not null, unique(oficina_id,placa), foreign key(cliente_id,oficina_id) references cliente(id,oficina_id)
);
create index ix_veiculo_oficina on veiculo(oficina_id);

create table ordem_servico (
 id uuid primary key, oficina_id uuid not null references oficina(id), criado_em timestamptz not null default now(),
 unique(id,oficina_id),
 revisao bigint not null default 0, numero bigint not null, veiculo_id uuid not null, cliente_id uuid not null, mecanico_id uuid, status varchar(30) not null check(status in ('RECEBIDO','DIAGNOSTICO','ORCAMENTO','AGUARDANDO_APROVACAO','EM_MANUTENCAO','AGUARDANDO_PECA','TESTE','PRONTO')), km_entrada bigint not null check(km_entrada >= 0), relato varchar(4000) not null, previsao_entrega timestamptz, concluida_em timestamptz, unique(oficina_id,numero), foreign key(veiculo_id,oficina_id) references veiculo(id,oficina_id), foreign key(cliente_id,oficina_id) references cliente(id,oficina_id), foreign key(mecanico_id,oficina_id) references usuario(id,oficina_id)
);
create index ix_ordem_servico_oficina on ordem_servico(oficina_id);

create table checklist_entrada (
 id uuid primary key, oficina_id uuid not null references oficina(id), criado_em timestamptz not null default now(),
 unique(id,oficina_id),
 ordem_servico_id uuid not null, autor_id uuid not null, observacoes varchar(4000), unique(oficina_id,ordem_servico_id), foreign key(ordem_servico_id,oficina_id) references ordem_servico(id,oficina_id), foreign key(autor_id,oficina_id) references usuario(id,oficina_id)
);
create index ix_checklist_entrada_oficina on checklist_entrada(oficina_id);

create table checklist_item (
 id uuid primary key, oficina_id uuid not null references oficina(id), criado_em timestamptz not null default now(),
 unique(id,oficina_id),
 checklist_entrada_id uuid not null, descricao varchar(200) not null, condicao varchar(100) not null, observacao varchar(1000), foreign key(checklist_entrada_id,oficina_id) references checklist_entrada(id,oficina_id)
);
create index ix_checklist_item_oficina on checklist_item(oficina_id);

create table diagnostico_item (
 id uuid primary key, oficina_id uuid not null references oficina(id), criado_em timestamptz not null default now(),
 unique(id,oficina_id),
 ordem_servico_id uuid not null, autor_id uuid not null, descricao varchar(4000) not null, classificacao varchar(10) not null check(classificacao in ('VERDE','AMARELO','VERMELHO')), foreign key(ordem_servico_id,oficina_id) references ordem_servico(id,oficina_id), foreign key(autor_id,oficina_id) references usuario(id,oficina_id)
);
create index ix_diagnostico_item_oficina on diagnostico_item(oficina_id);

create table foto_veiculo (
 id uuid primary key, oficina_id uuid not null references oficina(id), criado_em timestamptz not null default now(),
 unique(id,oficina_id),
 ordem_servico_id uuid not null, checklist_item_id uuid, diagnostico_item_id uuid, autor_id uuid not null, objeto varchar(300) not null unique, content_type varchar(30) not null, finalidade varchar(30) not null, descricao varchar(500), tamanho bigint not null check(tamanho > 0), check(checklist_item_id is null or diagnostico_item_id is null), foreign key(ordem_servico_id,oficina_id) references ordem_servico(id,oficina_id), foreign key(checklist_item_id,oficina_id) references checklist_item(id,oficina_id), foreign key(diagnostico_item_id,oficina_id) references diagnostico_item(id,oficina_id), foreign key(autor_id,oficina_id) references usuario(id,oficina_id)
);
create index ix_foto_veiculo_oficina on foto_veiculo(oficina_id);

create table orcamento (
 id uuid primary key, oficina_id uuid not null references oficina(id), criado_em timestamptz not null default now(),
 unique(id,oficina_id),
 ordem_servico_id uuid not null, unique(oficina_id,ordem_servico_id), foreign key(ordem_servico_id,oficina_id) references ordem_servico(id,oficina_id)
);
create index ix_orcamento_oficina on orcamento(oficina_id);

create table orcamento_versao (
 id uuid primary key, oficina_id uuid not null references oficina(id), criado_em timestamptz not null default now(),
 unique(id,oficina_id),
 orcamento_id uuid not null, numero integer not null check(numero > 0), autor_id uuid not null, observacoes varchar(4000), total numeric(19,2) not null check(total >= 0), unique(oficina_id,orcamento_id,numero), foreign key(orcamento_id,oficina_id) references orcamento(id,oficina_id), foreign key(autor_id,oficina_id) references usuario(id,oficina_id)
);
create index ix_orcamento_versao_oficina on orcamento_versao(oficina_id);

create table item_orcamento (
 id uuid primary key, oficina_id uuid not null references oficina(id), criado_em timestamptz not null default now(),
 unique(id,oficina_id),
 orcamento_versao_id uuid not null, tipo varchar(10) not null check(tipo in ('PECA','SERVICO')), descricao varchar(500) not null, quantidade numeric(12,3) not null check(quantidade > 0), valor_unitario numeric(12,2) not null check(valor_unitario >= 0), foreign key(orcamento_versao_id,oficina_id) references orcamento_versao(id,oficina_id)
);
create index ix_item_orcamento_oficina on item_orcamento(oficina_id);

create table link_acesso_publico (
 id uuid primary key, oficina_id uuid not null references oficina(id), criado_em timestamptz not null default now(),
 unique(id,oficina_id),
 ordem_servico_id uuid not null, token_hash varchar(64) not null unique, expira_em timestamptz not null, revogado_em timestamptz, foreign key(ordem_servico_id,oficina_id) references ordem_servico(id,oficina_id)
);
create index ix_link_acesso_publico_oficina on link_acesso_publico(oficina_id);

create table aprovacao_orcamento (
 id uuid primary key, oficina_id uuid not null references oficina(id), criado_em timestamptz not null default now(),
 unique(id,oficina_id),
 orcamento_versao_id uuid not null, link_acesso_publico_id uuid not null, aprovado boolean not null, canal varchar(30) not null check(canal = 'LINK_PUBLICO'), ip_origem varchar(45) not null, unique(oficina_id,orcamento_versao_id), foreign key(orcamento_versao_id,oficina_id) references orcamento_versao(id,oficina_id), foreign key(link_acesso_publico_id,oficina_id) references link_acesso_publico(id,oficina_id)
);
create index ix_aprovacao_orcamento_oficina on aprovacao_orcamento(oficina_id);

create table evento_ordem_servico (
 id uuid primary key, oficina_id uuid not null references oficina(id), criado_em timestamptz not null default now(),
 unique(id,oficina_id),
 ordem_servico_id uuid not null, autor_id uuid, origem varchar(30) not null, tipo varchar(50) not null, descricao varchar(4000) not null, foreign key(ordem_servico_id,oficina_id) references ordem_servico(id,oficina_id), foreign key(autor_id,oficina_id) references usuario(id,oficina_id)
);
create index ix_evento_ordem_servico_oficina on evento_ordem_servico(oficina_id);

create index ix_os_veiculo on ordem_servico(oficina_id,veiculo_id);
create index ix_evento_os on evento_ordem_servico(oficina_id,ordem_servico_id,criado_em);
create index ix_foto_os on foto_veiculo(oficina_id,ordem_servico_id);
create function impedir_mutacao_historico() returns trigger language plpgsql as $$
begin raise exception 'Historico imutavel' using errcode = '23000'; end;
$$;
create trigger historico_imutavel before update or delete on orcamento_versao for each row execute function impedir_mutacao_historico();
create trigger historico_imutavel before update or delete on item_orcamento for each row execute function impedir_mutacao_historico();
create trigger historico_imutavel before update or delete on aprovacao_orcamento for each row execute function impedir_mutacao_historico();
create trigger historico_imutavel before update or delete on evento_ordem_servico for each row execute function impedir_mutacao_historico();
