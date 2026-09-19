import { useState } from 'react';
import { api } from '../api';
import { Form } from '../forms';
import { Drawer, Field } from '../ui';
import { money, val } from '../model';
import { Collection, EntitySelect, History, MoneyField, Notes, StatusForm, Validity } from './shared';
import { instant } from './dados';
import { rotulo } from './types';
import type { Proposta } from './types';
export default function PropostasPage({ userId }: { userId: string }) {
  const [panel, setPanel] = useState(''); const [selected, setSelected] = useState<Proposta>(); const [version, setVersion] = useState(0); const [status, setStatus] = useState('');
  const close = () => setPanel(''); const done = () => { close(); setVersion(n => n + 1); };
  return <><div className="page-heading"><div><h1>Propostas</h1><p>Cada revisão preserva os valores já apresentados.</p></div><button className="primary" onClick={() => { setSelected(undefined); setPanel('editar'); }}>Nova proposta</button></div>
    <Field label="Situação da proposta"><select value={status} onChange={e => setStatus(e.target.value)}><option value="">Todas</option>{['RASCUNHO', 'ENVIADA', 'ACEITA', 'RECUSADA', 'EXPIRADA', 'CANCELADA'].map(s => <option key={s} value={s}>{rotulo(s)}</option>)}</select></Field>
    <Collection<Proposta> key={`${version}-${status}`} path={`/revenda/propostas?status=${status}`} render={p => <><span className="dealer-status">{rotulo(p.status)} · Versão {p.numeroVersao}</span><h2>{p.clienteNome}</h2><p>{p.veiculoDescricao}</p><dl><dt>Negociado</dt><dd>{money(p.valorNegociado)}</dd><dt>Desconto</dt><dd>{money(p.desconto)}</dd><dt>Entrada / troca</dt><dd>{money(p.entrada)} / {money(p.valorTroca)}</dd><dt>Válida até</dt><dd>{new Date(p.validade).toLocaleString('pt-BR')}</dd></dl><div className="dealer-actions">{['RASCUNHO', 'ENVIADA', 'RECUSADA'].includes(p.status) && <button onClick={() => { setSelected(p); setPanel('editar'); }}>Nova versão</button>}{['RASCUNHO', 'ENVIADA'].includes(p.status) && <button onClick={() => { setSelected(p); setPanel('status'); }}>Alterar proposta</button>}<button onClick={() => { setSelected(p); setPanel('versoes'); }}>Ver versões</button><button onClick={() => { setSelected(p); setPanel('historico'); }}>Histórico</button></div></>} />
    {panel && <Drawer title={panel === 'editar' ? selected ? 'Nova versão da proposta' : 'Nova proposta' : panel === 'versoes' ? 'Versões da proposta' : panel === 'status' ? 'Alterar proposta' : 'Histórico da proposta'} close={close}>
      {panel === 'editar' && <Form close={close} save={async f => { const termos = { valorNegociado: val(f, 'valorNegociado'), entrada: val(f, 'entrada'), avaliacaoTrocaId: val(f, 'avaliacaoTrocaId') || null, valorTroca: val(f, 'valorTroca'), validade: instant(f), observacoes: val(f, 'observacoes') }; await api(selected ? `/revenda/propostas/${selected.id}/versoes` : '/revenda/propostas', 'POST', selected ? { revisao: selected.revisao, termos } : { estoqueId: val(f, 'estoqueId'), clienteId: val(f, 'clienteId'), vendedorId: val(f, 'vendedorId'), leadId: val(f, 'leadId') || null, termos }); done(); }}>
        {!selected && <><EntitySelect path="/revenda/estoque" name="estoqueId" label="Veículo em estoque" /><EntitySelect path="/clientes" name="clienteId" label="Cliente" /><EntitySelect path="/usuarios" name="vendedorId" label="Vendedor" commercial initial={userId} /><EntitySelect path="/revenda/leads" name="leadId" label="Lead (opcional)" required={false} /></>}
        <MoneyField name="valorNegociado" label="Valor negociado *" value={selected?.valorNegociado} /><MoneyField name="entrada" label="Entrada *" value={selected?.entrada} /><EntitySelect path="/revenda/avaliacoes?status=ABERTA" name="avaliacaoTrocaId" label="Avaliação da troca (opcional)" required={false} initial={selected?.avaliacaoTrocaId || ''} /><MoneyField name="valorTroca" label="Valor da troca *" value={selected?.valorTroca} /><Validity /><Notes value={selected?.observacoes} />
      </Form>}
      {selected && panel === 'status' && <StatusForm path={`/revenda/propostas/${selected.id}/status`} revision={selected.revisao} options={new Date(selected.validade) <= new Date() ? ['EXPIRADA', 'CANCELADA'] : selected.status === 'RASCUNHO' ? ['ENVIADA', 'CANCELADA'] : ['ACEITA', 'RECUSADA', 'CANCELADA']} close={close} done={done} />}
      {selected && panel === 'versoes' && <Collection<{ id: string; numero: number; valorNegociado: number; entrada: number; valorTroca: number; validade: string; observacoes: string }> path={`/revenda/propostas/${selected.id}/versoes`} render={v => <><h3>Versão {v.numero}</h3><p>Valor {money(v.valorNegociado)} · Entrada {money(v.entrada)} · Troca {money(v.valorTroca)}</p><p>Válida até {new Date(v.validade).toLocaleString('pt-BR')}</p><p>{v.observacoes}</p></>} />}{selected && panel === 'historico' && <History recurso="PROPOSTA" id={selected.id} />}
    </Drawer>}
  </>;
}
