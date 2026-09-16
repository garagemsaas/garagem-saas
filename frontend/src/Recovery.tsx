import { useEffect, useRef, useState } from 'react';
import { api, ApiError } from './api';
import type { PageResult } from './api';
import { Form } from './forms';
import { Field, Icon, Pager } from './ui';
import { PageState } from './PageState';
import { date, money } from './model';
import './Recovery.css';

const base = '/dinheiro-esquecido';
const types = { ORCAMENTO_ESQUECIDO: 'Orçamento esquecido', REVISAO_ATRASADA: 'Revisão atrasada', REAVALIACAO_PENDENTE: 'Reavaliação pendente' };
const statuses = { ABERTA: 'Aberta', EM_CONTATO: 'Em contato', AGENDADA: 'Agendada', RECUPERADA: 'Recuperada', PERDIDA: 'Perdida', DESCARTADA: 'Descartada' };
const outcomes = { SEM_RESPOSTA: 'Sem resposta', CONTATO_REALIZADO: 'Contato realizado', INTERESSADO: 'Interessado', NAO_INTERESSADO: 'Não interessado', AGENDADO: 'Agendado' };
const channels = { TELEFONE: 'Telefone', WHATSAPP: 'WhatsApp', EMAIL: 'E-mail', PRESENCIAL: 'Presencial', OUTRO: 'Outro' };
interface Opportunity {
  id: string; tipo: keyof typeof types; status: keyof typeof statuses; revisao: number;
  cliente: { id: string; nome: string; telefone: string; email: string | null };
  veiculo: { id: string; placa: string; marca: string; modelo: string };
  origem: { ordemServicoId: string; numeroOs: number; orcamentoVersaoId: string | null };
  responsavel: { id: string; nome: string } | null;
  valorPotencial: number | null; criadoEm: string; elegivelDesde: string; diasEmAberto: number;
  ultimoContatoEm: string | null; proximoContatoEm: string | null; encerradaEm: string | null;
}
interface Detail {
  oportunidade: Opportunity;
  contatos: { id: string; realizadoEm: string; canal: keyof typeof channels; resultado: keyof typeof outcomes; observacao: string | null; proximoContatoEm: string | null }[];
  resultado: { valorRecuperado: number; registradoEm: string; observacao: string | null } | null;
  auditoria: { id: string; criadoEm: string; tipo: string; anterior: string | null; novo: string | null; observacao: string | null }[];
}
interface Summary { oportunidadesAbertas: number; valorPotencialConhecido: number; valorRecuperado: number; quantidadeRecuperada: number }
const terminal = (o: Opportunity) => ['RECUPERADA', 'PERDIDA', 'DESCARTADA'].includes(o.status);
function nextAction(o: Opportunity) {
  if (terminal(o)) return 'Encerrada. Consulte o histórico.';
  if (o.proximoContatoEm) return `Retomar contato em ${date(o.proximoContatoEm, true)}.`;
  if (o.tipo === 'REVISAO_ATRASADA') return 'Convidar o cliente para avaliar e agendar a revisão.';
  return o.tipo === 'REAVALIACAO_PENDENTE' ? 'Consultar o interesse em reavaliar a proposta recusada.' : 'Consultar o cliente sobre o orçamento sem resposta.';
}
function origin(o: Opportunity) {
  if (o.tipo === 'REVISAO_ATRASADA') return `OS #${o.origem.numeroOs} concluída com revisão prevista. Valor ainda não avaliado.`;
  return `OS #${o.origem.numeroOs} · total da versão ${o.tipo === 'ORCAMENTO_ESQUECIDO' ? 'publicada sem resposta há pelo menos 7 dias' : 'recusada, elegível para reavaliação'}.`;
}
function ErrorText(e: unknown) { return e instanceof Error ? e.message : 'Não foi possível concluir a operação.'; }

export default function Recovery({ openOrder }: { openOrder: (id: string) => void }) {
  const [tipo, setTipo] = useState('');
  const [status, setStatus] = useState('');
  const [age, setAge] = useState('');
  const [sort, setSort] = useState('elegivelDesde,asc');
  const [page, setPage] = useState(0);
  const [attempt, setAttempt] = useState(0);
  const [rows, setRows] = useState<PageResult<Opportunity>>();
  const [summary, setSummary] = useState<Summary>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [identifying, setIdentifying] = useState(false);
  const [identifiedAt, setIdentifiedAt] = useState('');
  const identifyingRef = useRef(false);
  const [selected, setSelected] = useState('');
  const [detail, setDetail] = useState<Detail>();
  const [detailError, setDetailError] = useState('');
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailAttempt, setDetailAttempt] = useState(0);
  const [form, setForm] = useState<'contact' | 'recovered' | null>(null);
  const [result, setResult] = useState('CONTATO_REALIZADO');
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  useEffect(() => {
    let active = true;
    // oxlint-disable-next-line react/set-state-in-effect -- remote request lifecycle
    setLoading(true); setError('');
    const query = new URLSearchParams({ pagina: String(page), tamanho: '10', ordenacao: sort });
    if (tipo) query.set('tipo', tipo);
    if (status) query.set('status', status);
    if (age) query.set('faixaIdade', age);
    Promise.all([api<PageResult<Opportunity>>(`${base}/oportunidades?${query}`), api<Summary>(`${base}/resumo`)])
      .then(([data, totals]) => { if (active) { setRows(data); setSummary(totals); } })
      .catch(e => { if (active) setError(ErrorText(e)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [tipo, status, age, sort, page, attempt]);
  useEffect(() => {
    if (!selected) return;
    let active = true;
    // oxlint-disable-next-line react/set-state-in-effect -- remote request lifecycle
    setDetailLoading(true); setDetailError(''); setDetail(undefined);
    api<Detail>(`${base}/oportunidades/${selected}`).then(data => { if (active) setDetail(data); })
      .catch(e => { if (active) setDetailError(ErrorText(e)); })
      .finally(() => { if (active) setDetailLoading(false); });
    return () => { active = false; };
  }, [selected, detailAttempt]);
  async function identify() {
    if (identifyingRef.current) return;
    identifyingRef.current = true; setIdentifying(true); setNotice('');
    try {
      const counts = await api<{ criadas: number; descartadas: number }>(`${base}/identificar`, 'POST');
      setIdentifiedAt(new Date().toISOString());
      setNotice(`${counts.criadas} oportunidades criadas; ${counts.descartadas} descartadas por mudança na origem.`);
      setPage(0); setAttempt(n => n + 1);
    } catch (e) { setNotice(`${ErrorText(e)} A identificação pode ter sido parcial. Você pode repetir a operação.`); }
    finally { identifyingRef.current = false; setIdentifying(false); }
  }
  async function save(data: FormData) {
    if (!detail || savingRef.current) return;
    savingRef.current = true; setSaving(true);
    const o = detail.oportunidade;
    const note = String(data.get('observacao') ?? '').trim() || null;
    let committed = false;
    try {
      if (form === 'contact') {
        const next = String(data.get('proximoContatoEm') ?? '');
        if (next && new Date(next).getTime() <= Date.now()) throw new Error('Informe um próximo contato no futuro.');
        await api(`${base}/oportunidades/${o.id}/contatos`, 'POST', { canal: data.get('canal'), resultado: data.get('resultado'), observacao: note, proximoContatoEm: next ? new Date(next).toISOString() : null, revisao: o.revisao });
      } else {
        const value = String(data.get('valorRecuperado') ?? '').trim().replace(',', '.');
        if (!/^\d{1,17}(\.\d{1,2})?$/.test(value)) throw new Error('Informe um valor não negativo com até duas casas decimais.');
        await api(`${base}/oportunidades/${o.id}/resultados`, 'POST', { valorRecuperado: value, observacao: note, revisao: o.revisao });
      }
      committed = true;
      setForm(null); setNotice('Registro salvo na oficina.'); setAttempt(n => n + 1);
      setDetail(await api<Detail>(`${base}/oportunidades/${o.id}`));
    } catch (e) {
      if (committed) { setDetail(undefined); setDetailError('Registro salvo, mas o histórico não pôde ser atualizado. Recarregue os dados antes de continuar.'); return; }
      if (e instanceof ApiError && e.status === 409) {
        setForm(null); setNotice('A oportunidade mudou. Confira os dados atualizados antes de registrar novamente. Nenhuma ação foi repetida.');
        setDetail(undefined); setDetailAttempt(n => n + 1); setAttempt(n => n + 1);
        return;
      }
      throw e;
    } finally { savingRef.current = false; setSaving(false); }
  }
  const o = detail?.oportunidade;
  return <section className="recovery-page" aria-label="Carteira de oportunidades">
    <p>Sua oficina já tem clientes. Faça eles voltarem.</p>
    {notice && <p role="status" className="notice">{notice}</p>}
    {selected ? <>
      <button disabled={saving} onClick={() => { setSelected(''); setForm(null); }}>Voltar às oportunidades</button>
      {detailLoading ? <PageState state="loading" title="Carregando oportunidade" /> : detailError ? <PageState state="error" title="Falha ao carregar oportunidade" retry={() => setDetailAttempt(n => n + 1)}>{detailError}</PageState> : o && detail && <>
        <h2>{o.cliente.nome} · {o.veiculo.placa}</h2>
        <p>{o.veiculo.marca} {o.veiculo.modelo} · {types[o.tipo]} · {statuses[o.status]}</p>
        <p>{o.cliente.telefone} {o.cliente.email && `· ${o.cliente.email}`}</p>
        <div className="recovery-source"><strong>Potencial: {o.valorPotencial === null ? 'Não avaliado' : money(o.valorPotencial)}</strong><p>{origin(o)}</p>
          {o.origem.orcamentoVersaoId && <p>Referência da versão: <code>{o.origem.orcamentoVersaoId}</code></p>}
          <button onClick={() => openOrder(o.origem.ordemServicoId)}>Consultar OS #{o.origem.numeroOs}</button>
        </div>
        <p>Identificada em {date(o.criadoEm, true)} · Elegível desde {date(o.elegivelDesde, true)} · {o.diasEmAberto} dias</p>
        <p>Responsável: {o.responsavel?.nome ?? 'Não atribuído'}</p>
        <p><strong>Próxima ação:</strong> {nextAction(o)}</p>
        {detail.resultado && <p className="notice">Valor recuperado: {money(detail.resultado.valorRecuperado)} · {date(detail.resultado.registradoEm, true)}{detail.resultado.observacao && ` · ${detail.resultado.observacao}`}</p>}
        {!terminal(o) && !form && <div className="form-actions"><button onClick={() => { setResult('CONTATO_REALIZADO'); setForm('contact'); }}>Registrar contato</button><button className="primary" onClick={() => setForm('recovered')}>Marcar valor recuperado</button></div>}
        {form && !terminal(o) && <div className="recovery-source"><h3>{form === 'contact' ? 'Registrar contato' : 'Registrar recuperação'}</h3>
          <Form key={form} save={save} close={() => setForm(null)} submit={form === 'contact' ? 'Salvar contato' : 'Revisar recuperação'} confirmation={form === 'recovered' ? 'Confirme o valor efetivamente recuperado. O registro encerra a oportunidade e não poderá ser editado. Ele não representa confirmação de pagamento.' : undefined}>
            {form === 'contact' ? <>
              <p>Registro manual: nenhuma mensagem será enviada.</p>
              <Field label="Canal"><select name="canal">{Object.entries(channels).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></Field>
              <Field label="Resultado do contato"><select name="resultado" value={result} onChange={e => setResult(e.target.value)}>{Object.entries(outcomes).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></Field>
              <Field label="Próximo contato" hint="Horário local. Deixar vazio remove o agendamento atual; obrigatório para Agendado."><input type="datetime-local" name="proximoContatoEm" required={result === 'AGENDADO'} /></Field>
            </> : <Field label="Valor recuperado (R$)" hint="Informe o valor efetivo, mesmo que diferente do potencial. Zero é permitido."><input name="valorRecuperado" required inputMode="decimal" placeholder="0,00" /></Field>}
            <Field label="Observação"><textarea name="observacao" maxLength={4000} rows={3} /></Field>
          </Form>
        </div>}
        <h3>Histórico de contatos</h3>
        {detail.contatos.length ? <ol className="recovery-history">{detail.contatos.map(c => <li key={c.id}><strong>{channels[c.canal]} · {outcomes[c.resultado]}</strong><p>{date(c.realizadoEm, true)} · {c.observacao || 'Sem observação'}</p>{c.proximoContatoEm && <p>Próximo contato informado: {date(c.proximoContatoEm, true)}</p>}</li>)}</ol> : <p>Nenhum contato registrado.</p>}
      </>}
    </> : <>
      <button className="primary" disabled={identifying} onClick={() => void identify()}><Icon name="search" />{identifying ? 'Identificando…' : 'Identificar oportunidades'}</button>
      <p>Identificação manual: atualiza as oportunidades elegíveis e descarta origens que deixaram de ser válidas.</p>
      <p>{identifiedAt ? `Última identificação nesta sessão: ${date(identifiedAt, true)}` : 'Nenhuma identificação executada nesta sessão.'}</p>
      <div className="recovery-filters">
        <Field label="Tipo de oportunidade"><select value={tipo} onChange={e => { setTipo(e.target.value); setPage(0); }}><option value="">Todos os tipos</option>{Object.entries(types).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></Field>
        <Field label="Status da oportunidade"><select value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}><option value="">Todos os status</option>{Object.entries(statuses).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></Field>
        <Field label="Idade da oportunidade"><select value={age} onChange={e => { setAge(e.target.value); setPage(0); }}><option value="">Todas as idades</option>{Object.entries({ DIAS_0_7: '0–7 dias', DIAS_8_15: '8–15 dias', DIAS_16_30: '16–30 dias', DIAS_31_60: '31–60 dias', MAIS_60: 'Mais de 60 dias' }).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></Field>
        <Field label="Ordenar oportunidades"><select value={sort} onChange={e => { setSort(e.target.value); setPage(0); }}><option value="elegivelDesde,asc">Mais antigas primeiro</option><option value="criadoEm,desc">Identificadas recentemente</option><option value="valorPotencial,desc">Maior potencial</option><option value="proximoContatoEm,asc">Próximo contato</option></select></Field>
      </div>
      <button onClick={() => { setTipo(''); setStatus(''); setAge(''); setPage(0); }}>Limpar filtros</button>
      {loading ? <PageState state="loading" title="Carregando oportunidades" /> : error ? <PageState state="error" title="Não foi possível carregar oportunidades" retry={() => setAttempt(n => n + 1)}>{error}</PageState> : <>
        {summary && <><dl className="recovery-metrics"><div><dt>Oportunidades ativas</dt><dd>{summary.oportunidadesAbertas}</dd></div><div><dt>Potencial conhecido</dt><dd>{money(summary.valorPotencialConhecido)}</dd></div><div><dt>Valor recuperado</dt><dd>{money(summary.valorRecuperado)}</dd></div></dl><p>Indicadores de toda a carteira, sem os filtros da lista. Potencial soma valores conhecidos de oportunidades ativas; não é receita. Recuperado soma resultados registrados.</p></>}
        {!rows?.itens.length ? <PageState state="empty" title="Nenhuma oportunidade encontrada">Revise os filtros ou execute a identificação. Revisões sem avaliação não recebem um valor estimado.</PageState> : <ul className="recovery-list">{rows.itens.map(item => <li key={item.id}>
          <div className="section-heading"><h3>{item.cliente.nome}</h3><span>{statuses[item.status]}</span></div>
          <p>{item.veiculo.placa} · {item.veiculo.marca} {item.veiculo.modelo}</p>
          <p>{types[item.tipo]} · Identificada em {date(item.criadoEm, true)}</p>
          <strong>{item.valorPotencial === null ? 'Não avaliado' : money(item.valorPotencial)}</strong><p>{origin(item)}</p>
          <p><strong>Próxima ação:</strong> {nextAction(item)}</p>
          <button onClick={() => { setDetail(undefined); setDetailLoading(true); setSelected(item.id); setForm(null); }}>Ver oportunidade de {item.cliente.nome}</button>
        </li>)}</ul>}
        <Pager total={rows?.total ?? 0} page={page} setPage={setPage} />
      </>}
    </>}
  </section>;
}
