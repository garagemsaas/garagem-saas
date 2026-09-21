import { useState } from 'react';
import { Brand } from '../ui';
import type { Empresa } from '../branding-context';
import type { Role } from '../model';
import CompanySettings from '../CompanySettings';
import DashboardPage from './DashboardPage';
import EstoquePage from './EstoquePage';
import LeadsPage from './LeadsPage';
import PropostasPage from './PropostasPage';
import VendasPage from './VendasPage';
import ReservasPage from './ReservasPage';
import AvaliacoesPage from './AvaliacoesPage';
import CadastrosPage from './CadastrosPage';
import './revenda.css';
/**
 * Os nomes são os da conversa de quem vende carro: interessado, não lead; carros, não estoque.
 * Avaliações não têm item próprio — quem avalia um carro faz isso ao registrar a compra dele, e um
 * menu a mais para uma etapa de dentro de outra só aumenta a lista sem dar caminho novo a ninguém.
 */
const pages = { dashboard: 'Início', clientes: 'Clientes', estoque: 'Carros', leads: 'Interessados', propostas: 'Propostas', reservas: 'Reservas', vendas: 'Vendas', avaliacoes: 'Avaliações', configuracoes: 'Configurações' };
/** `avaliacoes` não entra em grupo nenhum: chega-se a ela pelo carro, que é o contexto dela. */
const grupos: { titulo: string; itens: (keyof typeof pages)[] }[] = [
  { titulo: '', itens: ['dashboard'] },
  { titulo: 'CLIENTES', itens: ['clientes'] },
  { titulo: 'REVENDA', itens: ['estoque', 'leads', 'propostas', 'reservas', 'vendas'] },
];
export default function RevendaApp({ empresa, update, userId, role, logout, switchOffice }: { empresa: Empresa; update: (e: Empresa) => void; userId: string; role: Role; logout: () => void; switchOffice: (osId?: string) => void }) {
  const [page, setPage] = useState<keyof typeof pages>('dashboard'); const hybrid = empresa.modulos.includes('OFICINA');
  return <div className="dealer-layout"><a className="skip-link" href="#dealer-main">Ir para conteúdo</a><aside className="dealer-sidebar"><Brand /><p>REVENDA DE VEÍCULOS</p><nav aria-label="Navegação da revenda">{grupos.map(g => <div key={g.titulo || 'inicio'}>{g.titulo && <span className="dealer-grupo">{g.titulo}</span>}{g.itens.map(key => <button key={key} aria-current={page === key ? 'page' : undefined} onClick={() => setPage(key)}>{pages[key]}</button>)}</div>)}<div><span className="dealer-grupo">GESTÃO</span>{role === 'OWNER' && <button aria-current={page === 'configuracoes' ? 'page' : undefined} onClick={() => setPage('configuracoes')}>{pages.configuracoes}</button>}</div></nav>{hybrid && <button onClick={() => switchOffice()}>Ir para oficina</button>}<button onClick={logout}>Sair</button></aside>
    <main id="dealer-main" className="dealer-main" key={page} tabIndex={-1}>{page === 'dashboard' ? <DashboardPage /> : page === 'estoque' ? <EstoquePage userId={userId} hybrid={hybrid} openOrder={switchOffice} abrirAvaliacoes={() => setPage('avaliacoes')} /> : page === 'avaliacoes' ? <AvaliacoesPage userId={userId} /> : page === 'reservas' ? <ReservasPage abrirCarro={() => setPage('estoque')} /> : page === 'leads' ? <LeadsPage userId={userId} /> : page === 'propostas' ? <PropostasPage userId={userId} /> : page === 'vendas' ? <VendasPage /> : page === 'clientes' ? <CadastrosPage kind={page} /> : <CompanySettings empresa={empresa} update={update} />}</main>
  </div>;
}
