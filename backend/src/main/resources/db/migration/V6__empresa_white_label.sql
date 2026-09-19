-- oficina/oficina_id permanecem como identificadores técnicos do tenant.
alter table oficina add column nome_exibicao varchar(160);
alter table oficina add column telefone varchar(40);
alter table oficina add column email varchar(254);
alter table oficina add column contato varchar(500);
alter table oficina add column cor_primaria varchar(7) not null default '#356452';
alter table oficina add column cor_secundaria varchar(7) not null default '#17352b';
alter table oficina add column revisao_branding bigint not null default 0;
alter table oficina add constraint cores_empresa check
 (cor_primaria ~ '^#[0-9A-Fa-f]{6}$' and cor_secundaria ~ '^#[0-9A-Fa-f]{6}$');

create table empresa_modulo (
 oficina_id uuid not null references oficina(id),
 modulo varchar(20) not null check (modulo in ('OFICINA','REVENDA')),
 primary key(oficina_id,modulo)
);
insert into empresa_modulo select id,'OFICINA' from oficina;
create function modulo_inicial_empresa() returns trigger language plpgsql as $$
begin
 insert into empresa_modulo values(new.id,'OFICINA');
 return new;
end $$;
create trigger modulo_inicial_empresa after insert on oficina
 for each row execute function modulo_inicial_empresa();

-- Raster pequeno, transacional, sem URL externa, nome original ou objeto órfão.
create table empresa_imagem (
 oficina_id uuid not null references oficina(id),
 tipo varchar(10) not null check(tipo in ('logo','favicon')),
 id uuid not null,
 conteudo bytea not null check(octet_length(conteudo) <= 2097152),
 primary key(oficina_id,tipo), unique(id,oficina_id)
);

create table empresa_administracao_evento (
 id bigint generated always as identity primary key,
 oficina_id uuid not null references oficina(id),
 operador varchar(160) not null,
 motivo varchar(500) not null,
 antes jsonb not null,
 depois jsonb not null,
 criado_em timestamptz not null default now()
);
create trigger historico_imutavel before update or delete on empresa_administracao_evento
 for each row execute function impedir_mutacao_historico();

-- Somente operador com acesso ao banco. Sem endpoint administrativo ou papel global.
create function administrar_empresa(p_slug text, p_status text, p_modulos text[],
 p_operador text, p_motivo text) returns void language plpgsql security invoker as $$
declare empresa oficina; anterior jsonb;
begin
 if p_status is null or p_status not in ('ATIVA','INATIVA')
    or p_modulos is null or cardinality(p_modulos)=0
    or array_position(p_modulos,null) is not null
    or not p_modulos <@ array['OFICINA','REVENDA']::text[]
    or nullif(trim(p_operador),'') is null or nullif(trim(p_motivo),'') is null then
   raise exception 'Configuração administrativa inválida';
 end if;
 select * into strict empresa from oficina where slug=p_slug for update;
 anterior := jsonb_build_object('status',empresa.situacao,'modulos',
   (select jsonb_agg(modulo order by modulo) from empresa_modulo where oficina_id=empresa.id));
 update oficina set situacao=p_status where id=empresa.id;
 delete from empresa_modulo where oficina_id=empresa.id;
 insert into empresa_modulo select empresa.id,m from (select distinct unnest(p_modulos) m) x;
 insert into empresa_administracao_evento(oficina_id,operador,motivo,antes,depois)
 values(empresa.id,p_operador,p_motivo,anterior,
   jsonb_build_object('status',p_status,'modulos',p_modulos));
end $$;
revoke all on function administrar_empresa(text,text,text[],text,text) from public;

-- Preserva contratos/eventos históricos, mas novas empresas não recebem trial automático.
alter table oficina disable trigger assinatura_da_oficina;
