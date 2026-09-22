import { Empty, Icon } from "./ui";
import type { IconName } from "./icons";
import { money } from "./model";
import type { Client, Order, Vehicle } from "./model";
import { getDashboard } from "./dashboard-model";

interface DashboardProps {
  summary: import("./api/types").Dashboard | null;
  orders: Order[];
  vehicles: Vehicle[];
  clients: Client[];
  today: Date;
  canWrite: boolean;
  openOrder: (id: string) => void;
  newOrder: () => void;
  viewOrders: () => void;
  viewRecovery: () => void;
}

function Metric({ icon, label, value, detail }: {
  icon: IconName; label: string; value: string; detail: string;
}) {
  return <div className="metric">
    <dt><Icon name={icon} size={18} />{label}</dt>
    <dd>{value}</dd><dd className="metric-detail">{detail}</dd>
  </div>;
}

export default function Dashboard({ summary, orders, vehicles, clients, today, canWrite, openOrder, newOrder, viewOrders, viewRecovery }: DashboardProps) {
  const dashboard = getDashboard(orders, today);
  const vehicleName = (order: Order) => {
    const vehicle = vehicles.find((v) => v.id === order.veiculoId);
    return vehicle ? `${vehicle.marca} ${vehicle.modelo}` : "Veículo não informado";
  };
  return <div className="dashboard">
    <div className="page-heading">
      <div><h1>Início</h1><p>O que precisa da sua atenção hoje.</p></div>
      {canWrite && <button className="primary" onClick={newOrder}><Icon name="plus" size={18} />Abrir OS</button>}
    </div>
    {/* Quatro números, e todos acionáveis. Um indicador que a oficina não consegue mudar hoje não
        informa nada — o card de ocupação do pátio existia sem a capacidade que o alimentaria e
        mostrava um travessão desde o primeiro dia. */}
    <dl className="metrics" aria-label="Indicadores da oficina">
      <Metric icon="work" label="OS em andamento" value={String(summary?.emAndamento ?? 0).padStart(2, "0")} detail="Do recebimento ao teste" />
      <Metric icon="clock" label="Aguardando aprovação" value={String(summary?.porStatus.AGUARDANDO_APROVACAO ?? 0).padStart(2, "0")} detail="Aguardam decisão do cliente" />
      <Metric icon="good" label="Veículos prontos" value={String(summary?.prontas ?? 0).padStart(2, "0")} detail="Serviços concluídos" />
      <Metric icon="recovery" label="Orçamentos sem resposta" value={String(summary?.orcamentosAguardandoDecisao.quantidade ?? 0).padStart(2, "0")} detail={`${money(summary?.orcamentosAguardandoDecisao.total ?? 0)} aguardando — não é receita`} />
    </dl>
    {canWrite && <p className="list-help"><button className="text-action" onClick={viewRecovery}>Ver retornos<Icon name="forward" size={16} /></button></p>}

    <div className="dashboard-columns">
      <section className="surface priorities" aria-labelledby="priorities-title">
        <header className="surface-heading"><div><h2 id="priorities-title">Prioridades nas OS recentes <span className="count">{dashboard.priorities.length}</span></h2><p>Próximos passos para o trabalho avançar.</p></div><Icon name="orders" /></header>
        {dashboard.priorities.length === 0 ? <Empty title="Tudo em ordem por aqui">Nenhuma pendência identificada nas ordens atuais.</Empty> :
          <ul className="priority-list">{dashboard.priorities.map(({ order, reason, overdue }) => <li key={order.id}>
            <span className={`priority-symbol ${overdue ? "critical" : "attention"}`}><Icon name={overdue ? "critical" : "clock"} size={19} /></span>
            <div><span className="item-eyebrow">OS #{order.numero}</span><h3>{vehicleName(order)}</h3><p>{reason}</p></div>
            <button className="icon-button" aria-label={`Abrir OS ${order.numero}`} onClick={() => openOrder(order.id)}><Icon name="arrow" size={18} /></button>
          </li>)}</ul>}
        <footer className="surface-footer"><button className="text-action" onClick={viewOrders}>Ver todas as ordens<Icon name="forward" size={16} /></button></footer>
      </section>
      <section className="surface" aria-labelledby="entries-title">
        <header className="surface-heading"><div><h2 id="entries-title">Entradas de hoje nas OS recentes</h2><p>Recebimentos registrados na oficina.</p></div><Icon name="calendar" size={20} /></header>
        {dashboard.entries.length === 0 ? <Empty title="Nenhuma entrada nas OS recentes">Consulte todas as ordens para outros recebimentos.</Empty> :
          <ul className="entry-list">{dashboard.entries.map((order) => <li key={order.id}>
            <time dateTime={order.criadoEm}>{new Date(order.criadoEm).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" })}</time>
            <button onClick={() => openOrder(order.id)}><strong>{vehicleName(order)}</strong><span>{clients.find((c) => c.id === order.clienteId)?.nome ?? "Cliente não informado"} · OS #{order.numero}</span></button>
          </li>)}</ul>}
      </section>
    </div>
  </div>;
}
