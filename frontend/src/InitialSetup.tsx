import { useEffect, useState } from 'react';
import { listPage } from './api';
import { Icon } from './ui';
import { PageState } from './PageState';
import type { Page } from './navigation';
import type { Role } from './model';

type Counts = { clients: number; vehicles: number; orders: number; team: number | null };
export default function InitialSetup({ role, navigate }: { role: Role; navigate: (page: Page) => void }) {
  const [counts, setCounts] = useState<Counts>();
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    // oxlint-disable-next-line react/set-state-in-effect -- Remote setup status lifecycle.
    setCounts(undefined); setError('');
    Promise.all([listPage('/clientes', 0), listPage('/veiculos', 0), listPage('/ordens-servico', 0), role === 'OWNER' ? listPage('/usuarios?ativo=true', 0) : Promise.resolve(null)])
      .then(([clients, vehicles, orders, team]) => { if (active) setCounts({ clients: clients.total, vehicles: vehicles.total, orders: orders.total, team: team?.total ?? null }); })
      .catch((reason: unknown) => { if (active) setError(reason instanceof Error ? reason.message : 'Não foi possível verificar a preparação.'); });
    return () => { active = false; };
  }, [role, attempt]);
  const steps: { page: Page; label: string; done: boolean; hint: string }[] = counts ? [
    ...(role === 'OWNER' ? [{ page: 'team' as const, label: 'Conferir equipe e acessos', done: (counts.team ?? 0) > 1, hint: 'Cadastre somente quem precisa de acesso e escolha o perfil adequado. Se trabalha sozinho, não precisa criar outro usuário.' }] : []),
    { page: 'clients', label: 'Cadastrar o primeiro cliente', done: counts.clients > 0, hint: 'Confira o nome e o contato antes de salvar.' },
    { page: 'vehicles', label: 'Vincular o primeiro veículo', done: counts.vehicles > 0, hint: 'Confira a placa e selecione o cliente proprietário.' },
    { page: 'orders', label: 'Abrir a primeira OS', done: counts.orders > 0, hint: 'Selecione o veículo e registre o relato do cliente.' },
  ] : [];
  return <section className="initial-setup" aria-label="Preparação inicial da oficina">
    <h3>Preparação inicial da oficina</h3>
    <p>Esta consulta usa os registros da oficina conectada. Encontrar um cadastro não confirma que ele está correto: revise os dados antes de começar.</p>
    {error ? <PageState state="error" title="Não foi possível verificar a preparação" retry={() => setAttempt(n => n + 1)}>{error}</PageState> : !counts ? <p role="status">Verificando os cadastros da oficina…</p> : <ul>{steps.map(step => <li key={step.page}><div><Icon name={step.done ? 'good' : 'clock'} size={18} /><strong>{step.label}</strong></div><small>{step.done ? 'Registro encontrado — confira os dados' : step.page === 'team' ? 'Sem usuário adicional — opcional' : 'Nenhum registro encontrado'}</small><p>{step.hint}</p><button onClick={() => navigate(step.page)}>Ir para {step.page === 'team' ? 'equipe' : step.page === 'clients' ? 'clientes' : step.page === 'vehicles' ? 'veículos' : 'ordens de serviço'}<Icon name="forward" size={16} /></button></li>)}</ul>}
    <p>O identificador da oficina e o primeiro acesso são preparados pela equipe responsável pela Plataforma Automotiva. O OWNER configura a identidade em Configurações. Módulos e situação são administrados pela plataforma.</p>
  </section>;
}
