import { useState } from 'react';
import { api } from '../api';
import { Form } from '../forms';
import { Drawer, Field } from '../ui';
import { money, val } from '../model';
import { Collection, EntitySelect, MoneyField, Notes } from './shared';
import { today } from './dados';
import { rotulo } from './types';
import type { Estoque } from './types';
import EstoqueDetail from './EstoqueDetail';
export default function EstoquePage({ userId, abrirAvaliacoes }: { userId: string; abrirAvaliacoes: () => void }) {
  const [adding, setAdding] = useState(false); const [selected, setSelected] = useState(''); const [version, setVersion] = useState(0); const [filters, setFilters] = useState('');
  if (selected) return <EstoqueDetail id={selected} userId={userId} back={() => { setSelected(''); setVersion(n => n + 1); }} />;
  return <><div className="page-heading"><div><h1>Carros</h1><p>Compra, preparação e disponibilidade.</p></div><div className="dealer-actions"><button onClick={abrirAvaliacoes}>Avaliações</button><button className="primary" onClick={() => setAdding(true)}>Comprar carro</button></div></div>
    <form className="dealer-filters" onSubmit={e => { e.preventDefault(); const f = new FormData(e.currentTarget); setFilters(new URLSearchParams([...f.entries()].map(([k, v]) => [k, String(v)])).toString()); }}>
      <Field label="Buscar veículo"><input name="busca" type="search" /></Field><Field label="Situação"><select name="status"><option value="">Todas</option>{['EM_AVALIACAO', 'EM_PREPARACAO', 'DISPONIVEL', 'RESERVADO', 'VENDIDO'].map(s => <option key={s} value={s}>{rotulo(s)}</option>)}</select></Field>
      <Field label="Marca"><input name="marca" /></Field><Field label="Modelo"><input name="modelo" /></Field><Field label="Preço de"><input name="precoDe" type="number" min="0" step="0.01" /></Field><Field label="Preço até"><input name="precoAte" type="number" min="0" step="0.01" /></Field><button>Filtrar estoque</button>
    </form>
    <Collection<Estoque> key={`${version}-${filters}`} path={`/revenda/estoque?${filters}`} render={e => <><span className="dealer-status">{rotulo(e.status)}</span><h2>{e.marca} {e.modelo}</h2><p>{e.placa || 'Identificado pelo chassi'} · Entrada {e.entrada}</p><dl><dt>Preço anunciado</dt><dd>{money(e.precoAnunciado)}</dd><dt>Custo acumulado</dt><dd>{money(e.custoTotal)}</dd><dt>Margem prevista</dt><dd>{money(e.margemPrevista)}</dd></dl><button onClick={() => setSelected(e.id)}>Abrir veículo</button></>} />
    {adding && <Drawer title="Adquirir veículo" close={() => setAdding(false)}><Form close={() => setAdding(false)} confirmation="Confirmar a aquisição e transferir a propriedade deste veículo para a empresa?" save={async f => { const e = await api<Estoque>('/revenda/estoque', 'POST', { veiculoId: val(f, 'veiculoId'), responsavelId: val(f, 'responsavelId'), entrada: val(f, 'entrada'), origem: val(f, 'origem'), valorAquisicao: val(f, 'valorAquisicao'), precoAnunciado: val(f, 'precoAnunciado'), precoMinimo: val(f, 'precoMinimo'), observacoes: val(f, 'observacoes') }); setAdding(false); setSelected(e.id); }}>
      <EntitySelect path="/veiculos" name="veiculoId" label="Veículo" /><EntitySelect path="/usuarios" name="responsavelId" label="Responsável" commercial initial={userId} /><Field label="Data de entrada"><input name="entrada" type="date" required defaultValue={today()} max={today()} /></Field><Field label="Origem"><select name="origem"><option>COMPRA</option><option value="AQUISICAO_DIRETA">Aquisição direta</option><option>OUTRO</option></select></Field><MoneyField name="valorAquisicao" label="Valor de aquisição *" /><MoneyField name="precoAnunciado" label="Preço anunciado *" /><MoneyField name="precoMinimo" label="Preço mínimo *" /><Notes />
    </Form></Drawer>}
  </>;
}
