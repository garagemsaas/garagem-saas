import { useState } from 'react';
import { api } from '../api';
import { Form } from '../forms';
import { Drawer, Field } from '../ui';
import { val } from '../model';
import { Collection, EntitySelect, History, Notes, StatusForm } from './shared';
import { rotulo } from './types';
import type { Lead } from './types';
const next: Record<string, string[]> = { NOVO: ['CONTATO_REALIZADO', 'PERDIDO'], CONTATO_REALIZADO: ['INTERESSADO', 'PERDIDO'], INTERESSADO: ['NEGOCIACAO', 'PERDIDO'], PROPOSTA: ['NEGOCIACAO', 'PERDIDO'], NEGOCIACAO: ['INTERESSADO', 'PERDIDO'] };
export default function LeadsPage({ userId }: { userId: string }) {
  const [panel, setPanel] = useState(''); const [selected, setSelected] = useState<Lead>(); const [version, setVersion] = useState(0); const [status, setStatus] = useState('');
  const close = () => setPanel(''); const done = () => { close(); setVersion(n => n + 1); };
  return <><div className="page-heading"><div><h1>Interessados</h1><p>Quem procurou a loja e em que ponto está a conversa.</p></div><button className="primary" onClick={() => setPanel('novo')}>Novo interessado</button></div>
    <Field label="Situação do lead"><select value={status} onChange={e => setStatus(e.target.value)}><option value="">Todas</option>{[...Object.keys(next), 'VENDIDO', 'PERDIDO'].map(s => <option key={s} value={s}>{rotulo(s)}</option>)}</select></Field>
    <Collection<Lead> key={`${version}-${status}`} path={`/revenda/leads?status=${status}`} render={l => <><span className="dealer-status">{rotulo(l.status)}</span><h2>{l.clienteNome}</h2><p>{l.telefone} · {l.email}</p><p>{l.veiculoDescricao || 'Interesse ainda não definido'} · {rotulo(l.origem)}</p><p>{l.observacoes}</p><div className="dealer-actions">{next[l.status] && <button onClick={() => { setSelected(l); setPanel('status'); }}>Atualizar atendimento</button>}<button onClick={() => { setSelected(l); setPanel('historico'); }}>Histórico</button></div></>} />
    {panel && <Drawer title={panel === 'novo' ? 'Novo interessado' : panel === 'status' ? 'Atualizar atendimento' : 'Histórico do lead'} close={close}>
      {panel === 'novo' && <Form close={close} save={async f => { await api('/revenda/leads', 'POST', { clienteId: val(f, 'clienteId'), veiculoId: val(f, 'veiculoId') || null, vendedorId: val(f, 'vendedorId'), origem: val(f, 'origem'), observacoes: val(f, 'observacoes') }); done(); }}><EntitySelect path="/clientes" name="clienteId" label="Cliente" /><EntitySelect path="/veiculos" name="veiculoId" label="Veículo de interesse (opcional)" required={false} /><EntitySelect path="/usuarios" name="vendedorId" label="Vendedor" commercial initial={userId} /><Field label="Origem"><select name="origem">{['PRESENCIAL', 'TELEFONE', 'WHATSAPP', 'SITE', 'INSTAGRAM', 'FACEBOOK', 'INDICACAO', 'MARKETPLACE', 'OUTRO'].map(o => <option key={o}>{o}</option>)}</select></Field><Notes /></Form>}
      {selected && panel === 'status' && <StatusForm path={`/revenda/leads/${selected.id}/status`} revision={selected.revisao} options={next[selected.status] || []} close={close} done={done} />}{selected && panel === 'historico' && <History recurso="LEAD" id={selected.id} />}
    </Drawer>}
  </>;
}
