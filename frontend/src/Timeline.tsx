import type { Order } from './model';
import { date } from './model';
import { Empty } from './ui';

export function Timeline({ events }: { events: Order['timeline'] }) {
  if (!events.length) return <Empty title="Nenhum evento registrado">O histórico aparecerá aqui conforme o serviço avançar.</Empty>;
  return <ol className="timeline" aria-label="Histórico da ordem de serviço">
    {[...events].sort((a, b) => a.criadoEm.localeCompare(b.criadoEm)).map(event => <li key={event.id}>
      <time dateTime={event.criadoEm}>{date(event.criadoEm, true)}</time>
      <div><strong>{event.descricao}</strong><small>{event.origem === 'LINK_PUBLICO' ? 'Cliente · link público' : 'Equipe da oficina'}</small></div>
    </li>)}
  </ol>;
}
