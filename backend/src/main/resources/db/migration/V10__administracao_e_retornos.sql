-- Dados da função retirada são preservados apenas para recuperação administrativa.
-- Nenhuma API lê este arquivo histórico; não altera dados operacionais de clientes.
create table empresa_configuracao_arquivada (
 oficina_id uuid primary key references oficina(id), configuracao jsonb not null,
 capa bytea, criado_em timestamptz not null default now()
);
insert into empresa_configuracao_arquivada(oficina_id,configuracao,capa)
 select o.id,jsonb_build_object('corPrimaria',o.cor_primaria,'corSecundaria',o.cor_secundaria,
 'publicado',o.site_publicado,'frase',o.site_frase,'sobre',o.site_sobre,'servicos',o.site_servicos,
 'endereco',o.site_endereco,'horario',o.site_horario,'whatsapp',o.site_whatsapp,'instagram',o.site_instagram),i.conteudo
 from oficina o left join empresa_imagem i on i.oficina_id=o.id and i.tipo='capa';
delete from empresa_imagem where tipo='capa';
alter table empresa_imagem drop constraint empresa_imagem_tipo_check;
alter table empresa_imagem add constraint empresa_imagem_tipo_check check(tipo in ('logo','favicon'));
alter table oficina drop column cor_primaria,drop column cor_secundaria,
 drop column site_publicado,drop column site_frase,drop column site_sobre,drop column site_servicos,
 drop column site_endereco,drop column site_horario,drop column site_whatsapp,drop column site_instagram;
alter table oficina add column revisao_administracao bigint not null default 0;
alter table usuario add column versao_sessao bigint not null default 0;
alter table oficina add constraint empresa_situacao check(situacao in ('ATIVA','SUSPENSA','INATIVA'));

-- Empresas híbridas passam à revenda quando já têm estoque comercial; caso contrário, oficina.
-- Os dados dos dois negócios permanecem íntegros. A plataforma pode trocar a operação depois.
insert into empresa_administracao_evento(oficina_id,operador,motivo,antes,depois)
 select o.id,'migration-v10','Operação exclusiva; histórico operacional preservado',
 jsonb_build_object('modulos',array['OFICINA','REVENDA']),
 jsonb_build_object('modulos',array[case when exists(select 1 from revenda_estoque e where e.oficina_id=o.id) then 'REVENDA' else 'OFICINA' end])
 from oficina o where (select count(*) from empresa_modulo m where m.oficina_id=o.id)>1;
delete from empresa_modulo m where exists(select 1 from empresa_modulo n where n.oficina_id=m.oficina_id and n.modulo<>m.modulo)
 and m.modulo<>case when exists(select 1 from revenda_estoque e where e.oficina_id=m.oficina_id) then 'REVENDA' else 'OFICINA' end;
create unique index empresa_operacao_exclusiva on empresa_modulo(oficina_id);

create or replace function provisionar_empresa(p_slug text,p_nome text,p_modulos text[],p_operador text,p_motivo text)
 returns uuid language plpgsql security invoker as $$
declare nova uuid;
begin
 if p_modulos is null or cardinality(p_modulos)<>1 or p_modulos[1] is null
 or not p_modulos <@ array['OFICINA','REVENDA']::text[]
 or nullif(trim(coalesce(p_slug,'')),'') is null or nullif(trim(coalesce(p_nome,'')),'') is null
 or nullif(trim(coalesce(p_operador,'')),'') is null or nullif(trim(coalesce(p_motivo,'')),'') is null then
 raise exception 'Configuração administrativa inválida'; end if;
 insert into oficina(id,nome,slug) values(gen_random_uuid(),p_nome,p_slug) on conflict(slug) do nothing returning id into nova;
 if nova is null then return null; end if;
 insert into empresa_modulo values(nova,p_modulos[1]);
 insert into empresa_administracao_evento(oficina_id,operador,motivo,antes,depois)
 values(nova,p_operador,p_motivo,'{}',jsonb_build_object('status','ATIVA','modulos',p_modulos));
 return nova;
end $$;
create or replace function administrar_empresa(p_slug text,p_status text,p_modulos text[],p_operador text,p_motivo text)
 returns void language plpgsql security invoker as $$
declare empresa oficina; anterior jsonb;
begin
 if p_status is null or p_status not in ('ATIVA','SUSPENSA','INATIVA') or p_modulos is null
 or cardinality(p_modulos)<>1 or p_modulos[1] is null or not p_modulos <@ array['OFICINA','REVENDA']::text[]
 or nullif(trim(coalesce(p_operador,'')),'') is null or nullif(trim(coalesce(p_motivo,'')),'') is null then
 raise exception 'Configuração administrativa inválida'; end if;
 select * into strict empresa from oficina where slug=p_slug for update;
 anterior:=jsonb_build_object('status',empresa.situacao,'modulos',(select jsonb_agg(modulo) from empresa_modulo where oficina_id=empresa.id));
 update oficina set situacao=p_status,revisao_administracao=revisao_administracao+1 where id=empresa.id;
 update usuario set versao_sessao=versao_sessao+1 where oficina_id=empresa.id;
 delete from empresa_modulo where oficina_id=empresa.id;
 insert into empresa_modulo values(empresa.id,p_modulos[1]);
 update refresh_token set revogado_em=now(),motivo_revogacao='DESATIVACAO' where oficina_id=empresa.id and revogado_em is null;
 insert into empresa_administracao_evento(oficina_id,operador,motivo,antes,depois)
 values(empresa.id,p_operador,p_motivo,anterior,jsonb_build_object('status',p_status,'modulos',p_modulos));
end $$;

create table plataforma_usuario (
 id uuid primary key, nome varchar(160) not null,email varchar(254) not null unique,
 senha_hash varchar(100) not null,papel varchar(30) not null check(papel in ('DESENVOLVEDOR','ADMIN_PLATAFORMA')),
 ativo boolean not null default true, versao_sessao bigint not null default 0,criado_em timestamptz not null default now()
);
create table plataforma_auditoria (
 id bigint generated always as identity primary key,autor_id uuid not null references plataforma_usuario(id),
 empresa_id uuid references oficina(id),acao varchar(80) not null,descricao varchar(2000) not null,
 criado_em timestamptz not null default now()
);
create trigger historico_imutavel before update or delete on plataforma_auditoria for each row execute function impedir_mutacao_historico();
create index plataforma_auditoria_empresa on plataforma_auditoria(empresa_id,id desc);
create table usuario_auditoria (
 id bigint generated always as identity primary key,oficina_id uuid not null references oficina(id),
 autor_id uuid not null,usuario_id uuid not null,acao varchar(80) not null,descricao varchar(1000) not null,
 criado_em timestamptz not null default now(),
 foreign key(autor_id,oficina_id) references usuario(id,oficina_id),foreign key(usuario_id,oficina_id) references usuario(id,oficina_id)
);
create trigger historico_imutavel before update or delete on usuario_auditoria for each row execute function impedir_mutacao_historico();

create table retorno (
 id uuid primary key,oficina_id uuid not null references oficina(id),unique(id,oficina_id),
 operacao varchar(20) not null check(operacao in ('OFICINA','REVENDA')),
 cliente_id uuid not null,veiculo_id uuid,responsavel_id uuid not null,
 motivo varchar(500) not null,agendado_em timestamptz not null,
 prioridade varchar(10) not null check(prioridade in ('NORMAL','ALTA')),
 status varchar(15) not null default 'PENDENTE' check(status in ('PENDENTE','CONCLUIDO','CANCELADO')),
 observacoes varchar(2000),resultado varchar(2000),origem_tipo varchar(40),origem_id uuid,
 revisao bigint not null default 0,criado_em timestamptz not null default now(),encerrado_em timestamptz,
 foreign key(cliente_id,oficina_id) references cliente(id,oficina_id),
 foreign key(veiculo_id,oficina_id) references veiculo(id,oficina_id),
 foreign key(responsavel_id,oficina_id) references usuario(id,oficina_id),
 check((origem_tipo is null)=(origem_id is null)),unique(oficina_id,origem_tipo,origem_id)
);
create index retorno_agenda on retorno(oficina_id,operacao,status,agendado_em,id);
create index retorno_responsavel on retorno(oficina_id,responsavel_id,status,agendado_em);
create table retorno_evento (
 id bigint generated always as identity primary key,oficina_id uuid not null references oficina(id),
 retorno_id uuid not null,autor_id uuid not null,descricao varchar(2000) not null,criado_em timestamptz not null default now(),
 foreign key(retorno_id,oficina_id) references retorno(id,oficina_id),foreign key(autor_id,oficina_id) references usuario(id,oficina_id)
);
create trigger historico_imutavel before update or delete on retorno_evento for each row execute function impedir_mutacao_historico();
create index retorno_evento_lista on retorno_evento(oficina_id,retorno_id,id desc);
-- Preserva a agenda e a responsabilidade de retornos já existentes na oficina.
insert into retorno(id,oficina_id,operacao,cliente_id,veiculo_id,responsavel_id,motivo,agendado_em,prioridade,status,observacoes,origem_tipo,origem_id)
 select gen_random_uuid(),o.oficina_id,'OFICINA',o.cliente_id,o.veiculo_id,
 coalesce(o.responsavel_id,(select u.id from usuario u where u.oficina_id=o.oficina_id and u.ativo and u.papel in ('OWNER','ATENDENTE') order by u.criado_em,u.id limit 1)),
 case o.tipo when 'ORCAMENTO_ESQUECIDO' then 'Consultar cliente sobre orçamento sem resposta' when 'REVISAO_ATRASADA' then 'Agendar revisão do veículo' else 'Conversar sobre orçamento recusado' end,
 coalesce(o.proximo_contato_em,o.criado_em),'NORMAL','PENDENTE',null,'OPORTUNIDADE',o.id
 from oportunidade_recuperacao o where o.status in ('ABERTA','EM_CONTATO','AGENDADA')
 and exists(select 1 from usuario u where u.oficina_id=o.oficina_id and u.ativo and u.papel in ('OWNER','ATENDENTE'));
