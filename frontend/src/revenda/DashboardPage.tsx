import { useState } from 'react';
import { Field } from '../ui';
import { money } from '../model';
import { ResourceState } from './shared';
import { useResource } from './dados';
interface Resumo { emAvaliacao: number; emPreparacao: number; disponiveis: number; reservados: number; vendidos: number; valorAquisicao: number; custosPreparacao: number; valorAnunciado: number; margemPotencial: number; mediaDiasEstoque: number; leadsAbertos: number; propostasAbertas: number; vendasPeriodo: number; valorVendido: number; margemRealizada: number }
export default function DashboardPage() {
  const [period, setPeriod] = useState(''); const r = useResource<Resumo>(`/revenda/dashboard?${period}`);
  return <><div className="page-heading"><div><h1>Início</h1><p>O essencial de hoje. Vendas dos últimos 30 dias, salvo outro período.</p></div><button onClick={r.reload}>Atualizar indicadores</button></div>
    <form className="dealer-filters" onSubmit={e => { e.preventDefault(); const f = new FormData(e.currentTarget); const q = new URLSearchParams(); if (f.get('de')) q.set('de', new Date(`${f.get('de')}T00:00:00`).toISOString()); if (f.get('ate')) q.set('ate', new Date(`${f.get('ate')}T23:59:59.999`).toISOString()); setPeriod(q.toString()); }}><Field label="Vendas desde"><input name="de" type="date" /></Field><Field label="Vendas até"><input name="ate" type="date" /></Field><button>Aplicar período</button></form>
    {r.data ? <dl className="dealer-metrics">{([['Carros disponíveis', r.data.disponiveis], ['Reservados', r.data.reservados], ['Interessados abertos', r.data.leadsAbertos], ['Propostas abertas', r.data.propostasAbertas], ['Vendas no período', r.data.vendasPeriodo], ['Margem realizada', money(r.data.margemRealizada)]] as const).map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl> : <ResourceState error={r.error} retry={r.reload} />}
  </>;
}
