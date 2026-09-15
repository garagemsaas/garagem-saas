import { clearSession, getAccessToken, getSession, setSession } from "./session";
import type {
  ApiProblem,
  Checklist,
  ChecklistInput,
  Cliente,
  Diagnostico,
  DiagnosticoInput,
  LoginInput,
  Evento,
  OrdemServico,
  OrcamentoVersao,
  OrcamentoVersaoInput,
  Pagina,
  Sessao,
  StatusOs,
  Usuario,
  Veiculo,
} from "./types";

const baseUrl = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, "") ?? "";

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

function messageForStatus(status: number, detail?: string): string {
  if (detail) return detail;
  if (status === 401) return "Sua sessão expirou. Entre novamente.";
  if (status === 403) return "Seu papel não permite esta ação.";
  if (status === 409) return "O registro mudou. Atualize a página e tente novamente.";
  if (status === 413) return "O arquivo enviado excede o limite permitido.";
  if (status >= 500) return "Não foi possível concluir a operação agora.";
  return "Confira os dados enviados e tente novamente.";
}

async function parseProblem(response: Response): Promise<string> {
  try {
    const problem = (await response.json()) as ApiProblem;
    return messageForStatus(response.status, problem.detail ?? problem.title);
  } catch {
    return messageForStatus(response.status);
  }
}

async function request<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  if (!baseUrl) throw new ApiError(0, "A URL da API ainda não foi configurada.");
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (init.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  const token = getAccessToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const response = await fetch(`${baseUrl}${path}`, { ...init, headers });
  if (response.ok) {
    if (response.status === 204) return undefined as T;
    return (await response.json()) as T;
  }
  if (response.status === 401 && retry && getSession()?.refreshToken) {
    try {
      const session = await request<Sessao>("/api/v1/auth/refresh", {
        method: "POST",
        body: JSON.stringify({ oficinaId: getSession()?.oficinaId, refreshToken: getSession()?.refreshToken }),
      }, false);
      setSession(session);
      return request<T>(path, init, false);
    } catch {
      clearSession();
    }
  }
  throw new ApiError(response.status, await parseProblem(response));
}

export const api = {
  isConfigured: Boolean(baseUrl),
  login(input: LoginInput): Promise<Sessao> {
    return request<Sessao>("/api/v1/auth/login", { method: "POST", body: JSON.stringify(input) });
  },
  async logout(): Promise<void> {
    const session = getSession();
    try {
      if (session) await request<void>("/api/v1/auth/logout", {
        method: "POST",
        body: JSON.stringify({ oficinaId: session.oficinaId, refreshToken: session.refreshToken }),
      });
    } finally {
      clearSession();
    }
  },
  listClients(search = "", page = 0, size = 20): Promise<Pagina<Cliente>> {
    return request<Pagina<Cliente>>(`/api/v1/clientes?busca=${encodeURIComponent(search)}&pagina=${page}&tamanho=${size}`);
  },
  listVehicles(search = "", page = 0, size = 20): Promise<Pagina<Veiculo>> {
    return request<Pagina<Veiculo>>(`/api/v1/veiculos?busca=${encodeURIComponent(search)}&pagina=${page}&tamanho=${size}`);
  },
  listOrders(page = 0, size = 20): Promise<Pagina<OrdemServico>> {
    return request<Pagina<OrdemServico>>(`/api/v1/ordens-servico?pagina=${page}&tamanho=${size}`);
  },
  getOrder(id: string): Promise<OrdemServico> {
    return request<OrdemServico>(`/api/v1/ordens-servico/${id}`);
  },
  getChecklist(id: string): Promise<Checklist> {
    return request<Checklist>(`/api/v1/ordens-servico/${id}/checklist`);
  },
  getDiagnostics(id: string): Promise<Diagnostico[]> {
    return request<Diagnostico[]>(`/api/v1/ordens-servico/${id}/diagnosticos`);
  },
  getBudgetVersions(id: string): Promise<OrcamentoVersao[]> {
    return request<OrcamentoVersao[]>(`/api/v1/ordens-servico/${id}/orcamento/versoes`);
  },
  getTimeline(id: string): Promise<Evento[]> {
    return request<Evento[]>(`/api/v1/ordens-servico/${id}/timeline`);
  },
  listUsers(page = 0, size = 100): Promise<Pagina<Usuario>> {
    return request<Pagina<Usuario>>(`/api/v1/usuarios?pagina=${page}&tamanho=${size}`);
  },
  updateOrderStatus(id: string, status: StatusOs, revisao: number): Promise<OrdemServico> {
    return request<OrdemServico>(`/api/v1/ordens-servico/${id}/status`, { method: "POST", body: JSON.stringify({ status, revisao }) });
  },
  createChecklist(id: string, input: ChecklistInput): Promise<Checklist> {
    return request<Checklist>(`/api/v1/ordens-servico/${id}/checklist`, { method: "POST", body: JSON.stringify(input) });
  },
  addDiagnostic(id: string, input: DiagnosticoInput): Promise<Diagnostico> {
    return request<Diagnostico>(`/api/v1/ordens-servico/${id}/diagnosticos`, { method: "POST", body: JSON.stringify(input) });
  },
  createBudgetVersion(id: string, input: OrcamentoVersaoInput): Promise<OrcamentoVersao> {
    return request<OrcamentoVersao>(`/api/v1/ordens-servico/${id}/orcamento/versoes`, { method: "POST", body: JSON.stringify(input) });
  },
};
