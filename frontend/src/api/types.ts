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
  totalPaginas: number;
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

export interface Usuario {
  id: string;
  nome: string;
  email: string;
  papel: Papel;
  ativo: boolean;
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
  /** Código estável do erro; prefira este valor a `detail` para decidir o que fazer. */
  code?: string;
  timestamp?: string;
  requestId?: string;
  errors?: CampoInvalido[];
}

export interface CampoInvalido {
  field: string;
  message: string;
}

export interface OrcamentosPendentes {
  quantidade: number;
  total: number;
}

export interface Dashboard {
  geradoEm: string;
  porStatus: Record<StatusOs, number>;
  emAndamento: number;
  prontas: number;
  concluidasSeteDias: number;
  entradasHoje: number;
  atrasadas: number;
  semResponsavel: number;
  orcamentosAguardandoDecisao: OrcamentosPendentes;
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

export interface ChecklistItem {
  id: string;
  descricao: string;
  condicao: string;
  observacao: string | null;
}

export interface Checklist {
  id: string;
  observacoes: string | null;
  itens: ChecklistItem[];
}

export interface DiagnosticoInput {
  descricao: string;
  classificacao: Classificacao;
}

export interface Diagnostico {
  id: string;
  descricao: string;
  classificacao: Classificacao;
  criadoEm: string;
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

export interface OrcamentoItem {
  id: string;
  tipo: "PECA" | "SERVICO";
  descricao: string;
  quantidade: number;
  valorUnitario: number;
  subtotal: number;
}

export interface OrcamentoVersao {
  id: string;
  numero: number;
  observacoes: string | null;
  total: number;
  criadoEm: string;
  itens: OrcamentoItem[];
  decisao: { aprovado: boolean; criadoEm: string; canal: string } | null;
}

export interface Evento {
  id: string;
  tipo: string;
  descricao: string;
  origem: string;
  autorId: string | null;
  criadoEm: string;
}
