-- O padrão OFICINA existia para que nenhuma empresa nascesse sem módulo, e isso continua valendo.
-- O problema era o momento: sendo imediato, uma empresa de revenda nascia OFICINA e só depois era
-- corrigida. Registrava um estado que nunca deveria ter existido e obrigava todo provisionamento a
-- passar por "criar errado e desfazer".
--
-- Adiando o gatilho para o fim da transação, o padrão vira o que sempre quis ser: rede de segurança.
-- Se a transação que criou a empresa já definiu os módulos, ele não faz nada; se ninguém definiu,
-- ela ainda assim não termina sem módulo algum.
drop trigger modulo_inicial_empresa on oficina;
create or replace function modulo_inicial_empresa() returns trigger language plpgsql as $$
begin
 if not exists(select 1 from empresa_modulo where oficina_id=new.id) then
   insert into empresa_modulo values(new.id,'OFICINA');
 end if;
 return null;
end $$;
create constraint trigger modulo_inicial_empresa after insert on oficina
 deferrable initially deferred for each row execute function modulo_inicial_empresa();

-- Provisionamento direto: a empresa nasce com exatamente os módulos contratados e com a trilha de
-- quem a criou, numa transação só. Devolve null quando o slug já existe, o que mantém o bootstrap
-- idempotente sem precisar consultar antes e correr contra outra instância subindo ao mesmo tempo.
--
-- Mesma validação e mesmo acesso de administrar_empresa: sem endpoint HTTP, sem papel global.
create function provisionar_empresa(p_slug text, p_nome text, p_modulos text[],
 p_operador text, p_motivo text) returns uuid language plpgsql security invoker as $$
declare nova uuid;
begin
 if p_modulos is null or cardinality(p_modulos)=0
    or array_position(p_modulos,null) is not null
    or not p_modulos <@ array['OFICINA','REVENDA']::text[]
    or nullif(trim(coalesce(p_slug,'')),'') is null
    or nullif(trim(coalesce(p_nome,'')),'') is null
    or nullif(trim(coalesce(p_operador,'')),'') is null
    or nullif(trim(coalesce(p_motivo,'')),'') is null then
   raise exception 'Configuração administrativa inválida';
 end if;
 insert into oficina(id,nome,slug) values(gen_random_uuid(),p_nome,p_slug)
  on conflict(slug) do nothing returning id into nova;
 if nova is null then return null; end if;
 insert into empresa_modulo select nova,m from (select distinct unnest(p_modulos) m) x;
 insert into empresa_administracao_evento(oficina_id,operador,motivo,antes,depois)
 values(nova,p_operador,p_motivo,
   jsonb_build_object('status',null,'modulos',null),
   jsonb_build_object('status',(select situacao from oficina where id=nova),
     'modulos',(select jsonb_agg(m order by m) from (select distinct unnest(p_modulos) m) y)));
 return nova;
end $$;
revoke all on function provisionar_empresa(text,text,text[],text,text) from public;
