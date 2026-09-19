import { useState } from 'react';
import { Brand } from '../ui';
import type { Empresa } from '../branding-context';
import type { Role } from '../model';
import CompanySettings from '../CompanySettings';
import DashboardPage from './DashboardPage';
import EstoquePage from './EstoquePage';
import AvaliacoesPage from './AvaliacoesPage';
import LeadsPage from './LeadsPage';
import PropostasPage from './PropostasPage';
import VendasPage from './VendasPage';
import CadastrosPage from './CadastrosPage';
import './revenda.css';
const pages = { dashboard: 'Visão geral', estoque: 'Estoque', avaliacoes: 'Avaliações', leads: 'Leads', propostas: 'Propostas', vendas: 'Vendas', clientes: 'Clientes', veiculos: 'Veículos', configuracoes: 'Configurações' };
export default function RevendaApp({ empresa, update, userId, role, logout, switchOffice }: { empresa: Empresa; update: (e: Empresa) => void; userId: string; role: Role; logout: () => void; switchOffice: (osId?: string) => void }) {
  const [page, setPage] = useState<keyof typeof pages>('dashboard'); const hybrid = empresa.modulos.includes('OFICINA');
  return <div className="dealer-layout"><a className="skip-link" href="#dealer-main">Ir para conteúdo</a><aside className="dealer-sidebar"><Brand /><p>REVENDA DE VEÍCULOS</p><nav aria-label="Navegação da revenda">{Object.entries(pages).filter(([p]) => p !== 'configuracoes' || role === 'OWNER').map(([key, name]) => <button key={key} aria-current={page === key ? 'page' : undefined} onClick={() => setPage(key as keyof typeof pages)}>{name}</button>)}</nav>{hybrid && <button onClick={() => switchOffice()}>Ir para oficina</button>}<button onClick={logout}>Sair</button></aside>
    <main id="dealer-main" className="dealer-main" key={page} tabIndex={-1}>{page === 'dashboard' ? <DashboardPage /> : page === 'estoque' ? <EstoquePage userId={userId} hybrid={hybrid} openOrder={switchOffice} /> : page === 'avaliacoes' ? <AvaliacoesPage userId={userId} /> : page === 'leads' ? <LeadsPage userId={userId} /> : page === 'propostas' ? <PropostasPage userId={userId} /> : page === 'vendas' ? <VendasPage /> : page === 'clientes' || page === 'veiculos' ? <CadastrosPage kind={page} /> : <CompanySettings empresa={empresa} update={update} />}</main>
  </div>;
}
