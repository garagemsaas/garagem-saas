import { useEffect, useState, useSyncExternalStore } from 'react';
import { api, ApiError } from './api';
import { PageState } from './PageState';
import type { Status, Version } from './model';
import { date, money } from './model';
import { Badge, Brand } from './ui';
import { BudgetTable } from './OrderDetail';

interface PublicSummary { numero: number; status: Status; veiculo: string; previsaoEntrega: string | null; orcamento: Version | null }
function subscribeToken(listener: () => void) {
  window.addEventListener('hashchange', listener);
  return () => window.removeEventListener('hashchange', listener);
}
export function PublicRoute() {
  const token = useSyncExternalStore(subscribeToken, () => window.location.hash.slice(1));
  return <PublicOrder key={token} token={token} />;
}
export default function PublicOrder({ token }: { token: string }) {
  const [data, setData] = useState<PublicSummary>();
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [decision, setDecision] = useState<boolean | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [invalid, setInvalid] = useState(false);
  const path = `/publico/${encodeURIComponent(token)}`;
  useEffect(() => {
    let active = true;
    if (!/^[A-Za-z0-9_-]{43}$/.test(token)) return;
    api<PublicSummary>(path).then(value => { if (active) setData(value); }).catch(e => { if (active) { setError(e.message); setInvalid(e instanceof ApiError && e.status === 404); } });
    return () => { active = false; };
  }, [path, token, attempt]);
  async function decide() {
    if (!data?.orcamento || decision === null || busy) return;
    setBusy(true); setError('');
    try {
      setData(await api<PublicSummary>(`${path}/decisao`, 'POST', { versaoId: data.orcamento.id, aprovado: decision }));
      setNotice(decision ? 'Aprovação registrada.' : 'Recusa registrada. A oficina poderá preparar uma nova versão.');
      setDecision(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Não foi possível registrar a decisão.');
      setDecision(null);
      if (e instanceof ApiError && e.status === 404) { setInvalid(true); setData(undefined); }
      if (e instanceof ApiError && e.status === 409) {
        try { setData(await api<PublicSummary>(path)); } catch { setData(undefined); }
      }
    }
    finally { setBusy(false); }
  }
  if (invalid || !/^[A-Za-z0-9_-]{43}$/.test(token)) return <main className="public-order"><Brand /><PageState state="error" title="Link indisponível">Este link é inválido, expirou ou foi revogado. Solicite um novo link à oficina.</PageState></main>;
  return <main className="detail-panel public-order">
    <Brand />
    {error && data && <p role="alert" className="error">{error}</p>}
    {!data ? error ? <PageState state="error" title="Não foi possível carregar o acompanhamento" retry={() => { setError(''); setAttempt(n => n + 1); }}>{error}</PageState> : <PageState state="loading" title="Carregando acompanhamento" /> : <>
      <div className="public-heading"><h1>Ordem de Serviço #{data.numero}</h1><p>{data.veiculo}</p><Badge status={data.status} /><p>Previsão: {date(data.previsaoEntrega ?? undefined, true)}</p></div>
      {notice && <p role="status" className="notice">{notice}</p>}
      {data.orcamento ? <><h2>Orçamento · versão {data.orcamento.numero}</h2><BudgetTable version={data.orcamento} />
        {data.orcamento.decisao ? <p>Orçamento {data.orcamento.decisao.aprovado ? 'aprovado' : 'recusado'} em {date(data.orcamento.decisao.criadoEm, true)}.</p> : data.status === 'AGUARDANDO_APROVACAO' && <>
          {decision === null ? <div className="form-actions"><button onClick={() => setDecision(false)}>Recusar orçamento</button><button className="primary" onClick={() => setDecision(true)}>Aprovar {money(data.orcamento.total)}</button></div> :
            <div className="confirmation"><h3>Confirmar {decision ? 'aprovação integral' : 'recusa do orçamento'}?</h3><p>Versão {data.orcamento.numero} · {money(data.orcamento.total)}. A decisão ficará registrada e não poderá ser alterada para esta versão.</p><div className="form-actions"><button disabled={busy} onClick={() => setDecision(null)}>Voltar</button><button className="primary" disabled={busy} onClick={decide}>Confirmar {decision ? 'aprovação' : 'recusa'}</button></div></div>}
        </>}
      </> : <PageState state="empty" title="Orçamento em preparação">A oficina disponibilizará os valores quando a avaliação estiver concluída.</PageState>}
      <button disabled={busy} onClick={async () => { setBusy(true); try { setData(await api<PublicSummary>(path)); setDecision(null); setError(''); } catch (e) { setError(e instanceof Error ? e.message : 'Falha ao atualizar.'); if (e instanceof ApiError && e.status === 404) { setInvalid(true); setData(undefined); setDecision(null); } } finally { setBusy(false); } }}>Atualizar acompanhamento</button>
    </>}
  </main>;
}
