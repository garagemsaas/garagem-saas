import { ResourceState } from './shared';
import { useResource } from './dados';
import { PageState } from '../PageState';
import { WhatsApp } from '../WhatsApp';
import { convite } from '../whatsapp-mensagem';

interface Retorno {
  tipo: string; id: string; cliente: string; telefone: string | null;
  veiculo: string | null; detalhe: string; desde: string;
}

/** O motivo da ligação em uma frase, para não obrigar ninguém a decorar o nome do tipo. */
const motivos: Record<string, string> = {
  INTERESSADO_SEM_RETORNO: 'Interessado sem retorno',
  NEGOCIACAO_PARADA: 'Negociação parada',
  PROPOSTA_EM_ABERTO: 'Proposta em aberto',
  RESERVA_CANCELADA: 'Reserva cancelada',
};

function dias(desde: string) {
  const total = Math.floor((Date.now() - new Date(desde).getTime()) / 86400000);
  return total <= 0 ? 'hoje' : total === 1 ? 'há 1 dia' : `há ${total} dias`;
}

/**
 * Quem está esperando uma resposta da loja. Chamar no WhatsApp abre a conversa e nada mais: o
 * registro de que o contato aconteceu continua sendo uma decisão de quem falou com a pessoa.
 */
export default function RetornosPage({ empresa }: { empresa: string }) {
  // Lista curta por definição — são as pessoas paradas há mais de uma semana, não um acervo.
  // Paginar aqui daria um controle que ninguém usaria e esconderia o fim da fila.
  const r = useResource<Retorno[]>('/revenda/retornos');
  return <><div className="page-heading"><div><h1>Retornos</h1><p>Pessoas e negócios parados há mais de uma semana.</p></div>
      <button onClick={r.reload}>Atualizar lista</button></div>
    {r.data ? (r.data.length === 0
      ? <PageState state="empty" title="Ninguém esperando retorno por aqui." />
      : <div className="dealer-cards">{r.data.map(item => <article className="dealer-card" key={`${item.tipo}-${item.id}`}>
          <span className="dealer-status">{motivos[item.tipo] ?? item.tipo}</span>
          <h2>{item.cliente}</h2>
          {item.veiculo ? <p>{item.veiculo}</p> : null}
          <p>{item.detalhe} · {dias(item.desde)}</p>
          <div className="dealer-actions">
            <WhatsApp telefone={item.telefone} rotulo="Chamar no WhatsApp"
              mensagem={convite(empresa, `${item.veiculo ? `Sobre o ${item.veiculo}: ` : ''}podemos retomar nossa conversa?`)} />
          </div>
        </article>)}</div>)
      : <ResourceState error={r.error} retry={r.reload} />}
  </>;
}
