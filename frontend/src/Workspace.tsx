import { useEffect, useRef, useState } from "react";
import type { ReactNode } from "react";
import { Brand, Drawer, Empty, Icon, Search } from "./ui";
import type { IconName } from "./icons";
import { roles } from "./model";
import type { Client, Order, Role, Vehicle } from "./model";
import { getDashboard } from "./dashboard-model";
import { listPage } from './api';
import { PageState } from './PageState';
import GettingStarted from './GettingStarted';

import { labels } from "./navigation";
import type { Page } from "./navigation";
type Panel = "menu" | "search" | "notifications" | "profile" | "settings" | "calendar" | "parking" | "parts" | "help" | null;
const upcoming: { id: "calendar" | "parking" | "parts"; name: string; description: string }[] = [
  { id: "calendar", name: "Agenda", description: "As entradas previstas e a capacidade diária serão exibidas quando os agendamentos estiverem disponíveis. Consulte os recebimentos já registrados na visão geral." },
  { id: "parking", name: "Pátio", description: "A ocupação depende do cadastro de vagas e da localização dos veículos. A quantidade de OS abertas não é uma medida de ocupação." },
  { id: "parts", name: "Peças", description: "O catálogo de peças ainda não está disponível. Você já pode consultar peças e valores nos orçamentos de cada ordem de serviço." },
];

interface WorkspaceProps {
  page: Page; navigate: (page: Page) => void; children: ReactNode;
  role: Role; name: string; workshop: string; logout: () => void;
  orders: Order[]; clients: Client[]; vehicles: Vehicle[]; today: Date;
  openOrder: (id: string) => void; openClient: (client: Client) => void;
  openVehicle: (vehicle: Vehicle) => void; openRecovery: () => void; openCompany: () => void;
}

export default function Workspace(props: WorkspaceProps) {
  const { page, navigate, children, role, name, workshop, logout, orders, clients, today, openOrder, openClient, openVehicle, openRecovery, openCompany } = props;
  const [panel, setPanel] = useState<Panel>(null);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState({ orders: [] as Order[], clients: [] as Client[], vehicles: [] as Vehicle[], total: 0 });
  const [searchLoading, setSearchLoading] = useState(false);
  const [searchError, setSearchError] = useState('');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    if (panel !== 'search' || !query.trim()) return;
    let active = true;
    // oxlint-disable-next-line react/set-state-in-effect -- API request lifecycle
    setSearchLoading(true); setSearchError('');
    const timer = window.setTimeout(() => {
      Promise.all([listPage<Order>('/ordens-servico', 0, query), listPage<Client>('/clientes', 0, query), listPage<Vehicle>('/veiculos', 0, query)])
        .then(([orders, clients, vehicles]) => { if (active) setResults({ orders: orders.itens, clients: clients.itens, vehicles: vehicles.itens, total: orders.total + clients.total + vehicles.total }); })
        .catch(error => { if (active) setSearchError(error.message); })
        .finally(() => { if (active) setSearchLoading(false); });
    }, 300);
    return () => { active = false; window.clearTimeout(timer); };
  }, [panel, query, attempt]);
  const menuRef = useRef<HTMLDialogElement>(null);
  const menuButtonRef = useRef<HTMLButtonElement>(null);
  const mainRef = useRef<HTMLElement>(null);
  useEffect(() => {
    if (panel !== "menu") return;
    const previous = menuButtonRef.current;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    menuRef.current?.showModal();
    return () => {
      document.body.style.overflow = overflow;
      previous?.focus();
    };
  }, [panel]);
  const move = (next: Page) => {
    setPanel(null);
    navigate(next);
    requestAnimationFrame(() => mainRef.current?.focus());
  };
  const initials = name.split(" ").map((part) => part[0]).slice(0, 2).join("");
  const dashboard = getDashboard(orders, today);
  const clean = query.trim().toLocaleLowerCase("pt-BR");
  const matchedOrders = results.orders, matchedClients = results.clients, matchedVehicles = results.vehicles;
  const total = results.total;
  const navButton = (id: Page, icon: IconName = id) => <button key={id} aria-current={page === id ? "page" : undefined} className={page === id ? "active" : ""} onClick={() => move(id)}><Icon name={icon} /><span>{labels[id]}</span></button>;
  const navigation = <>
    <div className="workspace-brand"><Brand /></div>
    <div className="tenant-label"><Icon name="security" size={18} /><div><strong>{workshop}</strong><span>Empresa autenticada</span></div></div>
    <span className="navigation-caption">OPERAÇÃO</span>
    <nav aria-label="Navegação principal">
      {navButton("overview")}{navButton("orders")}{navButton("clients")}{navButton("vehicles")}
      {upcoming.map((item) => <button key={item.id} onClick={() => setPanel(item.id)}><Icon name={item.id} /><span>{item.name}</span><small>Em breve</small></button>)}
    </nav>
    <span className="navigation-caption">CRESCIMENTO</span>
    {role !== 'MECANICO' && <nav aria-label="Crescimento"><button className="recovery-nav" onClick={() => { setPanel(null); openRecovery(); }}><Icon name="recovery" /><span>Dinheiro Esquecido</span></button></nav>}
    <div className="navigation-bottom"><nav aria-label="Administração">
      {role === "OWNER" && navButton("team")}
      <button onClick={() => setPanel("help")}><Icon name="info" /><span>Primeiros passos</span></button>
      <button onClick={() => setPanel("settings")}><Icon name="settings" /><span>Configurações</span></button>
    </nav><button className="sidebar-profile" onClick={() => setPanel("profile")}><span className="avatar">{initials}</span><span><strong>{name}</strong><small>{roles[role]}</small></span><Icon name="arrow" size={16} /></button></div>
  </>;
  const module = upcoming.find((item) => item.id === panel);
  return <div className="premium-shell">
    <a className="skip-link" href="#main-content">Pular para o conteúdo</a>
    <aside className="desktop-navigation">{navigation}</aside>
    {panel === "menu" && <dialog ref={menuRef} className="mobile-navigation" aria-label="Menu de navegação" onCancel={(event) => { event.preventDefault(); setPanel(null); }}>
      <button className="icon-button close-menu" aria-label="Fechar menu" onClick={() => setPanel(null)}><Icon name="close" /></button>{navigation}
    </dialog>}
    <div className="premium-workspace">
      <header className="workspace-header">
        <button ref={menuButtonRef} className="icon-button menu-trigger" aria-label="Abrir menu" aria-expanded={panel === "menu"} onClick={() => setPanel("menu")}><Icon name="menu" /></button>
        <span className="breadcrumb">Oficina<span>/</span><strong>{labels[page]}</strong></span>
        <button className="global-search-trigger" onClick={() => { setQuery(""); setPanel("search"); }}><Icon name="search" size={18} /><span>Buscar na oficina</span></button>
        <div className="header-actions"><button className="icon-button" aria-label="Notificações" onClick={() => setPanel("notifications")}><Icon name="bell" /></button><span className="header-divider" /><button className="profile-trigger" aria-label="Abrir perfil" onClick={() => setPanel("profile")}><span className="avatar">{initials}</span></button></div>
      </header>
      <div className="context-strip"><span><Icon name="info" size={14} />Dados da oficina</span><time dateTime={today.toISOString()}>{today.toLocaleDateString("pt-BR", { weekday: "long", day: "numeric", month: "long" })}</time></div>
      <main ref={mainRef} tabIndex={-1} id="main-content" className="premium-main">{children}</main>
      <footer className="premium-footer"><span>Plataforma Automotiva <span>/</span> Gestão que cuida do seu negócio.</span><span>Operação da oficina</span></footer>
    </div>
    {panel === "search" && <Drawer title="Buscar na oficina" close={() => setPanel(null)}>
      <Search value={query} onChange={setQuery} placeholder="OS, placa, veículo ou cliente" />
      <p className="search-count" role="status">{clean ? searchLoading ? 'Buscando…' : `${total} resultado(s) na oficina. Exibindo até 10 por categoria; refine a busca ou consulte a listagem.` : "Busque nos clientes, veículos e ordens de serviço."}</p>
      {clean && searchError && <PageState state="error" title="Falha na busca" retry={() => setAttempt(n => n + 1)}>{searchError}</PageState>}
      {clean && !searchLoading && !searchError && total === 0 && <Empty title="Nenhum resultado">Tente outro nome, placa ou número de OS.</Empty>}
      {clean && !searchLoading && !searchError &&
      <ul className="global-results">
        {matchedOrders.map((o) => <li key={o.id}><button onClick={() => { setPanel(null); openOrder(o.id); }}><Icon name="orders" /><span><strong>OS #{o.numero}</strong><small>{clients.find((c) => c.id === o.clienteId)?.nome}</small></span><Icon name="arrow" size={16} /></button></li>)}
        {matchedClients.map((c) => <li key={c.id}><button onClick={() => { setPanel(null); openClient(c); }}><Icon name="clients" /><span><strong>{c.nome}</strong><small>Cliente · {c.telefone}</small></span><Icon name="arrow" size={16} /></button></li>)}
        {matchedVehicles.map((v) => <li key={v.id}><button onClick={() => { setPanel(null); openVehicle(v); }}><Icon name="vehicles" /><span><strong>{v.marca} {v.modelo}</strong><small>Veículo · {v.placa}</small></span><Icon name="arrow" size={16} /></button></li>)}
      </ul>}
    </Drawer>}
    {panel === "notifications" && <Drawer title="Notificações" close={() => setPanel(null)}>
      <p>Os avisos em tempo real ainda não estão disponíveis. Estas são as pendências identificadas nas OS carregadas.</p>
      {dashboard.priorities.length === 0 ? <Empty title="Nenhuma pendência nas OS carregadas">Consulte a listagem de ordens de serviço para verificar os demais registros da oficina.</Empty> : <ul className="global-results">{dashboard.priorities.map(({ order, reason }) => <li key={order.id}><button onClick={() => { setPanel(null); openOrder(order.id); }}><Icon name="clock" /><span><strong>OS #{order.numero}</strong><small>{reason}</small></span><Icon name="arrow" size={16} /></button></li>)}</ul>}
    </Drawer>}
    {(panel === "profile" || panel === "settings") && <Drawer title={panel === "profile" ? "Seu perfil" : "Configurações"} close={() => setPanel(null)}>
      <div className="profile-summary"><span className="avatar">{initials}</span><h3>{name}</h3><p>{roles[role]} · {workshop}</p></div>
      <p>Sua sessão está vinculada à sua empresa.</p>
      {role === "OWNER" && <button onClick={() => { setPanel(null); openCompany(); }}><Icon name="security" />Identidade da empresa</button>}
      {role === "OWNER" && <button onClick={() => move("team")}><Icon name="team" />Gerenciar equipe</button>}
      <button className="session-logout" onClick={logout}><Icon name="logout" size={18} />Sair da oficina</button>
    </Drawer>}
    {module && <Drawer title={module.name} close={() => setPanel(null)}><Empty title="Em preparação">{module.description}</Empty><button onClick={() => move(module.id === "parts" ? "orders" : "overview")}>Ir para {module.id === "parts" ? "ordens de serviço" : "visão geral"}<Icon name="forward" size={18} /></button></Drawer>}
    {panel === 'help' && <GettingStarted role={role} close={() => setPanel(null)} navigate={move} recovery={() => { setPanel(null); openRecovery(); }} />}
  </div>;
}
