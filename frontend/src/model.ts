export type Role = "OWNER" | "ATENDENTE" | "MECANICO";
export const roles: Record<Role, string> = {
  OWNER: "Proprietário",
  ATENDENTE: "Atendente",
  MECANICO: "Mecânico",
};
export const statuses = {
  RECEBIDO: "Recebido",
  DIAGNOSTICO: "Diagnóstico",
  ORCAMENTO: "Orçamento",
  AGUARDANDO_APROVACAO: "Aguardando aprovação",
  EM_MANUTENCAO: "Em manutenção",
  AGUARDANDO_PECA: "Aguardando peça",
  TESTE: "Em teste",
  PRONTO: "Pronto",
};
export type Status = keyof typeof statuses;
export type Classification = "OK" | "ACOMPANHAR" | "TROCAR";
export interface Client {
  id: string;
  nome: string;
  telefone: string;
  email: string;
  revisao: number;
}
export interface Vehicle {
  id: string;
  clienteId: string;
  placa: string;
  marca: string;
  modelo: string;
  ano: number;
  km: number;
  cor: string;
  revisao: number;
}
export interface User {
  id: string;
  nome: string;
  email: string;
  papel: Role;
  ativo: boolean;
}
export interface CheckItem {
  id: string;
  descricao: string;
  condicao: string;
  observacao: string;
}
export interface Diagnosis {
  id: string;
  descricao: string;
  classificacao: Classification;
  criadoEm: string;
}
export interface BudgetItem {
  id: string;
  tipo: "PECA" | "SERVICO";
  descricao: string;
  quantidade: number;
  valorUnitario: number;
  subtotal: number;
}
export interface Version {
  id: string;
  numero: number;
  observacoes: string;
  total: number;
  criadoEm: string;
  itens: BudgetItem[];
  decisao?: { aprovado: boolean; criadoEm: string; canal: string };
}
export interface Photo {
  id: string;
  finalidade: string;
  descricao: string;
  url: string;
  vinculo?: string;
}
export interface Order {
  id: string;
  numero: number;
  veiculoId: string;
  clienteId: string;
  mecanicoId: string;
  status: Status;
  kmEntrada: number;
  relato: string;
  criadoEm: string;
  previsaoEntrega: string;
  revisao: number;
  checklist?: { observacoes: string; itens: CheckItem[] };
  diagnosticos: Diagnosis[];
  versoes: Version[];
  fotos: Photo[];
  timeline: {
    id: string;
    descricao: string;
    origem: string;
    criadoEm: string;
  }[];
link?: {
  ativo: boolean;
  expiraEm: string;
  id?: string;
  url?: string;
  token?: string;
};
}
export const uid = () => crypto.randomUUID();
export const val = (f: FormData, key: string) =>
  String(f.get(key) || "").trim();
export const now = () => new Date().toISOString();
export const money = (n: number) =>
  n.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
export const date = (s?: string, time = false) =>
  s
    ? new Date(s).toLocaleString("pt-BR", {
        day: "2-digit",
        month: "2-digit",
        ...(time
          ? { hour: "2-digit", minute: "2-digit" }
          : { year: "numeric" }),
      })
    : "Não informada";
export const number = (n: number) => n.toLocaleString("pt-BR");
export const transitions: Record<Status, Status[]> = {
  RECEBIDO: ["DIAGNOSTICO"],
  DIAGNOSTICO: ["ORCAMENTO"],
  ORCAMENTO: ["AGUARDANDO_APROVACAO"],
  AGUARDANDO_APROVACAO: ["ORCAMENTO"],
  EM_MANUTENCAO: ["AGUARDANDO_PECA", "TESTE"],
  AGUARDANDO_PECA: ["EM_MANUTENCAO"],
  TESTE: ["PRONTO"],
  PRONTO: [],
};
