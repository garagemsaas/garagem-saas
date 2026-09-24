import { useState } from 'react';
import { api } from '../api';
import { Form } from '../forms';
import { Drawer, Field } from '../ui';
import { money, val } from '../model';
import { Collection, EntitySelect, History, MoneyField, Notes, ResourceState, StatusForm, Validity } from './shared';
import { instant, today, useResource } from './dados';
import { rotulo } from './types';
import type { Estoque, Reserva } from './types';
import Photos from './Photos';
import VendaForm from './VendaForm';
export default function EstoqueDetail({ id, userId, back }: { id: string; userId: string; back: () => void }) {
  const r = useResource<Estoque>(`/revenda/estoque/${id}`); const [panel, setPanel] = useState(''); const [tab, setTab] = useState('custos'); const [version, setVersion] = useState(0);
  const close = () => setPanel(''); const done = () => { close(); r.reload(); setVersion(n => n + 1); };
  if (!r.data) return <ResourceState error={r.error} retry={r.reload} />; const e = r.data; const path = `/revenda/estoque/${id}`; const editable = !['VENDIDO', 'RESERVADO'].includes(e.status);
  return <><button onClick={back}>Voltar aos carros</button><div className="page-heading"><div><span className="dealer-status">{rotulo(e.status)}</span><h1>{e.marca} {e.modelo}</h1><p>{e.placa || 'Veículo identificado por chassi'}</p></div><button onClick={r.reload}>Atualizar veículo</button></div>
    <dl className="dealer-metrics"><div><dt>Compra</dt><dd>{money(e.valorAquisicao)}</dd></div><div><dt>Preparação</dt><dd>{money(e.custosPreparacao)}</dd></div><div><dt>Custo acumulado</dt><dd>{money(e.custoTotal)}</dd></div><div><dt>Anunciado</dt><dd>{money(e.precoAnunciado)}</dd></div><div><dt>Preço mínimo</dt><dd>{money(e.precoMinimo)}</dd></div><div><dt>Margem prevista</dt><dd>{money(e.margemPrevista)}</dd></div></dl>
    <div className="dealer-actions">{editable && <><button onClick={() => setPanel('precos')}>Alterar preços</button><button onClick={() => setPanel('custo')}>Registrar custo</button><button onClick={() => setPanel('status')}>Alterar situação</button></>}{e.status === 'DISPONIVEL' && <button onClick={() => setPanel('reserva')}>Reservar veículo</button>}{['DISPONIVEL', 'RESERVADO'].includes(e.status) && <button className="primary" onClick={() => setPanel('venda')}>Vender veículo</button>}
    </div><nav className="dealer-tabs" aria-label="Detalhes do estoque">{['custos', 'reservas', 'fotos', 'histórico'].map(t => <button key={t} aria-current={tab === t ? 'page' : undefined} onClick={() => setTab(t)}>{t}</button>)}</nav>
    {tab === 'custos' && <Collection<{ id: string; descricao: string; categoria: string; valor: number; data: string }> key={version} path={`${path}/custos`} render={c => <><h3>{c.descricao}</h3><p>{c.categoria} · {c.data}</p><strong>{money(c.valor)}</strong></>} />}
    {tab === 'reservas' && <Collection<Reserva> key={version} path={`/revenda/reservas?estoqueId=${id}`} render={s => <><h3>Reserva {rotulo(s.status)}</h3><p>Válida até {new Date(s.validade).toLocaleString('pt-BR')}</p>{s.status === 'ATIVA' && <Form close={back} submit="Cancelar reserva" confirmation="Cancelar esta reserva e disponibilizar o veículo?" save={async () => { await api(`/revenda/reservas/${s.id}/cancelamento`, 'POST', { revisao: s.revisao }); done(); }}>{null}</Form>}</>} />}
    {tab === 'fotos' && <Photos context="ESTOQUE" id={id} />}{tab === 'histórico' && <History recurso="ESTOQUE" id={id} />}
    {panel && <Drawer title={{ precos: 'Alterar preços', custo: 'Registrar custo de preparação', status: 'Situação do estoque', reserva: 'Reservar veículo', venda: 'Concluir venda' }[panel] || ''} close={close}>
      {panel === 'precos' && <Form close={close} save={async f => { await api(`${path}/precos`, 'PUT', { revisao: e.revisao, precoAnunciado: val(f, 'precoAnunciado'), precoMinimo: val(f, 'precoMinimo') }); done(); }}><MoneyField name="precoAnunciado" label="Preço anunciado *" value={e.precoAnunciado} /><MoneyField name="precoMinimo" label="Preço mínimo *" value={e.precoMinimo} /></Form>}
      {panel === 'custo' && <Form close={close} save={async f => { await api(`${path}/custos`, 'POST', { revisao: e.revisao, descricao: val(f, 'descricao'), categoria: val(f, 'categoria'), fornecedor: val(f, 'fornecedor'), valor: val(f, 'valor'), data: val(f, 'data'), observacoes: val(f, 'observacoes') }); done(); }}><Field label="Descrição *"><input name="descricao" required maxLength={500} /></Field><Field label="Categoria *"><input name="categoria" required maxLength={60} /></Field><Field label="Fornecedor"><input name="fornecedor" maxLength={160} /></Field><MoneyField name="valor" label="Valor *" /><Field label="Data *"><input name="data" type="date" required max={today()} defaultValue={today()} /></Field><Notes /></Form>}
      {panel === 'status' && <StatusForm path={`${path}/status`} revision={e.revisao} options={e.status === 'EM_PREPARACAO' ? ['DISPONIVEL'] : ['EM_PREPARACAO']} close={close} done={done} />}
      {panel === 'reserva' && <Form close={close} confirmation="Confirmar a reserva deste veículo para o cliente selecionado?" save={async f => { await api('/revenda/reservas', 'POST', { estoqueId: id, revisaoEstoque: e.revisao, clienteId: val(f, 'clienteId'), vendedorId: val(f, 'vendedorId'), validade: instant(f), observacoes: val(f, 'observacoes') }); done(); }}><EntitySelect path="/clientes" name="clienteId" label="Cliente" /><EntitySelect path="/usuarios" name="vendedorId" label="Vendedor" commercial initial={userId} /><Validity /><Notes /></Form>}
      {panel === 'venda' && <VendaForm estoque={e} userId={userId} close={close} done={done} />}
    </Drawer>}
  </>;
}
