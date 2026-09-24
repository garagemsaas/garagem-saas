import { useState } from 'react';
import { Brand } from '../ui';
import type { Empresa } from '../branding-context';
import type { Role } from '../model';
import TeamPage from '../TeamPage';
import Help from '../Help';
import DashboardPage from './DashboardPage';
import EstoquePage from './EstoquePage';
import LeadsPage from './LeadsPage';
import PropostasPage from './PropostasPage';
import VendasPage from './VendasPage';
import ReservasPage from './ReservasPage';
import RetornosPage from '../Retornos';
import AvaliacoesPage from './AvaliacoesPage';
import CadastrosPage from './CadastrosPage';
import './revenda.css';
/**
 * Os nomes são os da conversa de quem vende carro: interessado, não lead; carros, não estoque.
 * Avaliações não têm item próprio — quem avalia um carro faz isso ao registrar a compra dele, e um
 * menu a mais para uma etapa de dentro de outra só aumenta a lista sem dar caminho novo a ninguém.
 */
const pages = { dashboard: 'Início', clientes: 'Clientes', estoque: 'Carros', leads: 'Interessados', propostas: 'Propostas', reservas: 'Reservas', vendas: 'Vendas', retornos: 'Retornos', avaliacoes: 'Avaliações', team: 'Usuários', ajuda: 'Como podemos ajudar?' };
/** `avaliacoes` não entra em grupo nenhum: chega-se a ela pelo carro, que é o contexto dela. */
const grupos: { titulo: string; itens: (keyof typeof pages)[] }[] = [
  { titulo: '', itens: ['dashboard'] },
  { titulo: 'CLIENTES', itens: ['clientes'] },
  { titulo: 'REVENDA', itens: ['estoque', 'leads', 'propostas', 'reservas', 'vendas'] },
  { titulo: 'RETORNOS', itens: ['retornos'] },
];
export default function RevendaApp({ empresa, userId, role, logout }: { empresa: Empresa; userId: string; role: Role; logout: () => void; }) {
  const [page, setPage] = useState<keyof typeof pages>('dashboard');
  return <div className="dealer-layout"><a className="skip-link" href="#dealer-main">Ir para conteúdo</a><aside className="dealer-sidebar"><Brand /><p>REVENDA DE VEÍCULOS</p><nav aria-label="Navegação da revenda">{grupos.map(g => <div key={g.titulo || 'inicio'}>{g.titulo && <span className="dealer-grupo">{g.titulo}</span>}{g.itens.map(key => <button key={key} aria-current={page === key ? 'page' : undefined} onClick={() => setPage(key)}>{pages[key]}</button>)}</div>)}<div><span className="dealer-grupo">GESTÃO</span>{role === 'OWNER' && <button aria-current={page === 'team' ? 'page' : undefined} onClick={() => setPage('team')}>{pages.team}</button>}</div></nav><button onClick={() => setPage('ajuda')}>Como podemos ajudar?</button><button onClick={logout}>Sair</button></aside>
    <main id="dealer-main" className="dealer-main" key={page} tabIndex={-1}>{page === 'dashboard' ? <DashboardPage /> : page === 'estoque' ? <EstoquePage userId={userId} abrirAvaliacoes={() => setPage('avaliacoes')} /> : page === 'avaliacoes' ? <AvaliacoesPage userId={userId} /> : page === 'reservas' ? <ReservasPage abrirCarro={() => setPage('estoque')} empresa={empresa.branding.nomeExibicao} /> : page === 'retornos' ? <RetornosPage empresa={empresa.branding.nomeExibicao} /> : page === 'leads' ? <LeadsPage userId={userId} empresa={empresa.branding.nomeExibicao} /> : page === 'propostas' ? <PropostasPage userId={userId} empresa={empresa.branding.nomeExibicao} /> : page === 'vendas' ? <VendasPage empresa={empresa.branding.nomeExibicao} /> : page === 'clientes' ? <CadastrosPage kind={page} /> : page === 'team' && role === 'OWNER' ? <TeamPage userId={userId} dealer /> : <Help dealer />}</main>
  </div>;
}
