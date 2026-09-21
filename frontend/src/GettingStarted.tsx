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
    <p>Um guia da rotina da oficina. Nada é criado ao abrir este guia.</p>
    {canWrite && <InitialSetup role={role} navigate={navigate} />}
    <ol className="getting-started">
      {canWrite && <>
        <li><h3>Cadastre o cliente</h3><p>Em Clientes, busque primeiro pelo nome para evitar duplicidades. Se não encontrar, cadastre o contato.</p><button onClick={() => navigate('clients')}><Icon name="clients" />Abrir clientes</button></li>
        <li><h3>Vincule o veículo</h3><p>Em Veículos, confira a placa e selecione o proprietário. O cliente precisa estar cadastrado antes do veículo.</p><button onClick={() => navigate('vehicles')}><Icon name="vehicles" />Abrir veículos</button></li>
      </>}
      <li><h3>{canWrite ? 'Abra o serviço' : 'Encontre o serviço'}</h3><p>{canWrite ? 'Em Serviços, selecione o veículo e descreva o relato. O cliente vinculado entra junto.' : 'Em Serviços, use a busca para localizar o atendimento. Abra o detalhe para consultar o veículo e registrar o seu trabalho.'}</p><button onClick={() => navigate('orders')}><Icon name="orders" />Abrir serviços</button></li>
      <li><h3>Registre o diagnóstico</h3><p>No detalhe do serviço, confira o checklist de entrada, adicione fotos e classifique os itens do diagnóstico. Revise o status e a previsão de entrega.</p></li>
      {canWrite && <>
        <li><h3>Disponibilize e compartilhe o orçamento</h3><p>No detalhe do serviço, crie o orçamento, confira a versão e use “Disponibilizar orçamento”. Depois, gere o link do cliente, copie e envie pelo canal combinado.</p><p>Disponibilizar não envia uma mensagem. O envio é manual; o cliente poderá aprovar ou recusar pelo link. Confira o destinatário e não compartilhe o link com terceiros.</p></li>
        <li><h3>Retome contatos</h3><p>Em Retornos, veja quem precisa de uma ligação e confira a origem de cada valor antes de registrar o contato. Valor previsto não é valor recebido: marque como recuperado só o que foi confirmado.</p><button onClick={recovery}><Icon name="recovery" />Abrir retornos</button></li>
      </>}
    </ol>
    <section className="getting-started-note" aria-label="Se algo falhar">
      <h3>Se uma ação falhar</h3>
      <p>Leia o aviso antes de repetir. Havendo conflito, atualize a tela e confira o que já foi salvo.</p>
    </section>
  </Drawer>;
}
