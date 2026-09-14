-- Remove as proteções adicionais de V2; revisar impacto antes de executar.
BEGIN;
DROP TRIGGER conferir_total ON orcamento_versao;
DROP FUNCTION validar_total_orcamento();
DROP TRIGGER itens_somente_na_criacao ON item_orcamento;
DROP FUNCTION impedir_item_em_versao_antiga();
ALTER TABLE orcamento_versao DROP COLUMN criacao_tx;
COMMIT;
