import { useState } from 'react';
import { Drawer, Field } from '../ui';
import { money } from '../model';
import { Collection, History } from './shared';
import { WhatsApp } from '../WhatsApp';
import { convite } from '../whatsapp-mensagem';
import type { Venda } from './types';
export default function VendasPage({ empresa }: { empresa: string }) {
  const [period, setPeriod] = useState(''); const [selected, setSelected] = useState('');
  return <><h1>Vendas</h1><p>Valores e custos preservados no momento da venda. Para vender, abra o veículo no estoque.</p>
    <form className="dealer-filters" onSubmit={e => { e.preventDefault(); const f = new FormData(e.currentTarget); const q = new URLSearchParams(); if (f.get('de')) q.set('de', new Date(`${f.get('de')}T00:00:00`).toISOString()); if (f.get('ate')) q.set('ate', new Date(`${f.get('ate')}T23:59:59.999`).toISOString()); setPeriod(q.toString()); }}><Field label="Desde"><input name="de" type="date" /></Field><Field label="Até"><input name="ate" type="date" /></Field><button>Filtrar vendas</button></form>
    <Collection<Venda> key={period} path={`/revenda/vendas?${period}`} render={v => <><h2>{v.veiculoDescricao}</h2><p>{v.clienteNome} · {new Date(v.criadoEm).toLocaleString('pt-BR')}</p><dl><dt>Valor vendido</dt><dd>{money(v.valorVendido)}</dd><dt>Desconto</dt><dd>{money(v.desconto)}</dd><dt>Custo acumulado</dt><dd>{money(v.custoAcumulado)}</dd><dt>Margem bruta</dt><dd>{money(v.margemBruta)}</dd><dt>Entrada / troca</dt><dd>{money(v.entrada)} / {money(v.valorTroca)}</dd></dl>{v.estoqueTrocaId && <p>Veículo recebido em troca já registrado no estoque em preparação.</p>}<div className="dealer-actions"><WhatsApp telefone={v.telefone} mensagem={convite(empresa, `Sobre o ${v.veiculoDescricao}: como está o carro?`)} /><button onClick={() => setSelected(v.id)}>Histórico da venda</button></div></>} />
    {selected && <Drawer title="Histórico da venda" close={() => setSelected('')}><History recurso="VENDA" id={selected} /></Drawer>}
  </>;
}
