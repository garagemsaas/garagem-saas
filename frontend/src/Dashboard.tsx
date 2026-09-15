import { Badge, Empty, Icon } from "./ui";
import type { IconName } from "./icons";
import { date, money } from "./model";
import type { Client, Order, Vehicle } from "./model";
import { getDashboard } from "./dashboard-model";

interface DashboardProps {
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

export default function Dashboard({ orders, vehicles, clients, today, canWrite, openOrder, newOrder, viewOrders, viewRecovery }: DashboardProps) {
  const dashboard = getDashboard(orders, today);
  const vehicleName = (order: Order) => {
    const vehicle = vehicles.find((v) => v.id === order.veiculoId);
    return vehicle ? `${vehicle.marca} ${vehicle.modelo}` : "Veículo não informado";
  };
  return <div className="dashboard">
    <div className="page-heading">
      <div><div className="eyebrow">SUA OFICINA, EM PERSPECTIVA</div>
        <h1>Um dia bem organizado.</h1><p>O que precisa da sua atenção, em um só lugar.</p>
      </div>
      {canWrite && <button className="primary" onClick={newOrder}><Icon name="plus" size={18} />Abrir OS</button>}
    </div>
    <dl className="metrics" aria-label="Indicadores da oficina">
      <Metric icon="work" label="OS em andamento" value={String(dashboard.ongoing.length).padStart(2, "0")} detail="Do recebimento ao teste" />
      <Metric icon="clock" label="Aguardando aprovação" value={String(dashboard.waiting.length).padStart(2, "0")} detail="Aguardam decisão do cliente" />
      <Metric icon="good" label="Veículos prontos" value={String(dashboard.ready.length).padStart(2, "0")} detail="Serviços concluídos" />
      <Metric icon="parking" label="Ocupação do pátio" value="—" detail="Capacidade não disponível" />
    </dl>

    <section className="recovery-feature" aria-labelledby="recovery-title">
      <div className="recovery-copy"><span className="section-kicker"><Icon name="recovery" size={18} />RELACIONAMENTO QUE GERA RETORNO</span>
        <h2 id="recovery-title">Dinheiro Esquecido</h2>
        <p>Sua oficina já tem clientes.<br />Faça eles voltarem.</p>
        <button onClick={viewRecovery}>Explorar oportunidades<Icon name="forward" size={18} /></button>
      </div>
      <div className="recovery-amount"><span>Potencial de recuperação</span>
        <strong>Em apuração</strong>
        <p>Oportunidades ainda não classificadas.<br />O total será exibido quando houver uma origem verificável.</p>
        <div className="pending-origin"><Icon name="orders" size={18} /><span><b>{money(dashboard.pendingTotal)}</b> em {dashboard.pendingBudgets.length} orçamento(s) aguardando resposta. <span>Esse valor não representa receita recuperada.</span></span></div>
      </div>
    </section>

    <div className="dashboard-columns">
      <section className="surface priorities" aria-labelledby="priorities-title">
        <header className="surface-heading"><div><h2 id="priorities-title">Prioridades do dia <span className="count">{dashboard.priorities.length}</span></h2><p>Próximos passos para o trabalho avançar.</p></div><Icon name="orders" /></header>
        {dashboard.priorities.length === 0 ? <Empty title="Tudo em ordem por aqui">Nenhuma pendência identificada nas ordens atuais.</Empty> :
          <ul className="priority-list">{dashboard.priorities.map(({ order, reason, overdue }) => <li key={order.id}>
            <span className={`priority-symbol ${overdue ? "critical" : "attention"}`}><Icon name={overdue ? "critical" : "clock"} size={19} /></span>
            <div><span className="item-eyebrow">OS #{order.numero}</span><h3>{vehicleName(order)}</h3><p>{reason}</p></div>
            <button className="icon-button" aria-label={`Abrir OS ${order.numero}`} onClick={() => openOrder(order.id)}><Icon name="arrow" size={18} /></button>
          </li>)}</ul>}
        <footer className="surface-footer"><button className="text-action" onClick={viewOrders}>Ver todas as ordens<Icon name="forward" size={16} /></button></footer>
      </section>
      <section className="surface" aria-labelledby="entries-title">
        <header className="surface-heading"><div><h2 id="entries-title">Entradas de hoje</h2><p>Recebimentos registrados na oficina.</p></div><Icon name="calendar" size={20} /></header>
        {dashboard.entries.length === 0 ? <Empty title="Nenhuma entrada hoje">As OS recebidas hoje aparecerão aqui.</Empty> :
          <ul className="entry-list">{dashboard.entries.map((order) => <li key={order.id}>
            <time dateTime={order.criadoEm}>{new Date(order.criadoEm).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" })}</time>
            <button onClick={() => openOrder(order.id)}><strong>{vehicleName(order)}</strong><span>{clients.find((c) => c.id === order.clienteId)?.nome ?? "Cliente não informado"} · OS #{order.numero}</span></button>
          </li>)}</ul>}
        <footer className="surface-footer"><Icon name="info" size={15} /><span>Agendamentos futuros ainda não disponíveis.</span></footer>
      </section>
    </div>
    <section className="surface recent-orders" aria-labelledby="orders-title">
      <header className="surface-heading"><div><h2 id="orders-title">A operação em movimento</h2><p>Consulte o andamento e a previsão de cada serviço.</p></div><button className="text-action" onClick={viewOrders}>Todas as OS<Icon name="arrow" size={16} /></button></header>
      {orders.length === 0 ? <Empty title="Nenhuma ordem de serviço">Abra a primeira OS para acompanhar a operação.</Empty> :
        <ul className="operation-list">{orders.slice(0, 4).map((order) => <li key={order.id}>
          <button className="order-identity" onClick={() => openOrder(order.id)}><span className="item-eyebrow">#{order.numero}</span><strong>{vehicleName(order)}</strong><span>{vehicles.find((v) => v.id === order.veiculoId)?.placa}</span></button>
          <Badge status={order.status} /><span className="delivery">Previsão <b>{date(order.previsaoEntrega, true)}</b></span>
          <button className="icon-button" aria-label={`Consultar OS ${order.numero}`} onClick={() => openOrder(order.id)}><Icon name="arrow" size={17} /></button>
        </li>)}</ul>}
    </section>
  </div>;
}
