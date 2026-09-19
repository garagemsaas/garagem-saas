import { useState } from 'react';
import type { ReactNode } from 'react';
import { api } from '../api';
import { future, useResource } from './dados';
import type { PageResult } from '../api';
import { Field, Pager } from '../ui';
import { PageState } from '../PageState';
import { Form } from '../forms';
import { val } from '../model';
import { rotulo } from './types';

export function ResourceState({ error, retry }: { error?: string; retry: () => void }) { return <PageState state={error ? 'error' : 'loading'} title={error ? 'Não foi possível carregar' : 'Carregando registros'} retry={retry}>{error}</PageState>; }
export function Collection<T extends { id: string }>({ path, render, empty = 'Nenhum registro encontrado.' }: { path: string; render: (item: T, reload: () => void) => ReactNode; empty?: string }) {
  const [page, setPage] = useState(0); const r = useResource<PageResult<T>>(`${path}${path.includes('?') ? '&' : '?'}pagina=${page}&tamanho=10`);
  return <section className="dealer-collection">{r.data ? <><button className="text-button" onClick={r.reload}>Atualizar lista</button>{r.data.itens.length ? <div className="dealer-cards">{r.data.itens.map(item => <article className="dealer-card" key={item.id}>{render(item, r.reload)}</article>)}</div> : <PageState state="empty" title={empty} />}<Pager total={r.data.total} page={page} setPage={setPage} /></> : <ResourceState error={r.error} retry={r.reload} />}</section>;
}
interface Reference { id: string; nome?: string; marca?: string; modelo?: string; placa?: string; clienteNome?: string; veiculoDescricao?: string; status?: string; papel?: string; ativo?: boolean }
export function EntitySelect({ path, label, name, required = true, initial = '', commercial = false, onPick }: { path: string; label: string; name: string; required?: boolean; initial?: string; commercial?: boolean; onPick?: (id: string) => void }) {
  const [query, setQuery] = useState(''); const [page, setPage] = useState(0); const [value, setValue] = useState(initial);
  const r = useResource<PageResult<Reference>>(`${path}${path.includes('?') ? '&' : '?'}pagina=${page}&tamanho=10&busca=${encodeURIComponent(query)}`);
  const rows = r.data?.itens.filter(x => !commercial || x.ativo && x.papel !== 'MECANICO') ?? [];
  return <fieldset className="dealer-reference"><legend>{label}{required ? ' *' : ''}</legend>
    <label className="field">Buscar {label.toLowerCase()}<input value={query} onChange={e => { setQuery(e.target.value); setPage(0); }} type="search" /></label>
    <label className="field">Selecionar {label.toLowerCase()}<select name={name} required={required} value={value} onChange={e => { setValue(e.target.value); onPick?.(e.target.value); }}><option value="">Selecione</option>{value && !rows.some(x => x.id === value) && <option value={value}>Seleção atual</option>}{rows.map(x => <option key={x.id} value={x.id}>{x.nome || x.clienteNome || [x.marca, x.modelo, x.placa].filter(Boolean).join(' ') || x.veiculoDescricao || x.id}{x.status ? ` · ${rotulo(x.status)}` : ''}</option>)}</select></label>
    {r.error && <p role="alert">{r.error} <button type="button" onClick={r.reload}>Tentar novamente</button></p>}
    {!r.data && !r.error && <p role="status">Carregando opções…</p>}
    {r.data && <div className="dealer-reference-pages"><button type="button" disabled={page === 0} onClick={() => setPage(page - 1)}>Opções anteriores</button><span>Página {page + 1}</span><button type="button" disabled={(page + 1) * 10 >= r.data.total} onClick={() => setPage(page + 1)}>Mais opções</button></div>}
  </fieldset>;
}
export function MoneyField({ name, label, value = 0 }: { name: string; label: string; value?: number }) { return <Field label={label}><input name={name} type="number" inputMode="decimal" min="0" max="999999999999.99" step="0.01" required defaultValue={value.toFixed(2)} /></Field>; }
export function Notes({ value }: { value?: string | null }) { return <Field label="Observações"><textarea name="observacoes" maxLength={4000} defaultValue={value ?? ''} /></Field>; }
export function Validity({ value }: { value?: string }) { return <Field label="Válida até *"><input name="validade" type="datetime-local" required defaultValue={value ? new Date(new Date(value).getTime() - new Date(value).getTimezoneOffset() * 60000).toISOString().slice(0, 16) : future()} /></Field>; }
export function StatusForm({ path, revision, options, close, done }: { path: string; revision: number; options: string[]; close: () => void; done: () => void }) { return <Form close={close} submit="Alterar situação" confirmation="Confirmar esta mudança de situação?" save={async f => { await api(path, 'PUT', { revisao: revision, status: val(f, 'status') }); done(); }}><Field label="Nova situação"><select name="status" required>{options.map(s => <option key={s} value={s}>{rotulo(s)}</option>)}</select></Field></Form>; }
export function History({ recurso, id }: { recurso: string; id: string }) { return <Collection<{ id: string; tipo: string; descricao: string; criadoEm: string; versao: number | null }> path={`/revenda/historico/${recurso}/${id}`} render={e => <><strong>{rotulo(e.tipo)}</strong><p>{e.descricao}</p><small>{new Date(e.criadoEm).toLocaleString('pt-BR')}{e.versao ? ` · Versão ${e.versao}` : ''}</small></>} />; }
