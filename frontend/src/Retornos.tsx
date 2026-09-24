import { useState } from 'react';
import { api, currentSession } from './api';
import { Form } from './forms';
import { Drawer, Field } from './ui';
import { Collection, EntitySelect } from './revenda/shared';
import { val } from './model';
import { WhatsApp } from './WhatsApp';
import { convite } from './whatsapp-mensagem';
import './revenda/revenda.css';

interface Retorno { id: string; clienteId: string; cliente: string; telefone: string; veiculoId: string | null; veiculo: string;
  responsavelId: string; responsavel: string; motivo: string; agendadoEm: string; prioridade: string; status: string;
  observacoes: string | null; resultado: string | null; revisao: number }
const dataLocal = (value: string) => { const date = new Date(value); return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16); };
export default function Retornos({ empresa }: { empresa: string }) {
  const [now] = useState(() => Date.now());
  const [busca, setBusca] = useState(''); const [status, setStatus] = useState('PENDENTE'); const [meus, setMeus] = useState(false);
  const [version, setVersion] = useState(0); const [editing, setEditing] = useState<Retorno | 'novo'>();
  const [closing, setClosing] = useState<{ item: Retorno; status: 'CONCLUIDO' | 'CANCELADO' }>();
  const [suggesting, setSuggesting] = useState(false); const [notice, setNotice] = useState('');
  const close = () => { setEditing(undefined); setClosing(undefined); };
  const done = () => { close(); setVersion(n => n + 1); };
  const item = editing && editing !== 'novo' ? editing : undefined;
  return <section aria-label="Agenda de retornos"><div className="page-heading"><div><h1>Retornos</h1><p>Quem contatar, por quê e quando.</p></div><button className="primary" onClick={() => setEditing('novo')}>Agendar retorno</button></div>
    <button disabled={suggesting} onClick={async () => { setSuggesting(true); setNotice(''); try { const result = await api<{ criados: number }>('/retornos/sugestoes', 'POST'); setNotice(`${result.criados} retorno(s) adicionado(s) à agenda.`); setVersion(n => n + 1); } catch (e) { setNotice(e instanceof Error ? e.message : 'Não foi possível identificar retornos.'); } finally { setSuggesting(false); } }}>{suggesting ? 'Consultando atendimentos…' : 'Identificar contatos pendentes'}</button>
    {notice && <p role="status">{notice}</p>}
    <div className="dealer-filters"><Field label="Buscar cliente ou motivo"><input type="search" value={busca} onChange={e => setBusca(e.target.value)} maxLength={160} /></Field>
      <Field label="Situação"><select value={status} onChange={e => setStatus(e.target.value)}><option value="PENDENTE">Pendentes</option><option value="CONCLUIDO">Concluídos</option><option value="CANCELADO">Cancelados</option><option value="TODOS">Todos</option></select></Field>
      <label className="retorno-meus"><input type="checkbox" checked={meus} onChange={e => setMeus(e.target.checked)} /> Somente meus retornos</label></div>
    <Collection<Retorno> key={`${version}-${busca}-${status}-${meus}`} path={`/retornos?busca=${encodeURIComponent(busca)}&status=${status}&meus=${meus}`} empty="Nenhum retorno nesta seleção." render={r => <>
      <div className="retorno-heading"><h2>{r.cliente}</h2><span>{r.status === 'PENDENTE' ? r.prioridade === 'ALTA' ? 'Prioridade alta' : 'Pendente' : r.status === 'CONCLUIDO' ? 'Concluído' : 'Cancelado'}</span></div>
      <p><strong>{r.motivo}</strong></p>{r.veiculo && <p>{r.veiculo}</p>}
      <p><time dateTime={r.agendadoEm}>{new Date(r.agendadoEm).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })}</time>{r.status === 'PENDENTE' && Date.parse(r.agendadoEm) < now && <strong> · Em atraso</strong>}</p>
      <p>Responsável: {r.responsavel}</p>{r.observacoes && <p>{r.observacoes}</p>}{r.resultado && <p>Resultado: {r.resultado}</p>}
      {r.status === 'PENDENTE' && <><p className="form-note">Próxima ação: contatar o cliente e registrar o resultado.</p><div className="dealer-actions"><WhatsApp telefone={r.telefone} mensagem={convite(empresa, 'Podemos conversar sobre seu atendimento?')} /><button onClick={() => setClosing({ item: r, status: 'CONCLUIDO' })}>Concluir</button><button onClick={() => setEditing(r)}>Editar ou reagendar</button><button onClick={() => setClosing({ item: r, status: 'CANCELADO' })}>Cancelar retorno</button></div></>}
    </>} />
    {editing && <Drawer title={item ? 'Editar ou reagendar retorno' : 'Agendar retorno'} close={close}><Form close={close} save={async f => {
      await api(`/retornos${item ? `/${item.id}` : ''}`, item ? 'PUT' : 'POST', { clienteId: val(f, 'clienteId'), veiculoId: val(f, 'veiculoId') || null,
        responsavelId: val(f, 'responsavelId'), motivo: val(f, 'motivo'), agendadoEm: new Date(val(f, 'agendadoEm')).toISOString(), prioridade: val(f, 'prioridade'), observacoes: val(f, 'observacoes') || null, revisao: item?.revisao ?? 0 }); done();
    }}><EntitySelect path="/clientes" name="clienteId" label="Cliente" initial={item?.clienteId} /><EntitySelect path="/veiculos" name="veiculoId" label="Veículo (opcional)" required={false} initial={item?.veiculoId ?? ''} />
      <Field label="Motivo do contato"><textarea name="motivo" required maxLength={500} defaultValue={item?.motivo} /></Field>
      <Field label="Data e horário"><input type="datetime-local" name="agendadoEm" required defaultValue={dataLocal(item?.agendadoEm ?? new Date(now + 3600000).toISOString())} /></Field>
      <EntitySelect path="/usuarios?ativo=true" name="responsavelId" label="Responsável" commercial initial={item?.responsavelId ?? currentSession()?.usuarioId} />
      <Field label="Prioridade"><select name="prioridade" defaultValue={item?.prioridade ?? 'NORMAL'}><option value="NORMAL">Normal</option><option value="ALTA">Alta</option></select></Field>
      <Field label="Observações"><textarea name="observacoes" maxLength={2000} defaultValue={item?.observacoes ?? ''} /></Field>
    </Form></Drawer>}
    {closing && <Drawer title={closing.status === 'CONCLUIDO' ? 'Concluir retorno' : 'Cancelar retorno'} close={close}><p>{closing.item.cliente} · {closing.item.motivo}</p>
      <Form close={close} confirmation="Encerrar este retorno? O histórico será preservado." save={async f => { await api(`/retornos/${closing.item.id}/situacao`, 'PUT', { revisao: closing.item.revisao, status: closing.status, resultado: val(f, 'resultado') }); done(); }}>
        <Field label={closing.status === 'CONCLUIDO' ? 'Resultado do contato' : 'Motivo do cancelamento'}><textarea name="resultado" required maxLength={2000} /></Field></Form></Drawer>}
  </section>;
}
