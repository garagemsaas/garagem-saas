import type { Order } from "./model.ts";

export function isSameDay(value: string, today: Date) {
  const date = new Date(value);
  return date.getFullYear() === today.getFullYear()
    && date.getMonth() === today.getMonth()
    && date.getDate() === today.getDate();
}

export function getDashboard(orders: Order[], today = new Date()) {
  const ongoing = orders.filter((order) => order.status !== "PRONTO");
  const waiting = orders.filter((order) => order.status === "AGUARDANDO_APROVACAO");
  const ready = orders.filter((order) => order.status === "PRONTO");
  const pendingBudgets = waiting.flatMap((order) => {
    const version = order.versoes.reduce<Order["versoes"][number] | undefined>(
      (latest, item) => !latest || item.numero > latest.numero ? item : latest, undefined,
    );
    return version && !version.decisao ? [{ order, version }] : [];
  });
  const priorities = ongoing.map((order) => {
    const overdue = Boolean(order.previsaoEntrega) && new Date(order.previsaoEntrega) < today;
    const reason = overdue ? "Previsão de entrega ultrapassada"
      : order.status === "AGUARDANDO_PECA" ? "Acompanhar chegada da peça"
      : order.status === "AGUARDANDO_APROVACAO" ? "Consultar aprovação do orçamento"
      : !order.mecanicoId ? "Definir responsável pelo serviço" : null;
    return { order, reason, overdue };
  }).filter((item) => item.reason !== null)
    .sort((a, b) => Number(b.overdue) - Number(a.overdue)
      || new Date(a.order.criadoEm).getTime() - new Date(b.order.criadoEm).getTime());
  return {
    ongoing, waiting, ready, pendingBudgets, priorities,
    pendingTotal: pendingBudgets.reduce((sum, item) => sum + item.version.total, 0),
    entries: orders.filter((order) => isSameDay(order.criadoEm, today))
      .sort((a, b) => new Date(a.criadoEm).getTime() - new Date(b.criadoEm).getTime()),
  };
}
