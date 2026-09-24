import { useEffect, useState } from 'react';
import { Form } from './forms';
import { Drawer, Field, Pager } from './ui';
import { PageState } from './PageState';
import { ApiError } from './api';
import { val } from './model';
import type { Empresa } from './branding-context';
import './revenda/revenda.css';

interface Sessao { accessToken: string; nome: string; papel: 'DESENVOLVEDOR' | 'ADMIN_PLATAFORMA' }
interface Resumo { id: string; nome: string; slug: string; status: string; operacao: string; revisao: number }
interface Lista { itens: Resumo[]; total: number }
interface Detalhe { empresa: Resumo; identidade: Empresa }
const base = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, '') ?? '';
async function request<T>(session: Sessao | undefined, path: string, method = 'GET', body?: unknown, binary = false): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (session) headers.Authorization = `Bearer ${session.accessToken}`;
  if (body !== undefined && !(body instanceof FormData)) headers['Content-Type'] = 'application/json';
  let response: Response;
  try { response = await fetch(`${base}/api/v1/plataforma${path}`, { method, headers, cache: 'no-store', credentials: 'omit', signal: AbortSignal.timeout(30000), body: body === undefined ? undefined : body instanceof FormData ? body : JSON.stringify(body) }); }
  catch { throw new Error('Não foi possível conectar. Tente novamente.'); }
  if (!response.ok) { const problem = await response.json().catch(() => ({})); if (session && response.status === 401) window.dispatchEvent(new Event('platform-session-expired')); throw new ApiError(response.status, problem.detail ?? 'Não foi possível concluir esta ação.', problem); }
  return response.status === 204 ? undefined as T : binary ? await response.blob() as T : response.json();
}
function LogoPreview({ session, companyId, imageId }: { session: Sessao; companyId: string; imageId: string }) {
  const [state, setState] = useState<{ id: string; url?: string; error?: string }>(); const [attempt, setAttempt] = useState(0);
  useEffect(() => { let active = true; let url: string | undefined;
    request<Blob>(session, `/empresas/${companyId}/imagens/logo/${imageId}`, 'GET', undefined, true).then(blob => { if (active) { url = URL.createObjectURL(blob); setState({ id: imageId, url }); } }).catch(e => { if (active) setState({ id: imageId, error: e.message }); });
    return () => { active = false; if (url) URL.revokeObjectURL(url); };
  }, [session, companyId, imageId, attempt]);
  return state?.id === imageId && state.url ? <img src={state.url} alt="Logotipo atual da empresa" className="admin-logo-preview" /> : state?.id === imageId && state.error ? <p role="alert">{state.error} <button onClick={() => setAttempt(n => n + 1)}>Tentar novamente</button></p> : <p role="status">Carregando logotipo…</p>;
}
export default function PlatformAdmin() {
  const [session, setSession] = useState<Sessao>(); const [list, setList] = useState<Lista>(); const [error, setError] = useState('');
  const [page, setPage] = useState(0); const [query, setQuery] = useState(''); const [version, setVersion] = useState(0);
  const [selected, setSelected] = useState<Detalhe>(); const [panel, setPanel] = useState('');
  const [history, setHistory] = useState<{ id: number; acao: string; descricao: string; autor: string; criado_em: string }[]>();
  const reload = () => setVersion(n => n + 1); const close = () => { setPanel(''); setSelected(undefined); setHistory(undefined); };
  const done = () => { close(); reload(); };
  useEffect(() => { const expired = () => { setSession(undefined); setSelected(undefined); setPanel(''); setHistory(undefined); setList(undefined); setError('Sua sessão expirou. Entre novamente.'); }; window.addEventListener('platform-session-expired', expired); return () => window.removeEventListener('platform-session-expired', expired); }, []);
  useEffect(() => {
    if (!session) return;
    let active = true;
    // oxlint-disable-next-line react/set-state-in-effect -- lifecycle of the administrative query
    setList(undefined); setError('');
    const timer = setTimeout(() => { request<Lista>(session, `/empresas?pagina=${page}&tamanho=10&busca=${encodeURIComponent(query)}`)
      .then(result => { if (active) setList(result); }).catch(e => { if (active) { setError(e.message); if (e.status === 401) setSession(undefined); } }); }, 200);
    return () => { active = false; clearTimeout(timer); };
  }, [session, page, query, version]);
  async function open(id: string, next: string) { setError(''); try { const result = await request<Detalhe>(session, `/empresas/${id}`); setSelected(result); setPanel(next);
    if (next === 'historico') setHistory(await request(session, `/empresas/${id}/auditoria`));
  } catch (e) { setError(e instanceof Error ? e.message : 'Não foi possível abrir a empresa.'); } }
  const company = selected?.empresa; const brand = selected?.identidade.branding;
  if (!session) return <main className="platform-login"><h1>Administração da plataforma</h1><p>Acesso restrito à equipe da plataforma.</p>{error && <p role="alert">{error}</p>}
    <Form close={() => window.location.assign('/')} submit="Entrar" save={async f => { const result = await request<Sessao>(undefined, '/auth/login', 'POST', { email: val(f, 'email'), senha: val(f, 'senha') }); setSession(result); setError(''); }}>
      <Field label="E-mail"><input name="email" type="email" required maxLength={254} autoComplete="username" /></Field><Field label="Senha"><input name="senha" type="password" required maxLength={72} autoComplete="current-password" /></Field></Form></main>;
  return <main className="platform-admin"><div className="page-heading"><div><h1>Empresas</h1><p>{session.nome} · {session.papel === 'DESENVOLVEDOR' ? 'Desenvolvedor' : 'Administrador da plataforma'}</p></div><div className="dealer-actions"><button className="primary" onClick={() => setPanel('nova')}>Cadastrar empresa</button><button onClick={async () => { try { await request(session, '/auth/logout', 'POST'); setSession(undefined); setList(undefined); close(); } catch (e) { setError(e instanceof Error ? e.message : 'Não foi possível sair.'); } }}>Sair</button></div></div>
    <Field label="Buscar empresa"><input type="search" maxLength={160} value={query} onChange={e => { setQuery(e.target.value); setPage(0); }} /></Field>
    {error && <PageState state="error" title="Não foi possível concluir" retry={reload}>{error}</PageState>}
    {!list && !error ? <PageState state="loading" title="Carregando empresas" /> : list && <><div className="dealer-cards">{list.itens.map(e => <article className="dealer-card" key={e.id}><h2>{e.nome}</h2><p>{e.slug} · {e.operacao === 'REVENDA' ? 'Revenda' : 'Oficina'}</p><p>{e.status === 'ATIVA' ? 'Ativa' : e.status === 'SUSPENSA' ? 'Suspensa' : 'Inativa'}</p><div className="dealer-actions"><button onClick={() => void open(e.id, 'editar')}>Administrar</button>{session.papel === 'DESENVOLVEDOR' && <button onClick={() => void open(e.id, 'identidade')}>Identidade</button>}<button onClick={() => void open(e.id, 'historico')}>Histórico</button></div></article>)}</div>{!list.total && <PageState state="empty" title="Nenhuma empresa encontrada" />}<Pager page={page} setPage={setPage} total={list.total} /></>}
    {(panel === 'nova' || panel === 'editar' && company) && <Drawer title={company ? 'Administrar empresa' : 'Cadastrar empresa'} close={close}><Form close={close} confirmation={company ? 'Salvar situação e operação? As sessões da empresa serão atualizadas e os dados anteriores serão preservados.' : undefined} save={async f => {
      const common = { nome: val(f, 'nome'), operacao: val(f, 'operacao') };
      await request(session, `/empresas${company ? `/${company.id}` : ''}`, company ? 'PUT' : 'POST', company ? { ...common, status: val(f, 'status'), revisao: company.revisao, motivo: val(f, 'motivo') } : { ...common, slug: val(f, 'slug'), proprietario: val(f, 'proprietario'), email: val(f, 'email'), senha: val(f, 'senha') }); done();
    }}><Field label="Nome empresarial"><input name="nome" required maxLength={160} defaultValue={company?.nome} /></Field>
      {!company && <Field label="Identificador para entrar na empresa"><input name="slug" required pattern="[a-z0-9-]{3,80}" minLength={3} maxLength={80} placeholder="minha-empresa" /></Field>}
      <Field label="Operação"><select name="operacao" defaultValue={company?.operacao ?? 'OFICINA'}><option value="OFICINA">Oficina</option><option value="REVENDA">Garagem / revenda</option></select></Field>
      {company ? <><Field label="Situação"><select name="status" defaultValue={company.status}><option value="ATIVA">Ativa</option><option value="SUSPENSA">Suspensa</option><option value="INATIVA">Inativa</option></select></Field><Field label="Motivo da alteração"><textarea name="motivo" required maxLength={500} /></Field></> : <><Field label="Nome do proprietário"><input name="proprietario" required maxLength={160} /></Field><Field label="E-mail do proprietário"><input name="email" type="email" required maxLength={254} /></Field><Field label="Senha inicial"><input name="senha" type="password" required minLength={12} maxLength={72} autoComplete="new-password" /></Field></>}
    </Form></Drawer>}
    {panel === 'identidade' && company && brand && session.papel === 'DESENVOLVEDOR' && <Drawer title={`Identidade · ${company.nome}`} close={close}>
      <Form close={close} save={async f => { await request(session, `/empresas/${company.id}/identidade`, 'PUT', { nomeExibicao: val(f, 'nomeExibicao'), telefone: val(f, 'telefone') || null, email: val(f, 'email') || null, contato: val(f, 'contato') || null, revisao: brand.revisao }); done(); }}>
        <Field label="Nome exibido"><input name="nomeExibicao" required maxLength={160} defaultValue={brand.nomeExibicao} /></Field><Field label="Telefone"><input name="telefone" type="tel" maxLength={40} defaultValue={brand.telefone ?? ''} /></Field><Field label="E-mail"><input name="email" type="email" maxLength={254} defaultValue={brand.email ?? ''} /></Field><Field label="Informações de contato"><textarea name="contato" maxLength={500} defaultValue={brand.contato ?? ''} /></Field></Form>
      <h2>Logotipo</h2><p>{brand.logoId ? 'A empresa tem um logotipo cadastrado.' : 'Sem logotipo: o nome da empresa será exibido.'}</p>
      {brand.logoId && <LogoPreview session={session} companyId={company.id} imageId={brand.logoId} />}
      <Form close={close} submit="Salvar logotipo" save={async f => { const file = f.get('arquivo'); if (!(file instanceof File) || file.size === 0) throw new Error('Escolha uma imagem.'); if (file.size > 2097152) throw new Error('A imagem deve ter até 2 MB.'); f.set('revisao', String(brand.revisao)); await request(session, `/empresas/${company.id}/imagens/logo`, 'POST', f); done(); }}><Field label="Imagem PNG ou JPEG, até 2 MB"><input name="arquivo" type="file" accept="image/png,image/jpeg" required /></Field></Form>
      {brand.logoId && <Form close={close} submit="Remover logotipo" confirmation="Remover o logotipo e exibir o nome da empresa?" save={async () => { await request(session, `/empresas/${company.id}/imagens/logo?revisao=${brand.revisao}`, 'DELETE'); done(); }}><p>O nome continua visível quando não há imagem.</p></Form>}
    </Drawer>}
    {panel === 'historico' && company && <Drawer title={`Histórico · ${company.nome}`} close={close}>{!history ? <PageState state="loading" title="Carregando histórico" /> : history.length ? <ol>{history.map(e => <li key={e.id}><strong>{e.descricao}</strong><p>{e.autor} · {new Date(e.criado_em).toLocaleString('pt-BR')}</p></li>)}</ol> : <p>Nenhuma alteração registrada.</p>}<p>Exibindo as 20 alterações mais recentes.</p></Drawer>}
  </main>;
}
