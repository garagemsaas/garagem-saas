import { Drawer, Icon } from './ui';
import type { Page } from './navigation';
import type { Role } from './model';
import './GettingStarted.css';
import InitialSetup from './InitialSetup';

export default function GettingStarted({ role, close, navigate, recovery }: {
  role: Role; close: () => void; navigate: (page: Page) => void; recovery: () => void;
}) {
  const canWrite = role !== 'MECANICO';
  return <Drawer title="Primeiros passos" close={close}>
    <p>Um guia da rotina da oficina em que você está conectado. Nenhum cadastro é criado ao abrir este guia.</p>
    {canWrite && <InitialSetup role={role} navigate={navigate} />}
    <ol className="getting-started">
      {canWrite && <>
        <li><h3>Cadastre o cliente</h3><p>Em Clientes, busque primeiro pelo nome para evitar duplicidades. Se não encontrar, cadastre o contato.</p><button onClick={() => navigate('clients')}><Icon name="clients" />Abrir clientes</button></li>
        <li><h3>Vincule o veículo</h3><p>Em Veículos, confira a placa e selecione o proprietário. O cliente precisa estar cadastrado antes do veículo.</p><button onClick={() => navigate('vehicles')}><Icon name="vehicles" />Abrir veículos</button></li>
      </>}
      <li><h3>{canWrite ? 'Abra a ordem de serviço' : 'Encontre a ordem de serviço'}</h3><p>{canWrite ? 'Em Ordens de Serviço, crie a OS selecionando o veículo e descrevendo o relato. O cliente vinculado será incluído automaticamente.' : 'Em Ordens de Serviço, use a busca para localizar a OS. Abra o detalhe para consultar o veículo e registrar o trabalho permitido ao seu perfil.'}</p><button onClick={() => navigate('orders')}><Icon name="orders" />Abrir ordens de serviço</button></li>
      <li><h3>Registre o diagnóstico</h3><p>No detalhe da OS, confira o checklist de entrada, adicione fotos e classifique os itens do diagnóstico. Revise o status e a previsão de entrega.</p></li>
      {canWrite && <>
        <li><h3>Disponibilize e compartilhe o orçamento</h3><p>No detalhe da OS, crie o orçamento, confira a versão e use “Disponibilizar orçamento”. Depois, gere o link do cliente, copie e envie pelo canal combinado.</p><p>Disponibilizar não envia uma mensagem. O envio é manual; o cliente poderá aprovar ou recusar pelo link. Confira o destinatário e não compartilhe o link com terceiros.</p></li>
        <li><h3>Retome oportunidades</h3><p>Em Dinheiro Esquecido, identifique oportunidades e confira a origem de cada valor antes de registrar um contato. Potencial não é receita: marque recuperação somente com o valor confirmado.</p><button onClick={recovery}><Icon name="recovery" />Abrir oportunidades</button></li>
      </>}
    </ol>
    <section className="getting-started-note" aria-label="Limites da versão">
      <h3>O que ainda está em preparação</h3>
      <p>Agenda, pátio, catálogo de peças e notificações em tempo real ainda não estão disponíveis. Os itens “Em breve” não representam funções contratadas.</p>
      <p>Se uma ação falhar, leia o aviso antes de repetir. Se houver conflito, atualize os dados e confira o que já foi salvo.</p>
    </section>
  </Drawer>;
}
