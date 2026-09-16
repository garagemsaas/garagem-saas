import { TableRegion } from './TableRegion';
import { lazy, Suspense, useCallback, useEffect, useRef, useState } from "react";
import { Badge, Brand, Drawer, Empty, Field, Icon, Pager, Search } from "./ui";
import { ClientForm, OrderForm, UserForm, VehicleForm } from "./forms";
import OrderDetail from "./OrderDetail";
import { PageState } from "./PageState";
import Workspace from "./Workspace";
import { labels } from "./navigation";
import type { Page } from "./navigation";
import { getDashboard } from "./dashboard-model";
import { date, money, number, roles } from "./model";
import type { Client, Order, Vehicle } from "./model";
import { allPages, api, currentSession, emptyData, loadData, loadOrder, orderSummary, setApiSession } from "./api";
import type { Session } from "./api";
import "./App.css";
import "./design-system.css";

const Dashboard = lazy(() => import("./Dashboard"));

const subtitles: Record<Page, string> = {
  overview: "Sua oficina em perspectiva.",
  orders: "Do recebimento à entrega, acompanhe cada serviço.",
  clients: "Os contatos de quem confia o veículo à sua oficina.",
  vehicles: "Veículos cadastrados e seus proprietários.",
  team: "Pessoas e papéis de acesso à oficina.",
};
export default function App() {
  const [data, setData] = useState(emptyData);
  const [options, setOptions] = useState(emptyData);
  const [recoveryOrders, setRecoveryOrders] = useState<Order[]>([]);
  const [session, setSession] = useState<(Session & { role: Session['papel']; oficina: string }) | null>(null);
  const [busy, setBusy] = useState(false);
  const [dataError, setDataError] = useState('');
  const [detailLoading, setDetailLoading] = useState(false);
  const [dataLoading, setDataLoading] = useState(false);
  const [detailAttempt, setDetailAttempt] = useState(0);
  const loadSequence = useRef(0);
  const [reload, setReload] = useState(0);
  const [today, setToday] = useState(() => new Date());
  useEffect(() => {
    const timer = window.setInterval(() => setToday(new Date()), 60_000);
    return () => window.clearInterval(timer);
  }, []);
  const [page, setPage] = useState<Page>("overview"),
    [selected, setSelectedId] = useState(""),
    [query, setQuery] = useState(""),
    [pagination, setPagination] = useState(0);
  const [panel, setPanel] = useState(""),
    [client, setClient] = useState<Client>(),
    [vehicle, setVehicle] = useState<Vehicle>(),
    [toast, setToast] = useState("");
  const [showPassword, setShowPassword] = useState(false),
    [loginError, setLoginError] = useState("");
  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(""), 4500);
    return () => clearTimeout(timer);
  }, [toast]);
  useEffect(() => {
    const expired = () => { setSession(null); setData(emptyData()); setOptions(emptyData()); setRecoveryOrders([]); setClient(undefined); setVehicle(undefined); setToast(''); setSelectedId(''); setDetailLoading(false); setDataError(''); setPanel(''); setLoginError('Sessão expirada. Entre novamente.'); };
    const renewed = () => { const value = currentSession(); if (value) setSession(previous => previous ? { ...value, role: value.papel, oficina: previous.oficina } : null); };
    window.addEventListener('session-expired', expired);
    window.addEventListener('session-updated', renewed);
    return () => { window.removeEventListener('session-expired', expired); window.removeEventListener('session-updated', renewed); };
  }, []);
  function setSelected(value: string) {
    setSelectedId(value);
    if (value !== selected) setDetailLoading(Boolean(value));
    setDataError('');
  }
  function navigate(next: Page) {
    setPage(next);
    setSelected("");
    setQuery("");
    setPagination(0);
    setPanel("");
  }
  const notify = (s: string) => setToast(s);
  function openOrder(id: string) {
    navigate("orders");
    setSelected(id);
  }
  function finish(message: string) {
    setPanel("");
    notify(message);
  }
  async function logout() {
    const previous = currentSession();

    setApiSession(null); setSession(null); setData(emptyData());
    setOptions(emptyData()); setRecoveryOrders([]);
    setClient(undefined); setVehicle(undefined); navigate('orders'); setToast('');
    if (previous) {
      try { await api('/auth/logout', 'POST', { oficinaId: previous.oficinaId, refreshToken: previous.refreshToken }); }
      catch { setLoginError('Sessão local encerrada. Não foi possível revogar o acesso no servidor; tente entrar e sair novamente.'); }
    }
  }
  const user = session;
  const canWrite = session?.role !== "MECANICO";
  const orders = data.ordens;
  const clients = data.clientes;
  const vehicles = data.veiculos;
  const total = data.total;
  const order = data.ordens.find((o) => o.id === selected);
  const updateOrder = useCallback((o: Order) => {
    setData((d) => ({
      ...d,
      ordens: d.ordens.map((item) => (item.id === o.id ? o : item)),
    }));
  }, []);
  async function saveClient(c: Client) {
    const saved = await api<Client>(client ? `/clientes/${client.id}` : '/clientes', client ? 'PUT' : 'POST', { nome: c.nome, telefone: c.telefone, email: c.email || null, revisao: c.revisao });
    setData(d => ({ ...d, clientes: d.clientes.some(item => item.id === saved.id) ? d.clientes.map(item => item.id === saved.id ? saved : item) : [...d.clientes, saved] }));
    setClient(saved); finish('Cliente salvo.'); setReload(n => n + 1);
  }
  async function saveVehicle(v: Vehicle) {
    const saved = await api<Vehicle>(vehicle ? `/veiculos/${vehicle.id}` : '/veiculos', vehicle ? 'PUT' : 'POST', { clienteId: v.clienteId, placa: v.placa, marca: v.marca, modelo: v.modelo, ano: v.ano, km: v.km, cor: v.cor, revisao: v.revisao });
    setData(d => ({ ...d, veiculos: d.veiculos.some(item => item.id === saved.id) ? d.veiculos.map(item => item.id === saved.id ? saved : item) : [...d.veiculos, saved] }));
    setVehicle(saved); finish('Veículo salvo.'); setReload(n => n + 1);
  }
  const sessionId = session?.usuarioId;
  useEffect(() => {
    if (!sessionId || selected) return;
    let active = true;
    const sequence = ++loadSequence.current;
    // Synchronize the loading indicator with this API request's lifecycle.
    // oxlint-disable-next-line react/set-state-in-effect
    setDataLoading(true); setDataError('');
    const timer = window.setTimeout(() => {
      loadData(page, pagination, query).then(result => {
        if (active && sequence === loadSequence.current) setData(result);
      }).catch(error => { if (active) setDataError(error.message); })
        .finally(() => { if (active) setDataLoading(false); });
    }, query ? 300 : 0);
    return () => { active = false; window.clearTimeout(timer); };
  }, [sessionId, page, pagination, query, reload, selected]);
  const [optionsLoading, setOptionsLoading] = useState(false);
  const [optionsError, setOptionsError] = useState('');
  const [optionsAttempt, setOptionsAttempt] = useState(0);
  useEffect(() => {
    if (!sessionId || panel !== 'recovery') return;
    let active = true;
    // oxlint-disable-next-line react/set-state-in-effect -- API request lifecycle
    setOptionsLoading(true); setOptionsError('');
    allPages<Order>('/ordens-servico?status=AGUARDANDO_APROVACAO')
      .then(rows => Promise.all(rows.map(async o => ({ ...orderSummary(o), versoes: await api<Order['versoes']>(`/ordens-servico/${o.id}/orcamento/versoes`) }))))
      .then(ordens => { if (active) setRecoveryOrders(ordens); })
      .catch(error => { if (active) setOptionsError(error.message); })
      .finally(() => { if (active) setOptionsLoading(false); });
    return () => { active = false; };
  }, [sessionId, panel, optionsAttempt]);
  useEffect(() => {
    if (!sessionId || !['new-orders', 'new-vehicles', 'edit-vehicle'].includes(panel)) return;
    let active = true;
    // oxlint-disable-next-line react/set-state-in-effect -- API request lifecycle
    setOptionsLoading(true); setOptionsError('');
    Promise.all([allPages<Client>('/clientes'), panel === 'new-orders' ? allPages<Vehicle>('/veiculos') : Promise.resolve(null), panel === 'new-orders' ? allPages<import('./model').User>('/usuarios?ativo=true') : Promise.resolve(null)])
      .then(([clientes, veiculos, usuarios]) => { if (active) setOptions({ ...emptyData(), clientes, veiculos: veiculos ?? [], usuarios: usuarios ?? [] }); })
      .catch(error => { if (active) setOptionsError(error.message); })
      .finally(() => { if (active) setOptionsLoading(false); });
    return () => { active = false; };
  }, [sessionId, panel, optionsAttempt]);
  useEffect(() => {
    if (!sessionId || !selected) return;
    // oxlint-disable-next-line react/set-state-in-effect -- API request lifecycle
    setDataLoading(false); setDetailLoading(true);
    let active = true;
    loadOrder(selected).then(async o => {
      const [cliente, veiculo, usuarios] = await Promise.all([api<Client>(`/clientes/${o.clienteId}`), api<Vehicle>(`/veiculos/${o.veiculoId}`), allPages<import('./model').User>('/usuarios')]);
      if (active) setData(d => ({ ...d, clientes: [...d.clientes.filter(c => c.id !== cliente.id), cliente], veiculos: [...d.veiculos.filter(v => v.id !== veiculo.id), veiculo], usuarios, ordens: [...d.ordens.filter(item => item.id !== o.id), { ...o, link: d.ordens.find(item => item.id === o.id)?.link }] }));
    })
      .catch(e => { if (active) setDataError(e.message); })
      .finally(() => { if (active) setDetailLoading(false); });
    return () => { active = false; };
  }, [selected, sessionId, detailAttempt]);
  if (!session)
    return (
      <div className="login-page">
        <section className="login-story">
          <Brand />
          <div>
            <div className="eyebrow">A ROTINA DA OFICINA, ORGANIZADA.</div>
            <h1>
              Cada veículo.
              <br />
              Cada serviço.
              <br />
              Tudo no lugar.
            </h1>
            <p>
              Do primeiro relato à entrega das chaves, uma visão clara do
              trabalho da sua equipe.
            </p>
            <div className="login-process">
              <span>Receber</span>
              <span>Diagnosticar</span>
              <span>Executar</span>
              <span>Entregar</span>
            </div>
          </div>
          <small>Garagem SaaS · Núcleo operacional</small>
        </section>
        <main className="login-main">
          <div className="login-form">
            <span className="demo-label">GARAGEM · ACESSO DA OFICINA</span>
            <h2>Entre na sua oficina</h2>
            <p>Seu espaço de trabalho começa aqui.</p>
            <form
              onSubmit={async (e) => {
                e.preventDefault();
                const f = new FormData(e.currentTarget);
                const oficina = String(f.get("oficina")).trim();
                const email = String(f.get("email")).trim();
                const senha = String(f.get("senha"));
                if (!oficina) {
                  setLoginError("Informe a oficina.");
                  return;
                }
                if (busy) return;
                setBusy(true); setLoginError('');
                try {
                  const value = await api<Session>('/auth/login', 'POST', { oficina, email, senha });
                  setApiSession(value);
                  setSession({ ...value, role: value.papel, oficina });
                  navigate('overview');

                } catch (error) {
                  setApiSession(null); setData(emptyData());
                  setLoginError(error instanceof Error ? error.message : 'Não foi possível entrar.');
                } finally { setBusy(false); }

              }}
            >
              <Field
                label="Oficina"
                hint="Identificador da sua oficina."
              >
                <input
                  name="oficina"
                  required
                  maxLength={80}
                  autoComplete="organization"
                />
              </Field>
              <Field label="E-mail">
                <input
                  name="email"
                  type="email"
                  required
                  maxLength={254}
                  autoComplete="username"
                />
              </Field>
              <Field label="Senha">
                <span className="password-input">
                  <input
                    aria-label="Senha"
                    name="senha"
                    type={showPassword ? "text" : "password"}
                    required
                    maxLength={72}
                    autoComplete="current-password"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    aria-label={
                      showPassword ? "Ocultar senha" : "Mostrar senha"
                    }
                  >
                    {showPassword ? "Ocultar" : "Mostrar"}
                  </button>
                </span>
              </Field>
              {loginError && (
                <p className="error" role="alert">
                  {loginError}
                </p>
              )}
              <button type="submit" className="primary login-submit" disabled={busy}>
                {busy ? "Entrando…" : "Entrar"} <Icon name="arrow" size={18} />
              </button>

            </form>
          </div>
          <footer>Garagem SaaS · Português do Brasil</footer>
        </main>
      </div>
    );
  return (
    <Workspace page={page} navigate={navigate} role={session.role}
      name={user?.nome ?? "Usuário"} workshop={session.oficina}
      logout={logout} orders={data.ordens} clients={data.clientes} vehicles={data.veiculos} today={today}
      openOrder={openOrder}
      openClient={(c) => { navigate("clients"); setClient(c); setPanel("view-client"); }}
      openVehicle={(v) => { navigate("vehicles"); setVehicle(v); setPanel("view-vehicle"); }}
      openRecovery={() => setPanel("recovery")}>
          <button className="text-button" disabled={dataLoading || detailLoading} onClick={() => { setDataError(''); if (selected) { setDetailLoading(true); setDetailAttempt(n => n + 1); } else setReload(n => n + 1); }}>{dataLoading ? 'Atualizando…' : 'Atualizar dados'}</button>
          {dataLoading && !query ? <PageState state="loading" title="Carregando dados da oficina" /> : detailLoading ? <PageState state="loading" title="Carregando ordem de serviço" /> : dataError ? <PageState state="error" title="Não foi possível carregar os dados" retry={() => { setDataError(''); if (selected) { setDetailLoading(true); setDetailAttempt(n => n + 1); } else setReload(n => n + 1); }}>{dataError}</PageState> : page === "overview" ? <Suspense fallback={<PageState state="loading" title="Preparando sua visão geral" />}><Dashboard summary={data.dashboard} orders={data.ordens} clients={data.clientes} vehicles={data.veiculos}
            today={today} canWrite={canWrite} openOrder={openOrder}
            newOrder={() => { navigate("orders"); setPanel("new-orders"); }}
            viewOrders={() => navigate("orders")} viewRecovery={() => setPanel("recovery")} /></Suspense> :
          order && page === "orders" ? (
            <OrderDetail
              key={order.id}
              order={order}
              vehicle={data.veiculos.find((v) => v.id === order.veiculoId)!}
              client={data.clientes.find((c) => c.id === order.clienteId)!}
              users={data.usuarios}
              role={session.role}
              update={updateOrder}
              back={() => setSelected("")}
              notify={notify}
            />
          ) : (
            <>
              <div className="page-heading">
                <div>
                  <div className="eyebrow">
                    {page === "orders" ? "OPERAÇÃO DA OFICINA" : "CADASTROS"}
                  </div>
                  <h1>{labels[page]}</h1>
                  <p>{subtitles[page]}</p>
                </div>
                {canWrite && (page !== 'team' || session.role === 'OWNER') && (
                  <button
                    className="primary"
                    onClick={() => {
                      setClient(undefined);
                      setVehicle(undefined);
                      setPanel(`new-${page}`);
                    }}
                  >
                    <Icon name="plus" size={19} />
                    {page === "orders"
                      ? "Abrir OS"
                      : page === "clients"
                        ? "Cadastrar cliente"
                        : page === "vehicles"
                          ? "Cadastrar veículo"
                          : "Cadastrar usuário"}
                  </button>
                )}
              </div>
              <section className="list-panel" aria-busy={dataLoading}>
                {dataLoading && <p role="status">Buscando registros…</p>}
                <div className="list-toolbar">
                  <h2>
                    {page === "orders"
                      ? "Todas as ordens"
                      : page === "clients"
                        ? "Todos os clientes"
                        : page === "vehicles"
                          ? "Todos os veículos"
                          : "Pessoas da oficina"}
                    <span className="count">{total}</span>
                  </h2>
                  {page !== "team" && (
                    <Search
                      value={query}
                      onChange={(s) => {
                        setQuery(s);
                        setPagination(0);
                      }}
                      placeholder={
                        page === "orders"
                          ? "Buscar por placa, cliente ou nº da OS"
                          : page === "clients"
                            ? "Buscar por nome, telefone ou e-mail"
                            : "Buscar por placa, marca ou modelo"
                      }
                    />
                  )}
                </div>
                {total === 0 ? (
                  <Empty
                    title={
                      query
                        ? "Nenhum resultado encontrado"
                        : "Nenhum registro cadastrado"
                    }
                  >
                    {query
                      ? "Revise o termo de busca ou limpe o campo para ver todos os registros."
                      : "Os registros da oficina aparecerão aqui."}
                  </Empty>
                ) : (
                  <TableRegion label={labels[page]}>
                    {page === "orders" && (
                      <table className="orders-table">
                        <thead>
                          <tr>
                            <th scope="col">OS</th>
                            <th scope="col">Veículo / placa</th>
                            <th scope="col">Cliente</th>
                            <th scope="col">Status</th>
                            <th scope="col">Responsável</th>
                            <th scope="col">Entrada</th>
                            <th scope="col">Previsão</th>
                            <th scope="col">
                              <span className="sr-only">Abrir</span>
                            </th>
                          </tr>
                        </thead>
                        <tbody>
                          {orders

                            .map((o) => {
                              const v = data.veiculos.find(
                                  (v) => v.id === o.veiculoId,
                                )!,
                                c = data.clientes.find(
                                  (c) => c.id === o.clienteId,
                                )!;
                              return (
                                <tr key={o.id}>
                                  <td data-label="OS">
                                    <button
                                      className="table-link os-link"
                                      onClick={() => setSelected(o.id)}
                                      aria-label={`Abrir OS ${o.numero}`}
                                    >
                                      #{o.numero}
                                    </button>
                                  </td>
                                  <td data-label="Veículo / placa">
                                    <strong>
                                      {v.marca} {v.modelo}
                                    </strong>
                                    <small>
                                      <span className="plate">{v.placa}</span>
                                    </small>
                                  </td>
                                  <td data-label="Cliente">{c.nome}</td>
                                  <td data-label="Status">
                                    <Badge status={o.status} />
                                  </td>
                                  <td data-label="Responsável">
                                    {data.usuarios
                                      .find((u) => u.id === o.mecanicoId)
                                      ?.nome.split(" ")[0] || (
                                      <span className="unassigned">
                                        Não atribuído
                                      </span>
                                    )}
                                  </td>
                                  <td data-label="Entrada">
                                    <span className="date-cell">
                                      {date(o.criadoEm, true)}
                                    </span>
                                  </td>
                                  <td data-label="Previsão">
                                    <span className="date-cell">
                                      {date(o.previsaoEntrega, true)}
                                    </span>
                                  </td>
                                  <td data-label="Detalhes">
                                    <button
                                      className="icon-button"
                                      onClick={() => setSelected(o.id)}
                                      aria-label={`Ver detalhes da OS ${o.numero}`}
                                    >
                                      <Icon name="arrow" size={17} />
                                    </button>
                                  </td>
                                </tr>
                              );
                            })}
                        </tbody>
                      </table>
                    )}
                    {page === "clients" && (
                      <table>
                        <thead>
                          <tr>
                            <th scope="col">Nome</th>
                            <th scope="col">Telefone</th>
                            <th scope="col">E-mail</th>
                            <th scope="col">Cadastro</th>
                          </tr>
                        </thead>
                        <tbody>
                          {clients

                            .map((c) => (
                              <tr key={c.id}>
                                <td data-label="Nome">
                                  <button
                                    className="table-link"
                                    onClick={() => {
                                      setClient(c);
                                      setPanel("view-client");
                                    }}
                                  >
                                    {c.nome}
                                  </button>
                                </td>
                                <td data-label="Telefone">{c.telefone}</td>
                                <td data-label="E-mail">{c.email || "Não informado"}</td>
                                <td data-label="Cadastro">
                                  <button
                                    onClick={() => {
                                      setClient(c);
                                      setPanel("view-client");
                                    }}
                                  >
                                    Visualizar
                                  </button>
                                </td>
                              </tr>
                            ))}
                        </tbody>
                      </table>
                    )}
                    {page === "vehicles" && (
                      <table>
                        <thead>
                          <tr>
                            <th scope="col">Placa</th>
                            <th scope="col">Veículo</th>
                            <th scope="col">Ano / cor</th>
                            <th scope="col">Quilometragem</th>
                            <th scope="col">Cliente</th>
                            <th scope="col">Cadastro</th>
                          </tr>
                        </thead>
                        <tbody>
                          {vehicles

                            .map((v) => (
                              <tr key={v.id}>
                                <td data-label="Placa">
                                  <button
                                    className="table-link plate"
                                    onClick={() => {
                                      setVehicle(v);
                                      setPanel("view-vehicle");
                                    }}
                                  >
                                    {v.placa}
                                  </button>
                                </td>
                                <td data-label="Veículo">
                                  <strong>
                                    {v.marca} {v.modelo}
                                  </strong>
                                </td>
                                <td data-label="Ano / cor">
                                  {v.ano} / {v.cor}
                                </td>
                                <td data-label="Quilometragem">{number(v.km)} km</td>
                                <td data-label="Cliente">
                                  {
                                    data.clientes.find(
                                      (c) => c.id === v.clienteId,
                                    )?.nome
                                  }
                                </td>
                                <td data-label="Cadastro">
                                  <button
                                    onClick={() => {
                                      setVehicle(v);
                                      setPanel("view-vehicle");
                                    }}
                                  >
                                    Visualizar
                                  </button>
                                </td>
                              </tr>
                            ))}
                        </tbody>
                      </table>
                    )}
                    {page === "team" && (
                      <table>
                        <thead>
                          <tr>
                            <th scope="col">Nome</th>
                            <th scope="col">E-mail</th>
                            <th scope="col">Papel</th>
                            <th scope="col">Situação</th>
                          </tr>
                        </thead>
                        <tbody>
                          {data.usuarios

                            .map((u) => (
                              <tr key={u.id}>
                                <td data-label="Nome">
                                  <strong>{u.nome}</strong>
                                </td>
                                <td data-label="E-mail">{u.email}</td>
                                <td data-label="Papel">{roles[u.papel]}</td>
                                <td data-label="Situação">
                                  <span
                                    className={`classification ${u.ativo ? "ok" : ""}`}
                                  >
                                    {u.ativo ? "Ativo" : "Inativo"}
                                  </span>
                                </td>
                              </tr>
                            ))}
                        </tbody>
                      </table>
                    )}
                  </TableRegion>
                )}
                <Pager
                  total={total}
                  page={pagination}
                  setPage={setPagination}
                />
              </section>
              {page === "orders" && (
                <p className="list-help">
                  Abra uma OS para consultar o checklist, diagnóstico, orçamento
                  e histórico do serviço.
                </p>
              )}
              {page === "team" && (
                <p className="list-help">
                  O papel define as ações disponíveis. Apenas o proprietário
                  pode cadastrar usuários.
                </p>
              )}
            </>
          )}
      {toast && (
        <div role="status" className="toast">
          <Icon name="check" size={19} />
          {toast}
          <button
            onClick={() => setToast("")}
            className="icon-button"
            aria-label="Dispensar aviso"
          >
            <Icon name="close" size={16} />
          </button>
        </div>
      )}
      {panel === "new-orders" && (
        <Drawer title="Abrir ordem de serviço" close={() => setPanel("")}>
          {optionsLoading ? <PageState state="loading" title="Carregando veículos e equipe" /> : optionsError ? <PageState state="error" title="Não foi possível carregar as opções" retry={() => setOptionsAttempt(n => n + 1)}>{optionsError}</PageState> : <OrderForm
            vehicles={options.veiculos}
            clients={options.clientes}
            users={options.usuarios}
            close={() => setPanel("")}
            save={async (input) => {
              const o = await api<Order>('/ordens-servico', 'POST', { ...input, mecanicoId: input.mecanicoId || null, previsaoEntrega: input.previsaoEntrega || null });
              setData(d => ({ ...d, ordens: [...d.ordens, { ...o, diagnosticos: [], versoes: [], fotos: [], timeline: [] }] }));
              setSelected(o.id); finish(`OS #${o.numero} aberta.`);
            }}
          />}
        </Drawer>
      )}
      {(panel === "new-clients" || panel === "edit-client") && (
        <Drawer
          title={client ? "Editar cliente" : "Cadastrar cliente"}
          close={() => setPanel("")}
        >
          <ClientForm
            current={client}
            close={() => setPanel("")}
            save={saveClient}
          />
        </Drawer>
      )}
      {(panel === "new-vehicles" || panel === "edit-vehicle") && (
        <Drawer
          title={vehicle ? "Editar veículo" : "Cadastrar veículo"}
          close={() => setPanel("")}
        >
          {optionsLoading ? <PageState state="loading" title="Carregando clientes" /> : optionsError ? <PageState state="error" title="Não foi possível carregar as opções" retry={() => setOptionsAttempt(n => n + 1)}>{optionsError}</PageState> : <VehicleForm
            current={vehicle}
            clients={options.clientes}
            vehicles={data.veiculos}
            close={() => setPanel("")}
            save={saveVehicle}
          />}
        </Drawer>
      )}
      {panel === "new-team" && (
        <Drawer title="Cadastrar usuário" close={() => setPanel("")}>
          <UserForm
            users={data.usuarios}
            close={() => setPanel("")}
            save={async (u) => {
              const saved = await api<typeof u>('/usuarios', 'POST', { nome: u.nome, email: u.email, senha: u.senha, papel: u.papel });
              setData(d => ({ ...d, usuarios: [...d.usuarios, saved] })); finish('Usuário cadastrado.'); setReload(n => n + 1);
            }}
          />
        </Drawer>
      )}
      {panel === "view-client" && client && (
        <Drawer title="Cadastro do cliente" close={() => setPanel("")}>
          <h2>{client.nome}</h2>
          <dl className="record-details">
            <dt>Telefone</dt>
            <dd>{client.telefone}</dd>
            <dt>E-mail</dt>
            <dd>{client.email || "Não informado"}</dd>
          </dl>
          {canWrite && (
            <button onClick={() => setPanel("edit-client")}>
              Editar cadastro
            </button>
          )}
        </Drawer>
      )}
      {panel === "view-vehicle" && vehicle && (
        <Drawer title="Cadastro do veículo" close={() => setPanel("")}>
          <span className="plate large">{vehicle.placa}</span>
          <h2>
            {vehicle.marca} {vehicle.modelo}
          </h2>
          <dl className="record-details">
            <dt>Ano / cor</dt>
            <dd>
              {vehicle.ano} / {vehicle.cor}
            </dd>
            <dt>Quilometragem</dt>
            <dd>{number(vehicle.km)} km</dd>
            <dt>Cliente vinculado</dt>
            <dd>
              {data.clientes.find((c) => c.id === vehicle.clienteId)?.nome}
            </dd>
          </dl>
          {canWrite && (
            <button onClick={() => setPanel("edit-vehicle")}>
              Editar cadastro
            </button>
          )}
        </Drawer>
      )}
      {panel === "recovery" && <Drawer title="Dinheiro Esquecido" close={() => setPanel("")} wide>
        <div className="recovery-intro"><Icon name="recovery" size={28} /><h2>Todo retorno começa com uma boa conversa.</h2><p>A identificação de orçamentos esquecidos, revisões atrasadas e reavaliações pendentes ainda não está disponível.</p></div>
        <div className="notice"><Icon name="info" size={20} /><p>Os valores abaixo vêm da última versão de cada orçamento aguardando aprovação. Ainda não são oportunidades classificadas nem receita recuperada.</p></div>
        <h3>Orçamentos aguardando resposta</h3>
        {optionsLoading ? <PageState state="loading" title="Carregando orçamentos" /> : optionsError ? <PageState state="error" title="Falha ao carregar orçamentos" retry={() => setOptionsAttempt(n => n + 1)}>{optionsError}</PageState> : getDashboard(recoveryOrders, today).pendingBudgets.length === 0 ? <Empty title="Nenhum orçamento pendente">Não há versões aguardando decisão nas OS atuais.</Empty> :
          <ul className="global-results">{getDashboard(recoveryOrders, today).pendingBudgets.map(({ order: item, version }) => <li key={item.id}>
            <button onClick={() => openOrder(item.id)}><Icon name="orders" /><span><strong>OS #{item.numero} · {data.clientes.find((c) => c.id === item.clienteId)?.nome}</strong><small>Orçamento v{version.numero} · {date(version.criadoEm, true)} · {money(version.total)}</small><small>Próxima ação: consultar orçamento e decisão do cliente.</small></span><Icon name="arrow" size={18} /></button>
          </li>)}</ul>}
      </Drawer>}
    </Workspace>
  );
}
