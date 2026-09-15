import { useRef, useState } from "react";
import {
  date,
  money,
  now,
  number,
  statuses,
  transitions,
  uid,
  val,
} from "./model";
import type {
  Order,
  Client,
  Vehicle,
  User,
  Role,
  Version,
  BudgetItem,
  CheckItem,
  Classification,
  Photo,
} from "./model";
import { Badge, Drawer, Empty, Field, Icon } from "./ui";
import { AddButton, Form } from "./forms";
import { api, ApiError, diagnosisToApi, loadOrder } from "./api";
import PrivatePhoto from "./PrivatePhoto";

const tabs = [
  "Resumo",
  "Checklist",
  "Diagnóstico",
  "Orçamento",
  "Fotos",
  "Timeline",
];
export function BudgetTable({ version }: { version: Version }) {
  return (
    <>
      <div className="table-scroll">
        <table className="budget-table">
          <thead>
            <tr>
              <th>Item</th>
              <th>Tipo</th>
              <th className="numeric">Qtd.</th>
              <th className="numeric">Valor unitário</th>
              <th className="numeric">Subtotal</th>
            </tr>
          </thead>
          <tbody>
            {version.itens.map((i) => (
              <tr key={i.id}>
                <td>
                  <strong>{i.descricao}</strong>
                </td>
                <td>{i.tipo === "PECA" ? "Peça" : "Serviço"}</td>
                <td className="numeric">{number(i.quantidade)}</td>
                <td className="numeric">{money(i.valorUnitario)}</td>
                <td className="numeric">{money(i.subtotal)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
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

function ChecklistForm({
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
              value={i.descricao}
              onChange={(e) => edit(i.id, "descricao", e.target.value)}
              placeholder="Ex.: pneus, carroceria, combustível"
            />
          </Field>
          <Field label="Condição *">
            <input
              required
              maxLength={100}
              value={i.condicao}
              onChange={(e) => edit(i.id, "condicao", e.target.value)}
              placeholder="Ex.: bom estado"
            />
          </Field>
          <Field label="Observação">
            <input
              maxLength={1000}
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
function VersionForm({
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
function PhotoForm({
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
          const bitmap = await createImageBitmap(file);
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

export default function OrderDetail({
  order,
  vehicle,
  client,
  users,
  role,
  update,
  back,
  notify,
}: {
  order: Order;
  vehicle: Vehicle;
  client: Client;
  users: User[];
  role: Role;
  update: (o: Order) => void | Promise<void>;
  back: () => void | Promise<void>;
  notify: (s: string) => void | Promise<void>;
}) {
  const [tab, setTab] = useState("Resumo"),
    [panel, setPanel] = useState(""),
    [versionId, setVersionId] = useState(""),
    [photo, setPhoto] = useState<Photo>(),
    [error, setError] = useState("");
  const saving = useRef(false);
  const [busy, setBusy] = useState(false);
  const latest = order.versoes.at(-1),
    version = order.versoes.find((v) => v.id === versionId) || latest;
  const editable = order.status !== "PRONTO",
    office = role !== "MECANICO",
    diagnosis = role !== "ATENDENTE";
  async function perform(action: () => Promise<unknown>, event: string, revoke = false) {
    if (saving.current) return;
    saving.current = true; setBusy(true); setError('');
    try {
      await action();
      const fresh = await loadOrder(order.id);
      await update({ ...fresh, link: revoke ? undefined : order.link });
      setPanel(''); notify(event);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Não foi possível salvar.');
      if (e instanceof ApiError && e.status === 409) {
        try { await update({ ...await loadOrder(order.id), link: order.link }); } catch { /* Keep the original conflict visible. */ }
      }
    } finally { saving.current = false; setBusy(false); }
  }
  async function change(patch: Partial<Order>, event: string) {
    const path = `/ordens-servico/${order.id}`;
    return perform(async () => {
      if (patch.checklist) await api(`${path}/checklist`, 'POST', { observacoes: patch.checklist.observacoes, itens: patch.checklist.itens.map(({ descricao, condicao, observacao }) => ({ descricao, condicao, observacao })) });
      else if (patch.diagnosticos) {
        const d = patch.diagnosticos.at(-1)!;
        await api(`${path}/diagnosticos`, 'POST', { descricao: d.descricao, classificacao: diagnosisToApi[d.classificacao] });
      } else if (patch.versoes) {
        const v = patch.versoes.at(-1)!;
        await api(`${path}/orcamento/versoes`, 'POST', { observacoes: v.observacoes, itens: v.itens.map(({ tipo, descricao, quantidade, valorUnitario }) => ({ tipo, descricao, quantidade, valorUnitario })) });
        setVersionId('');
      } else if (patch.mecanicoId) await api(`${path}/responsavel`, 'PUT', { mecanicoId: patch.mecanicoId, revisao: order.revisao });
      else if (patch.status) await api(`${path}/status`, 'POST', { status: patch.status, revisao: order.revisao });
    }, event);
  }
  async function createLink() {
    if (saving.current) return;
    saving.current = true; setBusy(true); setError('');
    try {
      const link = await api<NonNullable<Order['link']>>(`/ordens-servico/${order.id}/links`, 'POST');
      await update({ ...await loadOrder(order.id), link: { ...link, ativo: true } });
      notify('Link criado. Copie o endereço abaixo para compartilhar com o cliente.');
    } catch (e) { setError(e instanceof Error ? e.message : 'Falha ao gerar link.'); }
    finally { saving.current = false; setBusy(false); }
  }
  const canVersion =
    office && ["ORCAMENTO", "AGUARDANDO_APROVACAO"].includes(order.status);
  return (
    <>
      {error && <p role="alert" className="error">{error}</p>}
      {busy && <p role="status">Salvando…</p>}
      <button className="back-link" onClick={back}>
        <Icon name="back" size={16} />
        Ordens de Serviço
      </button>
      <div className="page-heading">
        <div>
          <div className="eyebrow">ORDEM DE SERVIÇO</div>
          <h1>
            OS #{order.numero} <Badge status={order.status} />
          </h1>
          <p>
            {vehicle.marca} {vehicle.modelo}{" "}
            <span className="separator">/</span>{" "}
            <span className="plate">{vehicle.placa}</span>
          </p>
        </div>
        {editable && (
          <button onClick={() => setPanel("status")}>
            Atualizar status <Icon name="arrow" size={16} />
          </button>
        )}
      </div>
      <section className="identity-strip">
        <div>
          <small>Cliente</small>
          <strong>{client.nome}</strong>
        </div>
        <div>
          <small>Quilometragem</small>
          <strong>{number(order.kmEntrada)} km</strong>
        </div>
        <div>
          <small>Entrada</small>
          <strong>{date(order.criadoEm, true)}</strong>
        </div>
        <div>
          <small>Previsão de entrega</small>
          <strong>{date(order.previsaoEntrega, true)}</strong>
        </div>
        <div>
          <small>Responsável</small>
          <strong>
            {users.find((u) => u.id === order.mecanicoId)?.nome ||
              "Não atribuído"}
          </strong>
        </div>
      </section>
      <div
        className="tabs"
        role="tablist"
        aria-label="Áreas da ordem de serviço"
      >
        {tabs.map((t, i) => (
          <button
            key={t}
            role="tab"
            aria-selected={tab === t}
            aria-controls="os-panel"
            id={`tab-${i}`}
            tabIndex={tab === t ? 0 : -1}
            className={tab === t ? "active" : ""}
            onClick={() => setTab(t)}
            onKeyDown={(e) => {
              if (["ArrowRight", "ArrowLeft", "Home", "End"].includes(e.key)) {
                e.preventDefault();
                const next =
                  e.key === "Home"
                    ? 0
                    : e.key === "End"
                      ? tabs.length - 1
                      : (i + (e.key === "ArrowRight" ? 1 : -1) + tabs.length) %
                        tabs.length;
                setTab(tabs[next]);
                document.getElementById(`tab-${next}`)?.focus();
              }
            }}
          >
            {t}
            {t === "Fotos" && order.fotos.length > 0 && (
              <span className="count">{order.fotos.length}</span>
            )}
          </button>
        ))}
      </div>
      <section
        className="detail-panel"
        id="os-panel"
        role="tabpanel"
        aria-labelledby={`tab-${tabs.indexOf(tab)}`}
      >
        {tab === "Resumo" && (
          <div className="summary-grid">
            <div>
              <div className="section-heading">
                <h2>Relato do cliente</h2>
              </div>
              <p className="report">{order.relato}</p>
              <hr />
              <h2>Veículo</h2>
              <dl className="definition-grid">
                <div>
                  <dt>Marca e modelo</dt>
                  <dd>
                    {vehicle.marca} {vehicle.modelo}
                  </dd>
                </div>
                <div>
                  <dt>Ano / cor</dt>
                  <dd>
                    {vehicle.ano} / {vehicle.cor}
                  </dd>
                </div>
                <div>
                  <dt>Placa</dt>
                  <dd>
                    <span className="plate">{vehicle.placa}</span>
                  </dd>
                </div>
              </dl>
              <h2>Contato do cliente</h2>
              <dl className="definition-grid">
                <div>
                  <dt>Telefone</dt>
                  <dd>{client.telefone}</dd>
                </div>
                <div>
                  <dt>E-mail</dt>
                  <dd>{client.email || "Não informado"}</dd>
                </div>
              </dl>
            </div>
            <aside className="work-summary">
              <div className="section-heading">
                <h2>Andamento</h2>
              </div>
              <Badge status={order.status} />
              <p>
                {order.status === "AGUARDANDO_APROVACAO"
                  ? "A manutenção começa após a aprovação do cliente pelo link do orçamento."
                  : order.status === "PRONTO"
                    ? "Serviço concluído. Esta ordem está disponível para consulta."
                    : "Mantenha o status atualizado conforme o trabalho avança na oficina."}
              </p>
              {latest && (
                <>
                  <small>Orçamento atual · v{latest.numero}</small>
                  <strong className="summary-total">
                    {money(latest.total)}
                  </strong>
                  <button
                    className="text-button"
                    onClick={() => setTab("Orçamento")}
                  >
                    Ver orçamento <Icon name="arrow" size={15} />
                  </button>
                </>
              )}
              <hr />
              <small>Mecânico responsável</small>
              <strong>
                {users.find((u) => u.id === order.mecanicoId)?.nome ||
                  "Não atribuído"}
              </strong>
              {office && editable && (
                <button
                  className="text-button"
                  onClick={() => setPanel("responsavel")}
                >
                  Alterar responsável
                </button>
              )}
            </aside>
          </div>
        )}
        {tab === "Checklist" && (
          <>
            <div className="section-heading">
              <div>
                <h2>Checklist de entrada</h2>
                <p>Condições do veículo no recebimento.</p>
              </div>
              {!order.checklist && editable && (
                <button
                  className="primary"
                  onClick={() => setPanel("checklist")}
                >
                  <Icon name="plus" size={16} />
                  Registrar checklist
                </button>
              )}
            </div>
            {order.checklist ? (
              <>
                <div className="table-scroll">
                  <table>
                    <thead>
                      <tr>
                        <th>Item</th>
                        <th>Condição</th>
                        <th>Observação</th>
                      </tr>
                    </thead>
                    <tbody>
                      {order.checklist.itens.map((i) => (
                        <tr key={i.id}>
                          <td>
                            <strong>{i.descricao}</strong>
                          </td>
                          <td>{i.condicao}</td>
                          <td>{i.observacao || "—"}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <div className="budget-notes">
                  <small>Observações da entrada</small>
                  <p>
                    {order.checklist.observacoes ||
                      "Nenhuma observação adicional."}
                  </p>
                </div>
                <p className="muted">
                  Checklist registrado. Este documento preserva as condições
                  informadas na entrada.
                </p>
              </>
            ) : (
              <Empty title="Checklist ainda não registrado">
                Registre as condições de entrada do veículo.
              </Empty>
            )}
          </>
        )}
        {tab === "Diagnóstico" && (
          <>
            <div className="section-heading">
              <div>
                <h2>Diagnóstico do veículo</h2>
                <p>Itens avaliados e recomendação técnica.</p>
              </div>
              {diagnosis && editable && (
                <button
                  className="primary"
                  onClick={() => setPanel("diagnostico")}
                >
                  <Icon name="plus" size={16} />
                  Adicionar item
                </button>
              )}
            </div>
            {order.diagnosticos.length ? (
              <div className="diagnosis-list">
                {order.diagnosticos.map((d) => (
                  <article key={d.id}>
                    <span
                      className={`classification ${d.classificacao.toLowerCase()}`}
                    >
                      <Icon name={d.classificacao === "OK" ? "good" : d.classificacao === "TROCAR" ? "critical" : "warning"} size={15} />
                      {d.classificacao === "OK"
                        ? "OK"
                        : d.classificacao === "TROCAR"
                          ? "Trocar"
                          : "Acompanhar"}
                    </span>
                    <div>
                      <p>{d.descricao}</p>
                      <small>{date(d.criadoEm, true)}</small>
                    </div>
                  </article>
                ))}
              </div>
            ) : (
              <Empty title="Nenhum diagnóstico registrado">
                Os itens avaliados pelo mecânico aparecerão aqui.
              </Empty>
            )}
          </>
        )}
        {tab === "Orçamento" && (
          <>
            <div className="section-heading">
              <div>
                <h2>Orçamento</h2>
                <p>Peças, serviços e histórico de aprovação.</p>
              </div>
              {canVersion && (
                <button onClick={() => setPanel("versao")}>
                  <Icon name="plus" size={16} />
                  {latest ? "Nova versão" : "Criar orçamento"}
                </button>
              )}
            </div>
            {version ? (
              <>
                <div className="version-bar">
                  <Field label="Versão do orçamento">
                    <select
                      value={version.id}
                      onChange={(e) => setVersionId(e.target.value)}
                    >
                      {[...order.versoes].reverse().map((v) => (
                        <option key={v.id} value={v.id}>
                          Versão {v.numero}
                          {v.id === latest?.id
                            ? " · Atual"
                            : " · Anterior"} — {date(v.criadoEm, true)}
                        </option>
                      ))}
                    </select>
                  </Field>
                  <span
                    className={`decision-label ${version.decisao ? (version.decisao.aprovado ? "approved" : "rejected") : ""}`}
                  >
                    {version.decisao
                      ? version.decisao.aprovado
                        ? "Aprovado pelo cliente"
                        : "Recusado pelo cliente"
                      : version.id !== latest?.id
                        ? "Substituído"
                        : order.status === "AGUARDANDO_APROVACAO"
                          ? "Aguardando aprovação"
                          : "Não disponibilizado"}
                  </span>
                </div>
                <BudgetTable version={version} />
                {version.decisao && (
                  <p className="decision-proof">
                    Decisão registrada em {date(version.decisao.criadoEm, true)}{" "}
                    · Canal: link público
                  </p>
                )}
                {office && (
                  <div className="approval-box">
                    <div>
                      <h3>Aprovação do cliente</h3>
                      <p>
                        {order.status === "ORCAMENTO"
                          ? "Disponibilize a versão atual para solicitar uma nova decisão."
                          : "O cliente visualiza os itens e decide sobre o valor integral."}
                      </p>
                      {order.link?.ativo && (
                        <small>
                          Link disponível até{" "}
                          {date(order.link.expiraEm, true)}.
                        </small>
                      )}
                    </div>
                    <div className="button-stack">
                      {order.status === "ORCAMENTO" &&
                        latest &&
                        !latest.decisao && (
                          <button
                            className="primary"
                            onClick={() =>
                              change(
                                { status: "AGUARDANDO_APROVACAO" },
                                "Orçamento disponibilizado para aprovação.",
                              )
                            }
                          >
                            Disponibilizar orçamento
                          </button>
                        )}
                      {!order.link?.ativo ? (
                        <button disabled={busy} onClick={createLink}>Gerar link do cliente</button>
                      ) : (
                        <>
                          <a href={order.link.url} target="_blank" rel="noreferrer">Visualizar como cliente</a>
                          <Field label="Link do cliente"><input readOnly value={order.link.url || ''} onFocus={e => e.target.select()} /></Field>
                          <button
                            className="text-button danger"
                            onClick={() => setPanel("revogar")}
                          >
                            Revogar link
                          </button>
                        </>
                      )}
                    </div>
                  </div>
                )}
              </>
            ) : (
              <Empty title="Orçamento ainda não criado">
                {canVersion
                  ? "Inclua peças e serviços para preparar a primeira versão."
                  : "O orçamento pode ser criado quando a OS estiver na etapa Orçamento."}
              </Empty>
            )}
          </>
        )}
        {tab === "Fotos" && (
          <>
            <div className="section-heading">
              <div>
                <h2>Fotos do veículo</h2>
                <p>Registros de entrada, diagnóstico e serviço.</p>
              </div>
              {editable && (
                <button className="primary" onClick={() => setPanel("foto")}>
                  <Icon name="plus" size={16} />
                  Adicionar foto
                </button>
              )}
            </div>
            {order.fotos.length ? (
              <div className="photo-grid">
                {order.fotos.map((p) => (
                  <button
                    className="photo-card"
                    key={p.id}
                    onClick={() => setPhoto(p)}
                  >
                    <PrivatePhoto
                      path={p.url}
                      alt={
                        p.descricao || `Foto de ${p.finalidade.toLowerCase()}`
                      }
                    />
                    <span>
                      <small>
                        {p.finalidade === "ENTRADA"
                          ? "Entrada"
                          : p.finalidade === "DIAGNOSTICO"
                            ? "Diagnóstico"
                            : "Serviço"}
                      </small>
                      <strong>{p.descricao || "Sem descrição"}</strong>
                      {p.vinculo && (
                        <small>
                          Item:{" "}
                          {order.checklist?.itens.find(
                            (i) => i.id === p.vinculo,
                          )?.descricao ||
                            order.diagnosticos.find((i) => i.id === p.vinculo)
                              ?.descricao}
                        </small>
                      )}
                    </span>
                  </button>
                ))}
              </div>
            ) : (
              <Empty title="Nenhuma foto adicionada">
                Adicione uma imagem para avaliar a galeria e a visualização
                ampliada.
              </Empty>
            )}
          </>
        )}
        {tab === "Timeline" && (
          <>
            <div className="section-heading">
              <div>
                <h2>Histórico da OS</h2>
                <p>Eventos em ordem cronológica, do recebimento à conclusão.</p>
              </div>
            </div>
            <ol className="timeline">
              {[...order.timeline]
                .sort((a, b) => a.criadoEm.localeCompare(b.criadoEm))
                .map((e) => (
                  <li key={e.id}>
                    <time>{date(e.criadoEm, true)}</time>
                    <div>
                      <strong>{e.descricao}</strong>
                      <small>{e.origem}</small>
                    </div>
                  </li>
                ))}
            </ol>
          </>
        )}
      </section>
      {panel === "checklist" && (
        <Drawer error={error}
          title="Registrar checklist de entrada"
          close={() => setPanel("")}
        >
          <ChecklistForm
            close={() => setPanel("")}
            save={(checklist) =>
              change({ checklist }, "Checklist de entrada registrado.")
            }
          />
        </Drawer>
      )}
      {panel === "diagnostico" && (
        <Drawer error={error} title="Adicionar diagnóstico" close={() => setPanel("")}>
          <Form
            close={() => setPanel("")}
            submit="Registrar item"
            save={(f) => {
              if (!val(f, "descricao")) throw Error("Descreva o diagnóstico.");
              return change(
                {
                  diagnosticos: [
                    ...order.diagnosticos,
                    {
                      id: uid(),
                      descricao: val(f, "descricao"),
                      classificacao: val(f, "classificacao") as Classification,
                      criadoEm: now(),
                    },
                  ],
                },
                "Item de diagnóstico registrado.",
              );
            }}
          >
            <Field label="Avaliação técnica *">
              <textarea name="descricao" required maxLength={4000} rows={5} />
            </Field>
            <Field label="Classificação *">
              <select name="classificacao">
                <option value="OK">OK — sem intervenção</option>
                <option value="ACOMPANHAR">
                  Acompanhar — observar desgaste
                </option>
                <option value="TROCAR">
                  Trocar — substituição recomendada
                </option>
              </select>
            </Field>
          </Form>
        </Drawer>
      )}
      {panel === "versao" && (
        <Drawer error={error}
          title={`Orçamento · versão ${(latest?.numero || 0) + 1}`}
          wide
          close={() => setPanel("")}
        >
          <VersionForm
            previous={latest}
            close={() => setPanel("")}
            save={(v) => {
              setVersionId(v.id);
              return change(
                { versoes: [...order.versoes, v], status: "ORCAMENTO" },
                'Orçamento salvo. Consulte os valores da versão registrada.',
              );
            }}
          />
        </Drawer>
      )}
      {panel === "foto" && (
        <Drawer error={error} title="Adicionar foto" close={() => setPanel("")}>
          <PhotoForm
            order={order}
            close={() => setPanel("")}
            save={(upload) => perform(() => api(`/ordens-servico/${order.id}/fotos`, 'POST', upload), 'Foto adicionada à OS.')}
          />
        </Drawer>
      )}
      {panel === "responsavel" && (
        <Drawer error={error} title="Mecânico responsável" close={() => setPanel("")}>
          <Form
            close={() => setPanel("")}
            save={(f) =>
              change(
                { mecanicoId: val(f, "mecanicoId") },
                "Mecânico responsável atualizado.",
              )
            }
          >
            <Field label="Mecânico ativo *">
              <select
                name="mecanicoId"
                required
                defaultValue={order.mecanicoId || ""}
              >
                <option value="" disabled>
                  Selecione um mecânico
                </option>
                {users
                  .filter((u) => u.ativo && u.papel === "MECANICO")
                  .map((u) => (
                    <option value={u.id} key={u.id}>
                      {u.nome}
                    </option>
                  ))}
              </select>
            </Field>
          </Form>
        </Drawer>
      )}
      {panel === "status" && (
        <Drawer error={error} title="Atualizar status" close={() => setPanel("")}>
          <p className="form-note">
            Situação atual: <strong>{statuses[order.status]}</strong>
          </p>
          {order.status === "AGUARDANDO_APROVACAO" && (
            <p className="notice">
              A aprovação pelo cliente inicia a manutenção automaticamente. Para
              revisar o orçamento, volte à etapa anterior.
            </p>
          )}
          <Form
            close={() => setPanel("")}
            submit="Confirmar status"
            save={(f) => {
              const status = val(f, "status") as Order["status"];
              if (
                status === "AGUARDANDO_APROVACAO" &&
                (!latest || latest.decisao)
              )
                throw Error(
                  "Crie uma nova versão do orçamento antes de solicitar aprovação.",
                );
              return change({ status }, `Status alterado para ${statuses[status]}.`);
            }}
          >
            <Field label="Próxima etapa *">
              <select name="status" required>
                {transitions[order.status].map((s) => (
                  <option key={s} value={s}>
                    {statuses[s]}
                  </option>
                ))}
              </select>
            </Field>
            {order.status === "TESTE" && (
              <p className="notice">
                Ao marcar como Pronto, a OS será concluída e ficará disponível
                apenas para consulta.
              </p>
            )}
          </Form>
        </Drawer>
      )}
      {panel === "revogar" && (
        <Drawer error={error} title="Revogar link do cliente" close={() => setPanel("")}>
          <p>
            O link atual deixará de permitir a consulta e a decisão do cliente.
          </p>
          <div className="form-actions">
            <button onClick={() => setPanel("")}>Cancelar</button>
            <button
              className="danger-button"
              disabled={busy}
              onClick={async () => {
                await perform(() => api(`/ordens-servico/${order.id}/links/${order.link!.id}`, 'DELETE'), 'Link revogado.', true);
              }}
            >
              Revogar link
            </button>
          </div>
        </Drawer>
      )}
      {photo && (
        <Drawer error={error} title="Foto do veículo" wide close={() => setPhoto(undefined)}>
          <PrivatePhoto
            className="photo-full"
            path={photo.url}
            alt={photo.descricao || "Registro do veículo"}
          />
          <h3>{photo.descricao || "Sem descrição"}</h3>
          <p>{photo.finalidade.toLowerCase()}</p>
        </Drawer>
      )}
    </>
  );
}
