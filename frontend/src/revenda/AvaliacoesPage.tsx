import { useState } from 'react';
import { api } from '../api';
import { Form } from '../forms';
import { Drawer, Field } from '../ui';
import { money, val } from '../model';
import { Collection, EntitySelect, History, MoneyField, Notes, StatusForm, Validity } from './shared';
import { instant, today } from './dados';
import { rotulo } from './types';
import type { Avaliacao } from './types';
import type { Vehicle } from '../model';
import Photos from './Photos';
export default function AvaliacoesPage({ userId }: { userId: string }) {
  const [panel, setPanel] = useState(''); const [selected, setSelected] = useState<Avaliacao>(); const [version, setVersion] = useState(0); const [status, setStatus] = useState('');
  const close = () => setPanel(''); const done = () => { close(); setVersion(n => n + 1); };
  return <><div className="page-heading"><div><h1>Avaliações</h1><p>Conheça o veículo antes de adquiri-lo ou recebê-lo em troca.</p></div><button className="primary" onClick={() => setPanel('nova')}>Nova avaliação</button></div>
    <Field label="Situação da avaliação"><select value={status} onChange={e => setStatus(e.target.value)}><option value="">Todas</option>{['ABERTA', 'ACEITA', 'RECUSADA', 'EXPIRADA', 'CANCELADA'].map(s => <option key={s} value={s}>{rotulo(s)}</option>)}</select></Field>
    <Collection<Avaliacao> key={`${version}-${status}`} path={`/revenda/avaliacoes?status=${status}`} render={a => <><span className="dealer-status">{rotulo(a.status)}</span><h2>{a.marca} {a.modelo}</h2><p>{a.placa} · {a.km.toLocaleString('pt-BR')} km</p><dl><dt>Estimado</dt><dd>{money(a.valorEstimado)}</dd><dt>Oferecido</dt><dd>{money(a.valorOferecido)}</dd><dt>Validade</dt><dd>{new Date(a.validade).toLocaleString('pt-BR')}</dd></dl><div className="dealer-actions">{a.status === 'ABERTA' && <><button onClick={() => { setSelected(a); setPanel('aceitar'); }}>Aceitar e adquirir</button><button onClick={() => { setSelected(a); setPanel('status'); }}>Encerrar avaliação</button></>}<button onClick={() => { setSelected(a); setPanel('fotos'); }}>Fotos</button><button onClick={() => { setSelected(a); setPanel('historico'); }}>Histórico</button></div></>} />
    {panel && <Drawer title={{ nova: 'Nova avaliação', aceitar: 'Aceitar avaliação e adquirir veículo', status: 'Encerrar avaliação', fotos: 'Fotos da avaliação', historico: 'Histórico da avaliação' }[panel] || ''} close={close}>
      {panel === 'nova' && <Form close={close} save={async f => { const v = await api<Vehicle>(`/veiculos/${val(f, 'veiculoId')}`); await api('/revenda/avaliacoes', 'POST', { veiculoId: v.id, clienteId: v.clienteId, avaliadorId: val(f, 'avaliadorId'), data: val(f, 'data'), km: Number(val(f, 'km')), valorEstimado: val(f, 'valorEstimado'), valorOferecido: val(f, 'valorOferecido'), validade: instant(f), observacoes: val(f, 'observacoes') }); done(); }}><EntitySelect path="/veiculos" name="veiculoId" label="Veículo" /><p>O proprietário será o registrado no cadastro central do veículo.</p><EntitySelect path="/usuarios" name="avaliadorId" label="Avaliador" commercial initial={userId} /><Field label="Data *"><input name="data" type="date" required max={today()} defaultValue={today()} /></Field><Field label="Quilometragem *"><input name="km" type="number" min="0" required /></Field><MoneyField name="valorEstimado" label="Valor estimado *" /><MoneyField name="valorOferecido" label="Valor oferecido *" /><Validity /><Notes /></Form>}
      {selected && panel === 'aceitar' && <Form close={close} confirmation="Aceitar esta avaliação e transferir o veículo para a empresa pelo valor oferecido? Para receber em troca, use a avaliação diretamente na venda." save={async f => { await api(`/revenda/avaliacoes/${selected.id}/aceite`, 'POST', { revisao: selected.revisao, responsavelId: val(f, 'responsavelId'), precoAnunciado: val(f, 'precoAnunciado'), precoMinimo: val(f, 'precoMinimo') }); done(); }}><p>Aquisição por {money(selected.valorOferecido)}.</p><EntitySelect path="/usuarios" name="responsavelId" label="Responsável" commercial initial={userId} /><MoneyField name="precoAnunciado" label="Preço anunciado *" value={selected.valorEstimado} /><MoneyField name="precoMinimo" label="Preço mínimo *" /></Form>}
      {selected && panel === 'status' && <StatusForm path={`/revenda/avaliacoes/${selected.id}/status`} revision={selected.revisao} options={['RECUSADA', 'CANCELADA', ...(new Date(selected.validade) <= new Date() ? ['EXPIRADA'] : [])]} close={close} done={done} />}
      {selected && panel === 'fotos' && <Photos context="AVALIACAO" id={selected.id} />}{selected && panel === 'historico' && <History recurso="AVALIACAO" id={selected.id} />}
    </Drawer>}
  </>;
}
