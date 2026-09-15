import { lazy, Suspense, useEffect, useState } from "react";
import { Badge, Brand, Drawer, Empty, Field, Icon, Pager, Search } from "./ui";
import { ClientForm, OrderForm, UserForm, VehicleForm } from "./forms";
import OrderDetail from "./OrderDetail";
import { PageState } from "./PageState";
import Workspace from "./Workspace";
import { labels } from "./navigation";
import type { Page } from "./navigation";
import { getDashboard } from "./dashboard-model";
import { date, money, now, number, roles, seed, uid } from "./model";
import type { Client, Order, Role, Vehicle } from "./model";
import { api, clearSession, setSession as persistApiSession } from "./api";
import type { Checklist, Diagnostico, Evento, OrdemServico, OrcamentoVersao, Usuario } from "./api";
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
  const [data, setData] = useState(seed),
    [session, setSession] = useState<{ role: Role; oficina: string } | null>(
      null,
    );
  const [today, setToday] = useState(() => new Date());
  useEffect(() => {
    const timer = window.setInterval(() => setToday(new Date()), 60_000);
    return () => window.clearInterval(timer);
  }, []);
  const [page, setPage] = useState<Page>("overview"),
    [selected, setSelected] = useState(""),
    [query, setQuery] = useState(""),
    [pagination, setPagination] = useState(0);
  const [panel, setPanel] = useState(""),
    [client, setClient] = useState<Client>(),
    [vehicle, setVehicle] = useState<Vehicle>(),
    [toast, setToast] = useState("");
  const [loginRole, setLoginRole] = useState<Role>("OWNER"),
    [showPassword, setShowPassword] = useState(false),
    [loginError, setLoginError] = useState(""),
    [loginLoading, setLoginLoading] = useState(false);
  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(""), 4500);
    return () => clearTimeout(timer);
  }, [toast]);
  function navigate(next: Page) {
    setPage(next);
    setSelected("");
    setQuery("");
    setPagination(0);
    setPanel("");
  }
  const notify = (s: string) => setToast(s);
  async function hydrateOrder(id: string): Promise<void> {
    if (!api.isConfigured) return;
    const [remoteOrder, checklist, diagnostics, versions, timeline] = await Promise.all([
      api.getOrder(id),
      api.getChecklist(id).catch(() => null),
      api.getDiagnostics(id).catch(() => []),
      api.getBudgetVersions(id).catch(() => []),
      api.getTimeline(id).catch(() => []),
    ]);
    const mapClassification = (value: Diagnostico["classificacao"]): "OK" | "ACOMPANHAR" | "TROCAR" =>
      value === "BOM" ? "OK" : value === "ATENCAO" ? "ACOMPANHAR" : "TROCAR";
    const mapped: Order = {
      id: remoteOrder.id,
      numero: remoteOrder.numero,
      veiculoId: remoteOrder.veiculoId,
      clienteId: remoteOrder.clienteId,
      mecanicoId: remoteOrder.mecanicoId ?? "",
      status: remoteOrder.status,
      kmEntrada: remoteOrder.kmEntrada,
      relato: remoteOrder.relato,
      criadoEm: remoteOrder.criadoEm,
      previsaoEntrega: remoteOrder.previsaoEntrega ?? remoteOrder.criadoEm,
      revisao: remoteOrder.revisao,
      checklist: checklist ? {
        observacoes: checklist.observacoes ?? "",
        itens: checklist.itens.map((item: Checklist["itens"][number]) => ({
          id: item.id,
          descricao: item.descricao,
          condicao: item.condicao,
          observacao: item.observacao ?? "",
        })),
      } : undefined,
      diagnosticos: diagnostics.map((item: Diagnostico) => ({ ...item, classificacao: mapClassification(item.classificacao) })),
      versoes: versions.map((version: OrcamentoVersao) => ({
        ...version,
        observacoes: version.observacoes ?? "",
        itens: version.itens.map((item) => ({ ...item })),
        decisao: version.decisao ?? undefined,
      })),
      fotos: [],
      timeline: timeline.map((event: Evento) => ({
        id: event.id,
        descricao: event.descricao,
        origem: event.origem,
        criadoEm: event.criadoEm,
      })),
    };
    setData((current) => ({ ...current, ordens: current.ordens.map((item) => item.id === id ? mapped : item) }));
  }
  function openOrder(id: string) {
    navigate("orders");
    setSelected(id);
    void hydrateOrder(id).catch((error) => notify(error instanceof Error ? error.message : "Não foi possível carregar os detalhes da OS."));
  }
  function finish(message: string) {
    setPanel("");
    notify(message);
  }
  function logout() {
    data.ordens.forEach((o) =>
      o.fotos.forEach((p) => URL.revokeObjectURL(p.url)),
    );
    if (api.isConfigured) void api.logout().catch(() => undefined);
    clearSession();
    setSession(null);
    setData(seed());
    navigate("orders");
    setToast("");
  }
  async function loadRemoteData(): Promise<void> {
    const [clientsPage, vehiclesPage, ordersPage, usersPage] = await Promise.all([
      api.listClients(),
      api.listVehicles(),
      api.listOrders(),
      api.listUsers(),
    ]);
    const remoteOrders: Order[] = ordersPage.itens.map((order: OrdemServico) => ({
      id: order.id,
      numero: order.numero,
      veiculoId: order.veiculoId,
      clienteId: order.clienteId,
      mecanicoId: order.mecanicoId ?? "",
      status: order.status,
      kmEntrada: order.kmEntrada,
      relato: order.relato,
      criadoEm: order.criadoEm,
      previsaoEntrega: order.previsaoEntrega ?? order.criadoEm,
      revisao: order.revisao,
      diagnosticos: [],
      versoes: [],
      fotos: [],
      timeline: [],
    }));
    setData({
      clientes: clientsPage.itens.map((client) => ({ ...client, email: client.email ?? "" })),
      veiculos: vehiclesPage.itens,
      ordens: remoteOrders,
      usuarios: usersPage.itens.map((user: Usuario) => user),
    });
  }
  const user = session
    ? data.usuarios.find((u) => u.papel === session.role)!
    : null;
  const canWrite = session?.role !== "MECANICO";
  const clean = query.trim().toLocaleLowerCase("pt-BR");
  const orders = data.ordens.filter(
    (o) =>
      `${o.numero}`.includes(clean) ||
      data.veiculos
        .find((v) => v.id === o.veiculoId)!
        .placa.toLowerCase()
        .includes(clean) ||
      data.clientes
        .find((c) => c.id === o.clienteId)!
        .nome.toLocaleLowerCase("pt-BR")
        .includes(clean),
  );
  const clients = data.clientes.filter((c) =>
    c.nome.toLocaleLowerCase("pt-BR").includes(clean),
  );
  const vehicles = data.veiculos.filter((v) =>
    v.placa.toLowerCase().includes(clean),
  );
  const total =
    page === "orders"
      ? orders.length
      : page === "clients"
        ? clients.length
        : page === "vehicles"
          ? vehicles.length
          : data.usuarios.length;
  const order = data.ordens.find((o) => o.id === selected);
  function updateOrder(o: Order) {
    const previous = data.ordens.find((item) => item.id === o.id);
    setData((d) => ({
      ...d,
      ordens: d.ordens.map((item) => (item.id === o.id ? o : item)),
    }));
    if (!api.isConfigured || !previous) return;
    const revision = Math.max(0, o.revisao - 1);
    const writes: Promise<unknown>[] = [];
    if (previous.status !== o.status) writes.push(api.updateOrderStatus(o.id, o.status, revision));
    if (!previous.checklist && o.checklist) writes.push(api.createChecklist(o.id, {
      observacoes: o.checklist.observacoes,
      itens: o.checklist.itens.map((item) => ({ descricao: item.descricao, condicao: item.condicao, observacao: item.observacao })),
    }));
    if (o.diagnosticos.length > previous.diagnosticos.length) {
      const item = o.diagnosticos.at(-1);
      if (item) writes.push(api.addDiagnostic(o.id, {
        descricao: item.descricao,
        classificacao: item.classificacao === "OK" ? "BOM" : item.classificacao === "ACOMPANHAR" ? "ATENCAO" : "CRITICO",
      }));
    }
    if (o.versoes.length > previous.versoes.length) {
      const version = o.versoes.at(-1);
      if (version) writes.push(api.createBudgetVersion(o.id, {
        observacoes: version.observacoes,
        itens: version.itens.map((item) => ({ tipo: item.tipo, descricao: item.descricao, quantidade: item.quantidade, valorUnitario: item.valorUnitario })),
      }));
    }
    if (writes.length) void Promise.all(writes).then(() => hydrateOrder(o.id)).catch((error) => notify(error instanceof Error ? error.message : "Não foi possível salvar a alteração da OS."));
  }
  async function saveClient(c: Client) {
    if (api.isConfigured) {
      try {
        const saved = c.id.startsWith("c") && data.clientes.some((item) => item.id === c.id)
          ? await api.updateClient(c.id, c)
          : await api.createClient(c);
        c = { ...saved, email: saved.email ?? "" };
      } catch (error) {
        notify(error instanceof Error ? error.message : "Não foi possível salvar o cliente.");
        return;
      }
    }
    setData((d) => ({
      ...d,
      clientes: d.clientes.some((item) => item.id === c.id)
        ? d.clientes.map((item) => (item.id === c.id ? c : item))
        : [c, ...d.clientes],
    }));
    setClient(c);
    finish("Cliente salvo na demonstração.");
  }
  async function saveVehicle(v: Vehicle) {
    if (api.isConfigured) {
      try {
        v = v.id.startsWith("v") && data.veiculos.some((item) => item.id === v.id)
          ? await api.updateVehicle(v.id, v)
          : await api.createVehicle(v);
      } catch (error) {
        notify(error instanceof Error ? error.message : "Não foi possível salvar o veículo.");
        return;
      }
    }
    setData((d) => ({
      ...d,
      veiculos: d.veiculos.some((item) => item.id === v.id)
        ? d.veiculos.map((item) => (item.id === v.id ? v : item))
        : [v, ...d.veiculos],
    }));
    setVehicle(v);
    finish("Veículo salvo na demonstração.");
  }
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
            <span className="demo-label">PROTÓTIPO · FASE 1</span>
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
                setLoginError("");
                setLoginLoading(true);
                try {
                  if (api.isConfigured) {
                    const remoteSession = await api.login({ oficina, email, senha });
                    persistApiSession(remoteSession);
                    setSession({ role: remoteSession.papel, oficina: remoteSession.oficinaId });
                    await loadRemoteData();
                  } else {
                    setSession({ role: loginRole, oficina });
                  }
                  navigate("overview");
                } catch (error) {
                  setLoginError(error instanceof Error ? error.message : "Não foi possível entrar.");
                } finally {
                  setLoginLoading(false);
                }
              }}
            >
              <Field
                label="Oficina"
                hint="Oficina fictícia usada nesta demonstração."
              >
                <input
                  name="oficina"
                  required
                  maxLength={80}
                  defaultValue="oficina-modelo"
                  readOnly
                  autoComplete="organization"
                />
              </Field>
              <Field label="E-mail">
                <input
                  name="email"
                  type="email"
                  required
                  maxLength={254}
                  defaultValue="andre@example.com"
                  autoComplete="username"
                />
              </Field>
              <Field label="Senha">
                <span className="password-input">
                  <input
                    name="senha"
                    type={showPassword ? "text" : "password"}
                    required
                    maxLength={72}
                    defaultValue="demonstracao123"
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
              <button type="submit" className="primary login-submit" disabled={loginLoading}>
                {loginLoading ? "Entrando..." : api.isConfigured ? "Entrar na oficina" : "Entrar na demonstração"} <Icon name="arrow" size={18} />
              </button>
              <div className="demo-controls">
                <Field label="Papel para avaliação do protótipo">
                  <select
                    value={loginRole}
                    onChange={(e) => setLoginRole(e.target.value as Role)}
                  >
                    {Object.entries(roles).map(([r, label]) => (
                      <option value={r} key={r}>
                        {label}
                      </option>
                    ))}
                  </select>
                </Field>
                <p>
                  {api.isConfigured
                    ? "Acesso protegido pela API da oficina."
                    : "Dados fictícios, sem autenticação real. Use os campos preenchidos. As alterações são temporárias e serão descartadas ao sair ou recarregar."}
                </p>
              </div>
            </form>
          </div>
          <footer>Interface em avaliação · Português do Brasil</footer>
        </main>
      </div>
    );
  return (
    <Workspace page={page} navigate={navigate} role={session.role}
      name={user?.nome ?? "Usuário"} workshop="Oficina Modelo"
      logout={logout} orders={data.ordens} clients={data.clientes} vehicles={data.veiculos} today={today}
      openOrder={openOrder}
      openClient={(c) => { navigate("clients"); setClient(c); setPanel("view-client"); }}
      openVehicle={(v) => { navigate("vehicles"); setVehicle(v); setPanel("view-vehicle"); }}
      openRecovery={() => setPanel("recovery")}>
          {page === "overview" ? <Suspense fallback={<PageState state="loading" title="Preparando sua visão geral" />}><Dashboard orders={data.ordens} clients={data.clientes} vehicles={data.veiculos}
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
                {canWrite && (
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
              <section className="list-panel">
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
                            ? "Buscar por nome do cliente"
                            : "Buscar por placa"
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
                  <div className="table-scroll">
                    {page === "orders" && (
                      <table className="orders-table">
                        <thead>
                          <tr>
                            <th>OS</th>
                            <th>Veículo / placa</th>
                            <th>Cliente</th>
                            <th>Status</th>
                            <th>Responsável</th>
                            <th>Entrada</th>
                            <th>Previsão</th>
                            <th>
                              <span className="sr-only">Abrir</span>
                            </th>
                          </tr>
                        </thead>
                        <tbody>
                          {orders
                            .slice(pagination * 10, pagination * 10 + 10)
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
                            <th>Nome</th>
                            <th>Telefone</th>
                            <th>E-mail</th>
                            <th>Cadastro</th>
                          </tr>
                        </thead>
                        <tbody>
                          {clients
                            .slice(pagination * 10, pagination * 10 + 10)
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
                            <th>Placa</th>
                            <th>Veículo</th>
                            <th>Ano / cor</th>
                            <th>Quilometragem</th>
                            <th>Cliente</th>
                            <th>Cadastro</th>
                          </tr>
                        </thead>
                        <tbody>
                          {vehicles
                            .slice(pagination * 10, pagination * 10 + 10)
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
                            <th>Nome</th>
                            <th>E-mail</th>
                            <th>Papel</th>
                            <th>Situação</th>
                          </tr>
                        </thead>
                        <tbody>
                          {data.usuarios
                            .slice(pagination * 10, pagination * 10 + 10)
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
                  </div>
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
          <OrderForm
            vehicles={data.veiculos}
            clients={data.clientes}
            users={data.usuarios}
            close={() => setPanel("")}
            save={async (input) => {
              if (api.isConfigured) {
                try {
                  const remote = await api.createOrder(input);
                  await loadRemoteData();
                  setSelected(remote.id);
                  finish(`OS #${remote.numero} aberta na oficina.`);
                  return;
                } catch (error) {
                  notify(error instanceof Error ? error.message : "Não foi possível abrir a OS.");
                  return;
                }
              }
              const v = data.veiculos.find((v) => v.id === input.veiculoId)!;
              const created = now();
              const o: Order = {
                ...input,
                id: uid(),
                numero: Math.max(0, ...data.ordens.map((o) => o.numero)) + 1,
                clienteId: v.clienteId,
                status: "RECEBIDO",
                criadoEm: created,
                revisao: 0,
                diagnosticos: [],
                versoes: [],
                fotos: [],
                timeline: [
                  {
                    id: uid(),
                    descricao: "OS recebida na oficina.",
                    origem: `${user?.nome} · Usuário`,
                    criadoEm: created,
                  },
                ],
              };
              setData((d) => ({
                ...d,
                ordens: [o, ...d.ordens],
                veiculos: d.veiculos.map((item) =>
                  item.id === v.id ? { ...item, km: input.kmEntrada } : item,
                ),
              }));
              setSelected(o.id);
              finish(`OS #${o.numero} aberta na demonstração.`);
            }}
          />
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
          <VehicleForm
            current={vehicle}
            clients={data.clientes}
            vehicles={data.veiculos}
            close={() => setPanel("")}
            save={saveVehicle}
          />
        </Drawer>
      )}
      {panel === "new-team" && (
        <Drawer title="Cadastrar usuário" close={() => setPanel("")}>
          <UserForm
            users={data.usuarios}
            close={() => setPanel("")}
            save={(u) => {
              setData((d) => ({ ...d, usuarios: [...d.usuarios, u] }));
              finish("Usuário cadastrado na demonstração.");
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
        {getDashboard(data.ordens, today).pendingBudgets.length === 0 ? <Empty title="Nenhum orçamento pendente">Não há versões aguardando decisão nas OS atuais.</Empty> :
          <ul className="global-results">{getDashboard(data.ordens, today).pendingBudgets.map(({ order: item, version }) => <li key={item.id}>
            <button onClick={() => openOrder(item.id)}><Icon name="orders" /><span><strong>OS #{item.numero} · {data.clientes.find((c) => c.id === item.clienteId)?.nome}</strong><small>Orçamento v{version.numero} · {date(version.criadoEm, true)} · {money(version.total)}</small><small>Próxima ação: consultar orçamento e decisão do cliente.</small></span><Icon name="arrow" size={18} /></button>
          </li>)}</ul>}
      </Drawer>}
    </Workspace>
  );
}
