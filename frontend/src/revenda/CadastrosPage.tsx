import { useState } from 'react';
import { api } from '../api';
import { ClientForm, VehicleForm } from '../forms';
import { Drawer, Field } from '../ui';
import type { Client, Vehicle } from '../model';
import { Collection, EntitySelect } from './shared';
export default function CadastrosPage({ kind }: { kind: 'clientes' | 'veiculos' }) {
  const [adding, setAdding] = useState(false); const [current, setCurrent] = useState<Client | Vehicle>(); const [version, setVersion] = useState(0); const [query, setQuery] = useState('');
  const close = () => setAdding(false); const done = () => { close(); setVersion(n => n + 1); };
  return <><div className="page-heading"><h1>{kind === 'clientes' ? 'Clientes' : 'Veículos'}</h1><button className="primary" onClick={() => { setCurrent(undefined); setAdding(true); }}>Cadastrar {kind === 'clientes' ? 'cliente' : 'veículo'}</button></div><Field label="Buscar cadastro"><input type="search" value={query} onChange={e => setQuery(e.target.value)} /></Field>
    {kind === 'clientes' ? <Collection<Client> key={`${version}-${query}`} path={`/clientes?busca=${encodeURIComponent(query)}`} render={c => <><h2>{c.nome}</h2><p>{c.telefone} · {c.email}</p><button onClick={() => { setCurrent(c); setAdding(true); }}>Editar cliente</button></>} /> : <Collection<Vehicle> key={`${version}-${query}`} path={`/veiculos?busca=${encodeURIComponent(query)}`} render={v => <><h2>{v.marca} {v.modelo}</h2><p>{v.placa || v.chassi} · {v.ano} · {v.km.toLocaleString('pt-BR')} km</p><p>{v.propriedade === 'EMPRESA' ? 'Propriedade da empresa' : v.propriedade === 'NAO_INFORMADA' ? 'Proprietário não informado' : 'Veículo de cliente'}</p><button onClick={() => { setCurrent(v); setAdding(true); }}>Editar veículo</button></>} />}
    {adding && <Drawer title={kind === 'clientes' ? 'Cadastro de cliente' : 'Cadastro de veículo'} close={close}>{kind === 'clientes' ? <ClientForm current={current as Client | undefined} close={close} save={async c => { await api(current ? `/clientes/${current.id}` : '/clientes', current ? 'PUT' : 'POST', c); done(); }} /> : <VehicleForm current={current as Vehicle | undefined} clients={[]} vehicles={[]} allowUnowned clientPicker={(current as Vehicle | undefined)?.propriedade === 'EMPRESA' ? <p>Veículo da empresa. A transferência ao comprador ocorre pela venda.</p> : <EntitySelect path="/clientes" name="clienteId" label="Proprietário (opcional)" required={false} initial={(current as Vehicle | undefined)?.clienteId || ''} />} close={close} save={async v => { await api(current ? `/veiculos/${current.id}` : '/veiculos', current ? 'PUT' : 'POST', { ...v, placa: v.placa || null, clienteId: v.clienteId || null }); done(); }} />}</Drawer>}
  </>;
}
