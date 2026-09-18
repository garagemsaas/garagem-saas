-- Por que um refresh token foi revogado. Sem isto, "revogado" é ambíguo e os dois casos que
-- precisam de tratamento oposto ficam indistinguíveis:
--
--   LOGOUT   -- o usuário pediu. Reapresentar o token é uma aba atrasada, não um ataque.
--   ROTACAO  -- foi trocado por um novo no refresh. Reapresentá-lo significa que duas partes
--               tiveram o mesmo segredo, e aí a família inteira deve cair.
--
-- Tratar logout como reuso derrubaria todas as sessões da pessoa a cada saída, e ainda geraria
-- alarme de segurança falso.
alter table refresh_token add column motivo_revogacao varchar(20);

-- O preenchimento vem ANTES das restrições. Numa base que já tem tokens revogados, criar a
-- restrição primeiro a viola na hora e derruba a subida da aplicação — foi o que aconteceu ao
-- validar esta migration contra um banco com dados.
--
-- Tokens revogados antes desta migration não têm motivo registrado. Marcá-los como ROTACAO
-- transformaria histórico em suspeita de roubo; LOGOUT é a leitura conservadora e inofensiva.
update refresh_token set motivo_revogacao='LOGOUT' where revogado_em is not null;

alter table refresh_token add constraint motivo_revogacao_coerente
 check ((revogado_em is null) = (motivo_revogacao is null));

alter table refresh_token add constraint motivo_revogacao_conhecido
 check (motivo_revogacao is null
        or motivo_revogacao in ('LOGOUT','ROTACAO','REUSO','DESATIVACAO'));

create index ix_refresh_token_usuario_ativo on refresh_token(oficina_id,usuario_id)
 where revogado_em is null;
