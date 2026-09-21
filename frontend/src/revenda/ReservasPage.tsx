import { useState } from 'react';
import { Field } from '../ui';
import { Collection } from './shared';
import { WhatsApp } from '../WhatsApp';
import { convite } from '../whatsapp-mensagem';
import { rotulo } from './types';

interface Reserva {
  id: string; estoqueId: string; clienteId: string; vendedorId: string;
  validade: string; observacoes: string | null; status: string; revisao: number; criadoEm: string;
  clienteNome: string; telefone: string | null; veiculoDescricao: string;
}

/**
 * Quais carros estão presos e até quando. Reservar e cancelar continuam sendo ações do carro — é lá
 * que a pessoa está quando decide —, mas saber o que está reservado é pergunta de segunda-feira de
 * manhã, e para isso não se abre carro por carro.
 */
export default function ReservasPage({ abrirCarro, empresa }: { abrirCarro: (estoqueId: string) => void; empresa: string }) {
  const [status, setStatus] = useState('ATIVA');
  return <><h1>Reservas</h1><p>Para reservar, cancelar ou vender, abra o carro.</p>
    <form className="dealer-filters" onSubmit={e => e.preventDefault()}>
      <Field label="Situação"><select value={status} onChange={e => setStatus(e.target.value)}>
        <option value="">Todas</option>
        {['ATIVA', 'CONCLUIDA', 'CANCELADA', 'EXPIRADA'].map(s => <option key={s} value={s}>{rotulo(s)}</option>)}
      </select></Field>
    </form>
    <Collection<Reserva> key={status} path={`/revenda/reservas?status=${status}`} empty="Nenhuma reserva registrada."
      render={r => <>
        <span className="dealer-status">{rotulo(r.status)}</span>
        <h2>{r.clienteNome}</h2>
        <p>{r.veiculoDescricao}</p>
        <p>Válida até {new Date(r.validade).toLocaleString('pt-BR')}</p>
        {r.observacoes ? <p>{r.observacoes}</p> : null}
        <div className="dealer-actions"><WhatsApp telefone={r.telefone} mensagem={convite(empresa, `Sobre a reserva do ${r.veiculoDescricao}: podemos confirmar?`)} /><button onClick={() => abrirCarro(r.estoqueId)}>Abrir carro</button></div>
      </>} />
  </>;
}
