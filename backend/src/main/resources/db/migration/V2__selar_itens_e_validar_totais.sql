-- A versão e seus itens só podem ser criados na mesma transação.
ALTER TABLE orcamento_versao ADD COLUMN criacao_tx xid8 NOT NULL DEFAULT pg_current_xact_id();
CREATE FUNCTION impedir_item_em_versao_antiga() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS (SELECT 1 FROM orcamento_versao v WHERE v.id=NEW.orcamento_versao_id AND v.oficina_id=NEW.oficina_id AND v.criacao_tx=pg_current_xact_id()) THEN
  RAISE EXCEPTION 'Versao selada: crie uma nova versao' USING ERRCODE='23000';
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER itens_somente_na_criacao BEFORE INSERT ON item_orcamento
 FOR EACH ROW EXECUTE FUNCTION impedir_item_em_versao_antiga();

-- Validação diferida: os itens podem ser inseridos após a versão, na mesma transação.
CREATE FUNCTION validar_total_orcamento() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE valor numeric(19,2); quantidade_itens bigint;
BEGIN
 SELECT coalesce(sum(round(i.quantidade*i.valor_unitario,2)),0), count(*)
 INTO valor,quantidade_itens FROM item_orcamento i
 WHERE i.orcamento_versao_id=NEW.id AND i.oficina_id=NEW.oficina_id;
 IF quantidade_itens=0 OR valor<>NEW.total THEN
  RAISE EXCEPTION 'Itens e total inconsistentes' USING ERRCODE='23000';
 END IF;
 RETURN NEW;
END;
$$;
CREATE CONSTRAINT TRIGGER conferir_total AFTER INSERT ON orcamento_versao
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validar_total_orcamento();
