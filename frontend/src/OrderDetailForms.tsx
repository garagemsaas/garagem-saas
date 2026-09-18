// Formulários da tela de ordem de serviço: checklist de entrada, nova versão de orçamento e envio
// de foto. Saíram de OrderDetail.tsx, que passava de mil linhas — cada um é autocontido, com o
// próprio estado, e só recebe o que precisa por props. Nada de comportamento mudou.
import { TableRegion } from './TableRegion';
import { useState } from "react";
import {
  money,
  now,
  number,
  uid,
  val,
} from "./model";
import type {
  Order,
  Version,
  BudgetItem,
  CheckItem,
} from "./model";
import { Field } from "./ui";
import { AddButton, Form } from "./forms";

export function BudgetTable({ version }: { version: Version }) {
  return (
    <>
      <TableRegion label="Itens registrados">
        <table className="budget-table">
          <thead>
            <tr>
              <th scope="col">Item</th>
              <th scope="col">Tipo</th>
              <th scope="col" className="numeric">Qtd.</th>
              <th scope="col" className="numeric">Valor unitário</th>
              <th scope="col" className="numeric">Subtotal</th>
            </tr>
          </thead>
          <tbody>
            {version.itens.map((i) => (
              <tr key={i.id}>
                <td data-label="Item">
                  <strong>{i.descricao}</strong>
                </td>
                <td data-label="Tipo">{i.tipo === "PECA" ? "Peça" : "Serviço"}</td>
                <td data-label="Quantidade" className="numeric">{number(i.quantidade)}</td>
                <td data-label="Valor unitário" className="numeric">{money(i.valorUnitario)}</td>
                <td data-label="Subtotal" className="numeric">{money(i.subtotal)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </TableRegion>
      <div className="budget-total">
        <span>
          Total do orçamento{" "}
          <small>
            Versão {version.numero} · {version.itens.length} itens
          </small>
        </span>
        <strong>{money(version.total)}</strong>
      </div>
      {version.observacoes && (
        <div className="budget-notes">
          <small>Observações</small>
          <p>{version.observacoes}</p>
        </div>
      )}
    </>
  );
}

export function ChecklistForm({
  save,
  close,
}: {
  save: (c: NonNullable<Order["checklist"]>) => void | Promise<void>;
  close: () => void | Promise<void>;
}) {
  const [itens, setItens] = useState<CheckItem[]>([
    { id: uid(), descricao: "", condicao: "", observacao: "" },
  ]);
  const edit = (id: string, field: keyof CheckItem, value: string) =>
    setItens(itens.map((i) => (i.id === id ? { ...i, [field]: value } : i)));
  return (
    <Form
      close={close}
      submit="Registrar checklist"
      confirmation="O checklist preserva as condições de entrada e não poderá ser editado. Confira os itens antes de confirmar."
      note="Confira os dados antes de registrar. O checklist de entrada é único e não poderá ser editado."
      save={(f) => {
        if (itens.some((i) => !i.descricao.trim() || !i.condicao.trim()))
          throw Error("Informe a descrição e a condição de cada item.");
        return save({ itens, observacoes: val(f, "observacoes") });
      }}
    >
      {itens.map((i, n) => (
        <fieldset className="item-editor" key={i.id}>
          <legend>Item {n + 1}</legend>
          <Field label="Descrição *">
            <input
              required
              maxLength={200}
              name={`itens[${n}].descricao`}
              value={i.descricao}
              onChange={(e) => edit(i.id, "descricao", e.target.value)}
              placeholder="Ex.: pneus, carroceria, combustível"
            />
          </Field>
          <Field label="Condição *">
            <input
              required
              maxLength={100}
              name={`itens[${n}].condicao`}
              value={i.condicao}
              onChange={(e) => edit(i.id, "condicao", e.target.value)}
              placeholder="Ex.: bom estado"
            />
          </Field>
          <Field label="Observação">
            <input
              maxLength={1000}
              name={`itens[${n}].observacao`}
              value={i.observacao}
              onChange={(e) => edit(i.id, "observacao", e.target.value)}
            />
          </Field>
          {itens.length > 1 && (
            <button
              type="button"
              className="text-button danger"
              onClick={() => setItens(itens.filter((item) => item.id !== i.id))}
            >
              Remover item
            </button>
          )}
        </fieldset>
      ))}
      {itens.length < 100 && (
        <AddButton
          onClick={() =>
            setItens([
              ...itens,
              { id: uid(), descricao: "", condicao: "", observacao: "" },
            ])
          }
        >
          Adicionar item
        </AddButton>
      )}
      <Field label="Observações da entrada">
        <textarea name="observacoes" maxLength={4000} rows={3} />
      </Field>
    </Form>
  );
}
export function VersionForm({
  previous,
  save,
  close,
}: {
  previous?: Version;
  save: (v: Version) => void | Promise<void>;
  close: () => void | Promise<void>;
}) {
  const emptyItem = (): BudgetItem => ({
    id: uid(),
    tipo: "PECA",
    descricao: "",
    quantidade: 1,
    valorUnitario: 0,
    subtotal: 0,
  });
  const [itens, setItens] = useState<BudgetItem[]>(
    previous?.itens.map((i) => ({ ...i, id: uid() })) || [emptyItem()],
  );
  const subtotal = (i: BudgetItem) =>
    Math.round((i.quantidade * i.valorUnitario + Number.EPSILON) * 100) / 100;
  const edit = (id: string, patch: Partial<BudgetItem>) =>
    setItens(itens.map((i) => (i.id === id ? { ...i, ...patch } : i)));
  const total =
    Math.round(itens.reduce((s, i) => s + subtotal(i), 0) * 100) / 100;
  return (
    <Form
      close={close}
      submit={`Criar versão ${(previous?.numero || 0) + 1}`}
      confirmation={`Registrar esta versão de ${money(total)}? Os valores serão preservados e qualquer correção exigirá uma nova versão.`}
      note="Cada versão preserva seus itens e valores. Ao criar uma nova, a OS volta à etapa Orçamento e precisa ser disponibilizada novamente ao cliente."
      save={(f) => {
        if (itens.some((i) => !i.descricao.trim()))
          throw Error("Descreva todos os itens.");
        return save({
          id: uid(),
          numero: (previous?.numero || 0) + 1,
          criadoEm: now(),
          observacoes: val(f, "observacoes"),
          itens: itens.map((i) => ({ ...i, subtotal: subtotal(i) })),
          total,
        });
      }}
    >
      {itens.map((i, n) => (
        <fieldset className="item-editor" key={i.id}>
          <legend>Item {n + 1}</legend>
          <div className="form-grid">
            <Field label="Tipo">
              <select
                name={`itens[${n}].tipo`}
                value={i.tipo}
                onChange={(e) =>
                  edit(i.id, { tipo: e.target.value as BudgetItem["tipo"] })
                }
              >
                <option value="PECA">Peça</option>
                <option value="SERVICO">Serviço</option>
              </select>
            </Field>
            <Field label="Descrição *">
              <input
                required
                maxLength={500}
                name={`itens[${n}].descricao`}
                value={i.descricao}
                onChange={(e) => edit(i.id, { descricao: e.target.value })}
              />
            </Field>
          </div>
          <div className="form-grid">
            <Field label="Quantidade *">
              <input
                type="number"
                min="0.001"
                name={`itens[${n}].quantidade`}
                max="999999.999"
                step="0.001"
                required
                value={i.quantidade}
                onChange={(e) =>
                  edit(i.id, { quantidade: Number(e.target.value) })
                }
              />
            </Field>
            <Field label="Valor unitário (R$) *">
              <input
                type="number"
                min="0"
                name={`itens[${n}].valorUnitario`}
                max="99999999.99"
                step="0.01"
                required
                value={i.valorUnitario}
                onChange={(e) =>
                  edit(i.id, { valorUnitario: Number(e.target.value) })
                }
              />
            </Field>
          </div>
          <div className="section-heading">
            <strong>{money(subtotal(i))}</strong>
            {itens.length > 1 && (
              <button
                type="button"
                className="text-button danger"
                onClick={() =>
                  setItens(itens.filter((item) => item.id !== i.id))
                }
              >
                Remover item
              </button>
            )}
          </div>
        </fieldset>
      ))}
      {itens.length < 100 && (
        <AddButton onClick={() => setItens([...itens, emptyItem()])}>
          Adicionar item
        </AddButton>
      )}
      <Field label="Observações">
        <textarea
          name="observacoes"
          maxLength={4000}
          defaultValue={previous?.observacoes}
          rows={3}
        />
      </Field>
      <div className="budget-total">
        <span>Total da nova versão</span>
        <strong>{money(total)}</strong>
      </div>
    </Form>
  );
}
export function PhotoForm({
  order,
  save,
  close,
}: {
  order: Order;
  save: (p: FormData) => void | Promise<void>;
  close: () => void | Promise<void>;
}) {
  const [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  return (
    <form
      onSubmit={async (e) => {
        e.preventDefault();
        if (busy) return;
        const f = new FormData(e.currentTarget);
        const file = f.get("arquivo") as File;
        setError("");
        if (
          !file.size ||
          file.size > 10 * 1024 * 1024 ||
          !["image/png", "image/jpeg"].includes(file.type)
        ) {
          setError("Selecione uma imagem PNG ou JPEG de até 10 MB.");
          return;
        }
        setBusy(true);
        try {
          const bitmap = await createImageBitmap(file).catch(() => { throw new Error('Não foi possível ler esta imagem. Selecione um arquivo PNG ou JPEG válido.'); });
          const pixels = bitmap.width * bitmap.height;
          bitmap.close();
          if (pixels > 20_000_000)
            throw Error("A imagem deve ter até 20 megapixels.");
          const upload = new FormData();
          upload.set('arquivo', file); upload.set('finalidade', val(f, 'finalidade')); upload.set('descricao', val(f, 'descricao'));
          const item = val(f, 'vinculo');
          if (item) upload.set(order.checklist?.itens.some(i => i.id === item) ? 'checklistItemId' : 'diagnosticoItemId', item);
          await save(upload);
        } catch (err) {
          setError(
            err instanceof Error
              ? err.message
              : "Não foi possível ler esta imagem.",
          );
        } finally {
          setBusy(false);
        }
      }}
    >
      <p className="form-note">
        PNG ou JPEG, até 10 MB e 20 megapixels. A foto será armazenada de forma privada na oficina.
      </p>
      <Field label="Foto *">
        <input
          name="arquivo"
          type="file"
          accept="image/png,image/jpeg"
          required
        />
      </Field>
      <Field label="Finalidade *">
        <select name="finalidade">
          <option value="ENTRADA">Entrada</option>
          <option value="DIAGNOSTICO">Diagnóstico</option>
          <option value="SERVICO">Serviço</option>
        </select>
      </Field>
      <Field label="Descrição">
        <textarea name="descricao" maxLength={500} rows={3} />
      </Field>
      <Field label="Vincular a um item">
        <select name="vinculo">
          <option value="">Sem vínculo específico</option>
          {order.checklist?.itens.map((i) => (
            <option key={i.id} value={i.id}>
              Checklist · {i.descricao}
            </option>
          ))}
          {order.diagnosticos.map((i) => (
            <option key={i.id} value={i.id}>
              Diagnóstico · {i.descricao}
            </option>
          ))}
        </select>
      </Field>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      <div className="form-actions">
        <button type="button" onClick={close}>
          Cancelar
        </button>
        <button className="primary" disabled={busy}>
          {busy ? "Validando imagem…" : "Adicionar foto"}
        </button>
      </div>
    </form>
  );
}
