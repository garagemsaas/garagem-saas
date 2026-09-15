export type Papel = "OWNER" | "ATENDENTE" | "MECANICO";

export type StatusOs =
  | "RECEBIDO"
  | "DIAGNOSTICO"
  | "ORCAMENTO"
  | "AGUARDANDO_APROVACAO"
  | "EM_MANUTENCAO"
  | "AGUARDANDO_PECA"
  | "TESTE"
  | "PRONTO";

export type Classificacao = "BOM" | "ATENCAO" | "CRITICO";

export interface Sessao {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  oficinaId: string;
  usuarioId: string;
  nome: string;
  papel: Papel;
}

export interface Pagina<T> {
  itens: T[];
  pagina: number;
  tamanho: number;
  total: number;
}

export interface Cliente {
  id: string;
  nome: string;
  telefone: string;
  email: string | null;
  revisao: number;
}

export interface Veiculo {
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

export interface OrdemServico {
  id: string;
  numero: number;
  veiculoId: string;
  clienteId: string;
  mecanicoId: string | null;
  status: StatusOs;
  kmEntrada: number;
  relato: string;
  criadoEm: string;
  previsaoEntrega: string | null;
  concluidaEm: string | null;
  revisao: number;
}

export interface LoginInput {
  oficina: string;
  email: string;
  senha: string;
}

export interface ApiProblem {
  status?: number;
  detail?: string;
  title?: string;
}

export interface ChecklistItemInput {
  descricao: string;
  condicao: string;
  observacao?: string;
}

export interface ChecklistInput {
  observacoes?: string;
  itens: ChecklistItemInput[];
}

export interface DiagnosticoInput {
  descricao: string;
  classificacao: Classificacao;
}

export interface OrcamentoItemInput {
  tipo: "PECA" | "SERVICO";
  descricao: string;
  quantidade: number;
  valorUnitario: number;
}

export interface OrcamentoVersaoInput {
  observacoes?: string;
  itens: OrcamentoItemInput[];
}
