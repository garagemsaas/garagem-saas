import { TableRegion } from './TableRegion';
import './OrderDetail.css';
import { ChecklistForm, VersionForm, PhotoForm } from './OrderDetailForms';
import { useEffect, useRef, useState } from "react";
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
  Classification,
  Photo,
} from "./model";
import { Badge, Drawer, Empty, Field, Icon } from "./ui";
import { Form } from "./forms";
import { api, ApiError, diagnosisToApi, loadOrder } from "./api";
import PrivatePhoto from "./PrivatePhoto";
import { Timeline } from './Timeline';

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
  client?: Client;
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
  const [syncError, setSyncError] = useState('');
  const [checkedAt, setCheckedAt] = useState(() => Date.now());
  const linkExpired = Boolean(order.link && new Date(order.link.expiraEm).getTime() <= checkedAt);
  // Pause background reads while a form or write is active. Never replace a draft.
  useEffect(() => {
    if (panel || busy) return;
    let active = true;
    let reading = false;
    async function refresh() {
      if (reading || saving.current || document.visibilityState !== 'visible') return;
      reading = true;
      try {
        const fresh = await loadOrder(order.id);
        if (!active || saving.current) return;
        await update({ ...fresh, link: order.link });
        setCheckedAt(Date.now());
        setSyncError('');
      } catch {
        if (active) setSyncError('Não foi possível atualizar automaticamente. Os últimos dados continuam visíveis. Use Atualizar dados para tentar novamente.');
      } finally { reading = false; }
    }
    const timer = window.setInterval(() => { setCheckedAt(Date.now()); void refresh(); }, 15_000);
    const onVisible = () => { void refresh(); };
    window.addEventListener('focus', onVisible);
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      active = false;
      window.clearInterval(timer);
      window.removeEventListener('focus', onVisible);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [order.id, order.link, panel, busy, update]);
  const latest = order.versoes.at(-1),
    version = order.versoes.find((v) => v.id === versionId) || latest;
  const editable = order.status !== "PRONTO",
    office = role !== "MECANICO",
    diagnosis = role !== "ATENDENTE";
  async function perform(action: () => Promise<unknown>, event: string, revoke = false) {
    if (saving.current) return false;
    saving.current = true; setBusy(true); setError('');
    let committed = false;
    try {
      await action();
      committed = true;
      const fresh = await loadOrder(order.id);
      await update({ ...fresh, link: revoke ? undefined : order.link });
      setPanel(''); notify(event);
      return true;
    } catch (e) {
      if (committed) {
        setPanel(''); notify(event);
        setError('A alteração foi salva, mas os dados não puderam ser recarregados. Clique em Atualizar dados antes de continuar.');
        return true;
      }
      setError(e instanceof Error ? e.message : 'Não foi possível salvar.');
      if (e instanceof ApiError && e.status === 409) {
        try { await update({ ...await loadOrder(order.id), link: order.link }); } catch { /* Keep the original conflict visible. */ }
      }
      return false;
    } finally { saving.current = false; setBusy(false); }
  }
  async function change(patch: Partial<Order>, event: string) {
    const path = `/ordens-servico/${order.id}`;
    const saved = await perform(async () => {
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
    if (!saved) throw new Error('A alteração não foi salva. Confira o aviso e revise os dados antes de tentar novamente.');
  }
  async function createLink() {
    if (saving.current) return;
    saving.current = true; setBusy(true); setError('');
    try {
      const link = await api<NonNullable<Order['link']>>(`/ordens-servico/${order.id}/links`, 'POST');
      // Preserve the one-time token even if the following history refresh fails.
      const publicLink = { ...link, url: `${window.location.origin}/acompanhar#${encodeURIComponent(link.token ?? '')}`, ativo: true };
      await update({ ...order, link: publicLink });
      try { await update({ ...await loadOrder(order.id), link: publicLink }); } catch { setError('Link criado. Não foi possível atualizar o histórico; copie o link e atualize os dados.'); }
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
      {syncError && <p role="status" className="notice">{syncError}</p>}
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
      <p>{panel || busy ? 'Atualização automática pausada durante a edição.' : 'Esta OS é atualizada automaticamente a cada 15 segundos enquanto estiver aberta.'}</p>
      <section className="identity-strip">
        <div>
          <small>Cliente</small>
          <strong>{client?.nome || "Prepara??o interna da empresa"}</strong>
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
                  <dd>{client?.telefone || "?"}</dd>
                </div>
                <div>
                  <dt>E-mail</dt>
                  <dd>{client?.email || "Não informado"}</dd>
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
                <TableRegion label="Itens registrados">
                  <table>
                    <thead>
                      <tr>
                        <th scope="col">Item</th>
                        <th scope="col">Condição</th>
                        <th scope="col">Observação</th>
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
      </TableRegion>
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
                {office && order.tipo !== 'INTERNA' && (
                  <div className="approval-box">
                    <div>
                      <h3>Aprovação do cliente</h3>
                      <p>O envio é manual: disponibilize o orçamento, gere o link e compartilhe com o cliente. Nenhuma mensagem é enviada automaticamente.</p>
                      <p>
                        {order.status === "ORCAMENTO"
                          ? "Disponibilize a versão atual para solicitar uma nova decisão."
                          : "O cliente visualiza os itens e decide sobre o valor integral."}
                      </p>
                      {order.link?.ativo && !linkExpired && (
                        <small>
                          Link disponível até{" "}
                          {date(order.link.expiraEm, true)}.
                        </small>
                      )}
                      {linkExpired && <p className="notice">O link expirou. Gere um novo endereço para o cliente.</p>}
                      {!order.link && <p>Copie o endereço após gerar. Ele fica disponível nesta sessão; recarregar a página exige gerar outro link. Links anteriores continuam válidos até expirar ou serem revogados.</p>}
                    </div>
                    <div className="button-stack">
                      {order.status === "ORCAMENTO" &&
                        latest &&
                        !latest.decisao && (
                          <button
                            className="primary"
                            onClick={() => setPanel('disponibilizar')}
                          >
                            Disponibilizar orçamento
                          </button>
                        )}
                      {!order.link?.ativo || linkExpired ? (
<button disabled={busy} onClick={createLink}>
  Gerar link do cliente
</button>
                      ) : (
                        <>
                          <a href={order.link.url} target="_blank" rel="noreferrer">Visualizar como cliente</a>
                          <Field label="Link do cliente"><input readOnly value={order.link.url || ''} onFocus={e => e.target.select()} /></Field>
                          <button onClick={async () => {
                            try {
                              await navigator.clipboard.writeText(order.link?.url ?? '');
                              notify('Link copiado. Envie o endereço ao cliente.');
                            } catch { setError('Não foi possível copiar automaticamente. Selecione e copie o endereço no campo Link do cliente.'); }
                          }}>Copiar link</button>
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
                Registre as condições do veículo com fotos de entrada, diagnóstico ou serviço.
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
            <Timeline events={order.timeline} />
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
            save={async (upload) => { if (!await perform(() => api(`/ordens-servico/${order.id}/fotos`, 'POST', upload), 'Foto adicionada à OS.')) throw new Error('Não foi possível enviar a foto. Confira o aviso e tente novamente.'); }}
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
                {(order.tipo === "INTERNA" && order.status === "ORCAMENTO" ? ["EM_MANUTENCAO" as const] : transitions[order.status]).map((s) => (
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
      {panel === 'disponibilizar' && <Drawer title="Disponibilizar orçamento" close={() => setPanel('')} error={error}>
        <Form close={() => setPanel('')} submit="Confirmar disponibilização" save={() => change({ status: 'AGUARDANDO_APROVACAO' }, 'Orçamento disponibilizado para aprovação.')}>
          <p>Disponibilizar a versão {latest?.numero} de {money(latest?.total ?? 0)}? O cliente poderá aprovar ou recusar o valor integral pelo link público.</p>
        </Form>
      </Drawer>}
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
