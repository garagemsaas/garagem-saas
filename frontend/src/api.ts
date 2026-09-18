import type { Client, Vehicle, User, Order, Role, Diagnosis, Photo } from './model';
import type { ApiProblem, CampoInvalido } from './api/types';
import type { Dashboard } from './api/types';
import type { Page } from './navigation';

const baseUrl = (import.meta.env?.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, '') ?? '';

export interface Session {
  accessToken: string; refreshToken: string; expiresIn: number;
  oficinaId: string; usuarioId: string; nome: string; papel: Role;
}
export class ApiError extends Error {
  status: number;
  code?: string;
  requestId?: string;
  errors: CampoInvalido[];
  constructor(status: number, message: string, problem?: ApiProblem) {
    super(message); this.name = 'ApiError'; this.status = status;
    this.code = problem?.code; this.requestId = problem?.requestId;
    this.errors = problem?.errors ?? [];
  }
}
let session: Session | null = null;
let generation = 0;
let renewal: Promise<void> | null = null;
export function setApiSession(value: Session | null) { generation++; session = value; renewal = null; }
export function currentSession() { return session; }

async function request(path: string, method = 'GET', body?: unknown, retry = true): Promise<Response> {
  const start = generation;
  const authenticated = !path.startsWith('/auth/') && !path.startsWith('/publico/');
  const accessToken = session?.accessToken;
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (authenticated && session) headers.Authorization = `Bearer ${session.accessToken}`;
  if (body !== undefined && !(body instanceof FormData)) headers['Content-Type'] = 'application/json';
  let response: Response;
  try {
    response = await fetch(`${baseUrl}/api/v1${path}`, { method, headers, cache: 'no-store', signal: AbortSignal.timeout(30_000),
      body: body === undefined ? undefined : body instanceof FormData ? body : JSON.stringify(body) });
  } catch { throw new ApiError(0, 'Não foi possível conectar à API. Verifique a conexão e tente novamente.'); }
  if (authenticated && start !== generation) throw new ApiError(401, 'A sessão foi encerrada.');
  if (response.status === 401 && authenticated && session && retry) {
    // A slower request may return 401 after another request already rotated the token.
    if (accessToken !== session.accessToken) return request(path, method, body, false);
    if (!renewal) {
      const previous = session;
      renewal = api<Session>('/auth/refresh', 'POST', { oficinaId: previous.oficinaId, refreshToken: previous.refreshToken })
        .then(value => { if (start === generation) { session = value; if (typeof window !== 'undefined') window.dispatchEvent(new Event('session-updated')); } })
        .catch(error => { if (start === generation) { setApiSession(null); if (typeof window !== 'undefined') window.dispatchEvent(new Event('session-expired')); } throw error; })
        .finally(() => { if (start === generation) renewal = null; });
    }
    await renewal;
    if (start !== generation) throw new ApiError(401, 'Entre novamente.');
    return request(path, method, body, false);
  }
  if (!response.ok) {
    if (response.status === 401 && authenticated && session) {
      setApiSession(null);
      if (typeof window !== 'undefined') window.dispatchEvent(new Event('session-expired'));
    }
    const problem = await response.json().catch(() => ({})) as ApiProblem;
    const fallback: Record<number, string> = {
      401: path === '/auth/login' ? 'Confira a oficina, o e-mail e a senha.' : 'Sua sessão expirou. Entre novamente.',
      402: 'O limite do seu plano foi atingido. Consulte Configurações › Plano e assinatura.',
      403: 'Seu papel não permite esta ação.',
      404: 'Este registro não está disponível. Atualize os dados.',
      409: 'O registro mudou. Atualize os dados antes de tentar novamente.',
      413: 'Selecione uma imagem de até 10 MB.',
      415: 'Selecione uma imagem PNG ou JPEG válida.',
      503: 'O serviço está temporariamente indisponível. Tente novamente em instantes.',
    };
    throw new ApiError(response.status, problem?.detail || fallback[response.status] || 'Não foi possível concluir a operação. Tente novamente.', {
      ...problem, requestId: problem?.requestId ?? response.headers.get('X-Request-Id') ?? undefined,
    });
  }
  return response;
}
export async function api<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const start = generation;
  const response = await request(path, method, body);
  let value: T;
  try { value = response.status === 204 ? undefined as T : await response.json() as T; }
  catch { throw new ApiError(response.status, 'A API retornou uma resposta inválida. Tente novamente.'); }
  if (!path.startsWith('/auth/') && !path.startsWith('/publico/') && start !== generation) throw new ApiError(401, 'A sessão foi encerrada.');
  return value;
}
export async function photoBlob(path: string) { return (await request(path)).blob(); }
export interface PageResult<T> { itens: T[]; pagina: number; tamanho: number; total: number }
export async function allPages<T>(path: string): Promise<T[]> {
  const result: T[] = [];
  for (let page = 0; ; page++) {
    const next = await api<PageResult<T>>(`${path}${path.includes('?') ? '&' : '?'}pagina=${page}&tamanho=100`);
    result.push(...next.itens);
    if (!next.itens.length || result.length >= next.total) return result;
  }
}
export const emptyData = () => ({ clientes: [] as Client[], veiculos: [] as Vehicle[], usuarios: [] as User[], ordens: [] as Order[], total: 0, dashboard: null as Dashboard | null });
export function listPage<T>(path: string, pagina = 0, busca = '', tamanho = 10) {
  const params = new URLSearchParams({ pagina: String(pagina), tamanho: String(tamanho) });
  if (busca.trim()) params.set('busca', busca.trim());
  return api<PageResult<T>>(`${path}${path.includes('?') ? '&' : '?'}${params}`);
}
export const diagnosisToApi = { OK: 'VERDE', ACOMPANHAR: 'AMARELO', TROCAR: 'VERMELHO' } as const;
const diagnosisFromApi = { VERDE: 'OK', AMARELO: 'ACOMPANHAR', VERMELHO: 'TROCAR' } as const;
export function orderSummary(o: Order): Order {
  return { ...o, mecanicoId: o.mecanicoId ?? '', previsaoEntrega: o.previsaoEntrega ?? '', diagnosticos: [], versoes: [], fotos: [], timeline: [] };
}
export async function loadData(page: Page = 'overview', pagina = 0, busca = '') {
  const data = emptyData();
  const path = { overview: '/ordens-servico', orders: '/ordens-servico', clients: '/clientes', vehicles: '/veiculos', team: '/usuarios' }[page];
  const result = await listPage<Client | Vehicle | User | Order>(path, pagina, busca);
  data.total = result.total;
  if (page === 'clients') data.clientes = result.itens as Client[];
  else if (page === 'vehicles') data.veiculos = result.itens as Vehicle[];
  else if (page === 'team') data.usuarios = result.itens as User[];
  else data.ordens = (result.itens as Order[]).map(orderSummary);
  if (page === 'overview') data.dashboard = await api<Dashboard>('/dashboard');
  if (page === 'orders' || page === 'overview') data.usuarios = await allPages<User>('/usuarios');
  const { clientes, veiculos, ordens: orders } = data;
  // Relações podem ter sido criadas por outro usuário entre leituras paginadas, então ainda é
  // preciso buscá-las. O que mudou é que as buscas vão juntas: antes era um `await` dentro do
  // laço, e uma página com dez OS custava até vinte idas e voltas em sequência.
  const faltandoVeiculo = [...new Set(orders.map(o => o.veiculoId).filter(id => !veiculos.some(v => v.id === id)))];
  const buscados = await Promise.all(faltandoVeiculo.map(id => api<Vehicle>(`/veiculos/${id}`)));
  veiculos.push(...buscados);
  // Clientes só depois dos veículos: um veículo recém-buscado pode trazer um cliente novo.
  const idsDeCliente = [...orders.map(o => o.clienteId), ...veiculos.map(v => v.clienteId)];
  const faltandoCliente = [...new Set(idsDeCliente.filter(id => !clientes.some(c => c.id === id)))];
  clientes.push(...await Promise.all(faltandoCliente.map(id => api<Client>(`/clientes/${id}`))));
  return data;
}
export async function loadOrder(id: string): Promise<Order> {
  const path = `/ordens-servico/${id}`;
  const [o, checklist, diagnostics, versoes, photos, timeline] = await Promise.all([
    api<Order>(path), api<Order['checklist']>(`${path}/checklist`).catch(e => { if (e instanceof ApiError && e.status === 404) return undefined; throw e; }),
    api<(Omit<Diagnosis, 'classificacao'> & { classificacao: keyof typeof diagnosisFromApi })[]>(`${path}/diagnosticos`),
    api<Order['versoes']>(`${path}/orcamento/versoes`),
    api<(Photo & { checklistItemId: string | null; diagnosticoItemId: string | null })[]>(`${path}/fotos`),
    api<Order['timeline']>(`${path}/timeline`)
  ]);
  return { ...orderSummary(o), checklist, versoes, timeline,
    diagnosticos: diagnostics.map(d => ({ ...d, classificacao: diagnosisFromApi[d.classificacao] })),
    fotos: photos.map(p => ({ ...p, url: `${path}/fotos/${p.id}/conteudo`, vinculo: p.checklistItemId || p.diagnosticoItemId || undefined })) };
}
