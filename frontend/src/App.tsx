import { useEffect, useState } from "react";
import { Badge, Brand, Drawer, Empty, Field, Icon, Pager, Search } from "./ui";
import { ClientForm, OrderForm, UserForm, VehicleForm } from "./forms";
import OrderDetail from "./OrderDetail";
import { date, now, number, roles, seed, uid } from "./model";
import type { Client, Order, Role, Vehicle } from "./model";
import "./App.css";

type Page = "orders" | "clients" | "vehicles" | "team";
const labels: Record<Page, string> = {
  orders: "Ordens de Serviço",
  clients: "Clientes",
  vehicles: "Veículos",
  team: "Equipe",
};
const subtitles: Record<Page, string> = {
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
  const [page, setPage] = useState<Page>("orders"),
    [selected, setSelected] = useState(""),
    [query, setQuery] = useState(""),
    [pagination, setPagination] = useState(0);
  const [panel, setPanel] = useState(""),
    [client, setClient] = useState<Client>(),
    [vehicle, setVehicle] = useState<Vehicle>(),
    [toast, setToast] = useState("");
  const [loginRole, setLoginRole] = useState<Role>("OWNER"),
    [showPassword, setShowPassword] = useState(false),
    [loginError, setLoginError] = useState("");
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
  function finish(message: string) {
    setPanel("");
    notify(message);
  }
  function logout() {
    data.ordens.forEach((o) =>
      o.fotos.forEach((p) => URL.revokeObjectURL(p.url)),
    );
    setSession(null);
    setData(seed());
    navigate("orders");
    setToast("");
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
    setData((d) => ({
      ...d,
      ordens: d.ordens.map((item) => (item.id === o.id ? o : item)),
    }));
  }
  function saveClient(c: Client) {
    setData((d) => ({
      ...d,
      clientes: d.clientes.some((item) => item.id === c.id)
        ? d.clientes.map((item) => (item.id === c.id ? c : item))
        : [c, ...d.clientes],
    }));
    setClient(c);
    finish("Cliente salvo na demonstração.");
  }
  function saveVehicle(v: Vehicle) {
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
              onSubmit={(e) => {
                e.preventDefault();
                const f = new FormData(e.currentTarget);
                const oficina = String(f.get("oficina")).trim();
                if (!oficina) {
                  setLoginError("Informe a oficina.");
                  return;
                }
                setSession({ role: loginRole, oficina });
                navigate("orders");
              }}
            >
              <Field
                label="Oficina"
                hint="Identificador da oficina. Ex.: oficina-modelo"
              >
                <input
                  name="oficina"
                  required
                  maxLength={80}
                  defaultValue="oficina-modelo"
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
              <button type="submit" className="primary login-submit">
                Entrar na demonstração <Icon name="arrow" size={18} />
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
                  Dados fictícios, sem autenticação real. Use os campos
                  preenchidos. As alterações são temporárias e serão descartadas
                  ao sair ou recarregar.
                </p>
              </div>
            </form>
          </div>
          <footer>Interface em avaliação · Português do Brasil</footer>
        </main>
      </div>
    );
  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        Pular para o conteúdo
      </a>
      <aside className="sidebar">
        <Brand />
        <span className="nav-label">OPERAÇÃO</span>
        <nav aria-label="Navegação principal">
          {(Object.keys(labels) as Page[])
            .filter((p) => p !== "team" || session.role === "OWNER")
            .map((p) => (
              <button
                key={p}
                className={page === p ? "active" : ""}
                aria-current={page === p ? "page" : undefined}
                onClick={() => navigate(p)}
              >
                <Icon name={p} />
                {labels[p]}
              </button>
            ))}
        </nav>
        <div className="sidebar-bottom">
          <span className="sidebar-rule" />
          <strong>Fase 1</strong>
          <small>Operação da oficina</small>
          <span className="prototype-tag">Protótipo para avaliação</span>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <div className="workshop">
            <span className="workshop-symbol">
              <Icon name="vehicles" size={20} />
            </span>
            <div>
              <strong>
                {session.oficina === "oficina-modelo"
                  ? "Oficina Modelo"
                  : session.oficina}
              </strong>
              <small>Oficina atual</small>
            </div>
          </div>
          <div className="account">
            <span className="avatar">
              {user?.nome
                .split(" ")
                .map((s) => s[0])
                .slice(0, 2)
                .join("")}
            </span>
            <div>
              <strong>{user?.nome}</strong>
              <small>{roles[session.role]}</small>
            </div>
            <button className="logout" onClick={logout}>
              <Icon name="logout" size={18} />
              <span>Sair</span>
            </button>
          </div>
        </header>
        <div className="demo-ribbon">
          <span className="demo-dot" />
          Demonstração com dados fictícios
          <span>Alterações válidas apenas nesta sessão</span>
        </div>
        <main id="main-content" className="main-content">
          {order && page === "orders" ? (
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
                                  <td>
                                    <button
                                      className="table-link os-link"
                                      onClick={() => setSelected(o.id)}
                                      aria-label={`Abrir OS ${o.numero}`}
                                    >
                                      #{o.numero}
                                    </button>
                                  </td>
                                  <td>
                                    <strong>
                                      {v.marca} {v.modelo}
                                    </strong>
                                    <small>
                                      <span className="plate">{v.placa}</span>
                                    </small>
                                  </td>
                                  <td>{c.nome}</td>
                                  <td>
                                    <Badge status={o.status} />
                                  </td>
                                  <td>
                                    {data.usuarios
                                      .find((u) => u.id === o.mecanicoId)
                                      ?.nome.split(" ")[0] || (
                                      <span className="unassigned">
                                        Não atribuído
                                      </span>
                                    )}
                                  </td>
                                  <td>
                                    <span className="date-cell">
                                      {date(o.criadoEm, true)}
                                    </span>
                                  </td>
                                  <td>
                                    <span className="date-cell">
                                      {date(o.previsaoEntrega, true)}
                                    </span>
                                  </td>
                                  <td>
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
                                <td>
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
                                <td>{c.telefone}</td>
                                <td>{c.email || "Não informado"}</td>
                                <td>
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
                                <td>
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
                                <td>
                                  <strong>
                                    {v.marca} {v.modelo}
                                  </strong>
                                </td>
                                <td>
                                  {v.ano} / {v.cor}
                                </td>
                                <td>{number(v.km)} km</td>
                                <td>
                                  {
                                    data.clientes.find(
                                      (c) => c.id === v.clienteId,
                                    )?.nome
                                  }
                                </td>
                                <td>
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
                                <td>
                                  <strong>{u.nome}</strong>
                                </td>
                                <td>{u.email}</td>
                                <td>{roles[u.papel]}</td>
                                <td>
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
        </main>
        <footer className="workspace-footer">
          <span>Garagem SaaS</span>
          <span>Fase 1 · Protótipo de interface</span>
        </footer>
      </div>
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
            save={(input) => {
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
    </div>
  );
}
