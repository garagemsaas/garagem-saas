-- Site público da empresa: presença digital, não captação. O que entra aqui é só o que uma pessoa
-- procurando a oficina na rua precisa saber — quem é, o que faz, onde fica, como falar.
--
-- `site_publicado` começa false de propósito. Telefone e endereço de toda empresa cadastrada não
-- podem virar página aberta na internet porque uma migration rodou: publicar é uma decisão do dono,
-- tomada uma vez, na tela de configurações.
alter table oficina add column site_publicado boolean not null default false;
alter table oficina add column site_frase varchar(160);
alter table oficina add column site_sobre varchar(2000);
-- Um serviço por linha. É uma lista de seis itens que muda de ano em ano: uma tabela própria
-- custaria mais manutenção do que resolve, e ninguém consulta serviço por chave estrangeira.
alter table oficina add column site_servicos varchar(1000);
alter table oficina add column site_endereco varchar(300);
alter table oficina add column site_horario varchar(300);
alter table oficina add column site_whatsapp varchar(20);
alter table oficina add column site_instagram varchar(100);

-- WhatsApp é só dígitos, com DDI e DDD: o link do wa.me não aceita máscara, e validar aqui evita
-- descobrir o problema quando o cliente clicar.
alter table oficina add constraint site_whatsapp_numerico
 check (site_whatsapp is null or site_whatsapp ~ '^[0-9]{10,15}$');
-- Guardamos o identificador, nunca a URL: monta-se o endereço na exibição, e assim ninguém cola
-- um link para outro domínio no lugar do perfil.
alter table oficina add constraint site_instagram_usuario
 check (site_instagram is null or site_instagram ~ '^[A-Za-z0-9._]{1,30}$');

-- Publicar exige ter o que mostrar. Sem isto, um clique distraído colocaria no ar uma página com
-- o nome da empresa e mais nada — pior para a credibilidade do que não ter página nenhuma.
alter table oficina add constraint site_publicado_completo check (
  not site_publicado or (
    nullif(trim(site_frase),'') is not null
    and nullif(trim(site_sobre),'') is not null
    and nullif(trim(site_endereco),'') is not null
    and nullif(trim(site_horario),'') is not null
    and nullif(trim(coalesce(telefone,'')),'') is not null
  )
);

-- A capa é a imagem de abertura do site. Mesma tabela do logo e do favicon: mesmo limite de 2 MB,
-- mesma reescrita para PNG, mesmo isolamento por empresa. Não há motivo para um segundo caminho.
alter table empresa_imagem drop constraint empresa_imagem_tipo_check;
alter table empresa_imagem add constraint empresa_imagem_tipo_check
 check (tipo in ('logo','favicon','capa'));
