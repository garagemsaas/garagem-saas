import { useEffect, useState } from 'react';
import { api, ApiError } from './api';
import type { PageResult } from './api';
import { PageState } from './PageState';
import { Icon } from './ui';
import { date, money } from './model';
import type { Role } from './model';
import './Subscription.css';

const base = '/assinatura';

export interface PlanOption {
  id: string; codigo: string; nome: string; descricao: string;
  valorCentavos: number; moeda: string; periodicidade: 'MENSAL' | 'ANUAL';
  maxUsuarios: number; maxArmazenamentoBytes: number;
  maxOrdensServicoMes: number | null; maxVeiculos: number | null;
  ordem: number; atual: boolean;
}
export interface UsageLimit {
  chave: string; rotulo: string; usado: number; limite: number | null;
  usadoLegivel: string; limiteLegivel: string; percentual: number; atingido: boolean;
}
type Status = 'TRIAL' | 'ATIVA' | 'INADIMPLENTE' | 'SUSPENSA' | 'CANCELADA';
export interface SubscriptionInfo {
  id: string; plano: PlanOption; status: Status; situacao: string; permiteCrescer: boolean;
  provedor: string; periodoInicio: string; periodoFim: string; trialFim: string | null;
  inadimplenteDesde: string | null; suspensaEm: string | null;
  canceladaEm: string | null; cancelamentoEfetivoEm: string | null;
  cancelamentoMotivo: string | null; revisao: number;
}
interface Usage { geradoEm: string; plano: string; status: Status; limites: UsageLimit[] }
interface Detail { assinatura: SubscriptionInfo; consumo: Usage; planos: PlanOption[] }
interface BillingEvent {
  id: string; criadoEm: string; tipo: string; mensagem: string;
  provedor: string | null; metadados: string | null;
}

const statusLabels: Record<Status, string> = {
  TRIAL: 'Em avaliação', ATIVA: 'Ativa', INADIMPLENTE: 'Pagamento em atraso',
  SUSPENSA: 'Suspensa', CANCELADA: 'Cancelada',
};
// O preço só é exibido quando existe: zero aqui significa "ainda não definido comercialmente",
// não "gratuito", e inventar um número na tela seria pior do que assumir a pendência.
const price = (p: PlanOption) =>
  p.valorCentavos > 0
    ? `${money(p.valorCentavos / 100)} / ${p.periodicidade === 'ANUAL' ? 'ano' : 'mês'}`
    : 'Valor a definir';

const limitText = (value: number | null, singular: string, plural: string) =>
  value === null ? `${plural} ilimitados` : `${value} ${value === 1 ? singular : plural}`;

export default function Subscription({ role }: { role: Role }) {
  const [detail, setDetail] = useState<Detail | null>(null);
  const [events, setEvents] = useState<BillingEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState('');
  const [notice, setNotice] = useState('');
  const [attempt, setAttempt] = useState(0);
  const [reason, setReason] = useState('');
  const [confirming, setConfirming] = useState<'imediato' | 'periodo' | null>(null);
  const owner = role === 'OWNER';

  useEffect(() => {
    let active = true;
    // oxlint-disable-next-line react/set-state-in-effect -- API request lifecycle
    setLoading(true); setError('');
    Promise.all([api<Detail>(base), api<PageResult<BillingEvent>>(`${base}/eventos?pagina=0&tamanho=20`)])
      .then(([d, e]) => { if (active) { setDetail(d); setEvents(e.itens); } })
      .catch(err => { if (active) setError(err.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [attempt]);

  async function run(label: string, action: () => Promise<unknown>, done: string) {
    setBusy(label); setNotice(''); setError('');
    try {
      await action();
      setNotice(done);
      setAttempt(n => n + 1);
    } catch (e) {
      // 409 por revisão desatualizada é recarregável: o usuário decide de novo com o dado certo.
      setError(e instanceof ApiError ? e.message : 'Não foi possível concluir a operação.');
      if (e instanceof ApiError && e.status === 409) setAttempt(n => n + 1);
    } finally { setBusy(''); }
  }

  if (loading) return <PageState state="loading" title="Carregando plano e assinatura" />;
  if (error && !detail) return <PageState state="error" title="Não foi possível carregar a assinatura" retry={() => setAttempt(n => n + 1)}>{error}</PageState>;
  if (!detail) return null;

  const { assinatura: s, consumo, planos } = detail;
  const alerta = s.status === 'SUSPENSA' || s.status === 'CANCELADA'
    ? 'subscription-alert'
    : s.status === 'INADIMPLENTE' || s.canceladaEm ? 'subscription-alert is-warning' : '';

  return <div className="subscription-page">
    {notice && <p className="subscription-alert is-warning" role="status">{notice}</p>}
    {error && <p className="subscription-alert" role="alert">{error}</p>}

    <section className="subscription-status" aria-labelledby="assinatura-atual">
      <h3 id="assinatura-atual">Plano e assinatura</h3>
      <p className="subscription-plan">{s.plano.nome}</p>
      {alerta && <p className={alerta} role="status">{s.situacao}</p>}
      {!alerta && <p>{s.situacao}</p>}
      <dl>
        <div><dt>Situação</dt><dd>{statusLabels[s.status]}</dd></div>
        <div><dt>Valor</dt><dd>{price(s.plano)}</dd></div>
        <div>
          <dt>{s.status === 'CANCELADA' ? 'Encerrada em' : s.canceladaEm ? 'Acesso até' : 'Próxima cobrança'}</dt>
          <dd>{date(s.canceladaEm ? s.cancelamentoEfetivoEm ?? s.periodoFim : s.periodoFim)}</dd>
        </div>
        {s.status === 'TRIAL' && s.trialFim && <div><dt>Avaliação até</dt><dd>{date(s.trialFim)}</dd></div>}
        {s.cancelamentoMotivo && <div><dt>Motivo do cancelamento</dt><dd>{s.cancelamentoMotivo}</dd></div>}
      </dl>
      {!s.permiteCrescer && <p>Consulta, exportação e pagamento continuam liberados. Cadastros e envios de fotos voltam assim que o pagamento for confirmado. Nenhum dado da oficina foi removido.</p>}
    </section>

    <h3>Consumo do plano</h3>
    <ul className="subscription-usage">
      {consumo.limites.map(l => {
        const state = l.atingido ? 'is-full' : l.percentual >= 80 ? 'is-near' : '';
        return <li key={l.chave}>
          <header>
            <strong>{l.rotulo}</strong>
            <span>{l.usadoLegivel} de {l.limiteLegivel}</span>
          </header>
          {l.limite !== null && <div className={`subscription-bar ${state}`}><div style={{ width: `${l.percentual}%` }} /></div>}
          {l.atingido && <p>Limite atingido. Mude de plano para continuar cadastrando.</p>}
        </li>;
      })}
    </ul>
    <p><Icon name="info" size={14} /> Consumo apurado no servidor em {date(consumo.geradoEm, true)}.</p>

    <h3>Planos disponíveis</h3>
    <ul className="subscription-plans">
      {planos.map(p => <li key={p.id} aria-current={p.atual}>
        <h4>{p.nome}</h4>
        <span className="subscription-price">{price(p)}</span>
        <p>{p.descricao}</p>
        <ul>
          <li>{limitText(p.maxUsuarios, 'usuário', 'usuários')}</li>
          <li>{Math.round(p.maxArmazenamentoBytes / 1073741824)} GB de armazenamento</li>
          <li>{limitText(p.maxOrdensServicoMes, 'OS por mês', 'OS por mês')}</li>
          <li>{limitText(p.maxVeiculos, 'veículo', 'veículos')}</li>
        </ul>
        {p.atual
          ? <button disabled>Plano atual</button>
          : owner && <button disabled={!!busy} onClick={() => run(p.codigo,
              () => api(`${base}/plano`, 'PUT', { codigo: p.codigo, revisao: s.revisao }),
              `Plano alterado para ${p.nome}.`)}>
              {busy === p.codigo ? 'Alterando…' : 'Mudar para este plano'}
            </button>}
      </li>)}
    </ul>
    {!owner && <p>Somente o proprietário da oficina pode mudar de plano, cancelar ou reativar a assinatura.</p>}

    {owner && <section aria-labelledby="assinatura-acoes">
      <h3 id="assinatura-acoes">Cancelamento e reativação</h3>
      {s.canceladaEm
        ? <>
            <p>{s.status === 'CANCELADA'
              ? 'Assinatura cancelada. Reative para voltar a cadastrar; seus dados continuam disponíveis.'
              : `Cancelamento agendado para ${date(s.cancelamentoEfetivoEm ?? s.periodoFim)}. Você pode revogar até lá.`}</p>
            <button disabled={!!busy} onClick={() => run('reativar',
              () => api(`${base}/reativacao`, 'POST', { revisao: s.revisao }),
              'Reativação registrada.')}>
              {busy === 'reativar' ? 'Reativando…' : 'Reativar assinatura'}
            </button>
          </>
        : <>
            <p>O cancelamento não apaga nenhum dado da oficina. Você continua consultando e exportando o histórico.</p>
            <label htmlFor="motivo-cancelamento">Motivo do cancelamento</label>
            <textarea id="motivo-cancelamento" maxLength={500} rows={3} value={reason}
              onChange={e => setReason(e.target.value)} placeholder="Conte por que está cancelando." />
            {!confirming
              ? <>
                  <button disabled={!reason.trim()} onClick={() => setConfirming('periodo')}>Cancelar ao fim do período</button>
                  <button disabled={!reason.trim()} onClick={() => setConfirming('imediato')}>Cancelar imediatamente</button>
                </>
              : <>
                  <p role="alert">{confirming === 'imediato'
                    ? 'O acesso de cadastro termina agora. Confirma?'
                    : `O acesso continua até ${date(s.periodoFim)}. Confirma?`}</p>
                  <button disabled={!!busy} onClick={() => run('cancelar',
                    () => api(`${base}/cancelamento`, 'POST', { imediato: confirming === 'imediato', motivo: reason.trim(), revisao: s.revisao }),
                    'Cancelamento registrado.').then(() => { setConfirming(null); setReason(''); })}>
                    {busy === 'cancelar' ? 'Cancelando…' : 'Confirmar cancelamento'}
                  </button>
                  <button onClick={() => setConfirming(null)}>Voltar</button>
                </>}
          </>}
    </section>}

    <h3>Histórico de cobrança</h3>
    {events.length === 0
      ? <p>Nenhum evento financeiro registrado ainda.</p>
      : <ul className="subscription-events">
          {events.map(e => <li key={e.id}>
            <time dateTime={e.criadoEm}>{date(e.criadoEm, true)} · {e.tipo}</time>
            {e.mensagem}
          </li>)}
        </ul>}
  </div>;
}
