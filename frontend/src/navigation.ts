export type Page = "overview" | "orders" | "clients" | "vehicles" | "team";

/**
 * Como cada área se chama para quem usa. Os identificadores continuam em inglês por serem chave de
 * rota e de ícone; o que a pessoa lê é outra coisa — "Ordem de Serviço" é vocabulário de sistema,
 * e quem trabalha na oficina chama de serviço.
 */
export const labels: Record<Page, string> = {
  overview: "Início", orders: "Serviços", clients: "Clientes", vehicles: "Veículos", team: "Usuários",
};
