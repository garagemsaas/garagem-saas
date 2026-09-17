import { useEffect } from 'react';
import { Brand, Icon } from './ui';
import './Marketing.css';

const help = [
  ['Como recebo acesso?', 'A equipe responsável pelo Garagem precisa provisionar sua oficina e o primeiro usuário. Não há cadastro público automático. Combine escopo e condições antes da ativação. Nunca envie sua senha por mensagem.'],
  ['Por onde começo?', 'Entre com o identificador da sua oficina, e-mail e senha. Abra Primeiros passos no menu e consulte a preparação inicial. Cadastre o cliente, vincule o veículo e depois abra a OS. O proprietário gerencia os usuários em Equipe.'],
  ['Como envio um orçamento?', 'No detalhe da OS, crie e confira a versão do orçamento. Use Disponibilizar orçamento, gere o link do cliente e copie o endereço. Envie manualmente pelo canal combinado. Disponibilizar ou copiar não envia uma mensagem.'],
  ['Como o cliente aprova?', 'O destinatário abre o link de acompanhamento, confere a versão e decide sobre o orçamento disponível. O link dá acesso às informações do atendimento: compartilhe apenas com o destinatário correto. Se expirar, gere outro pelo detalhe da OS.'],
  ['O que significa Dinheiro Esquecido?', 'São oportunidades identificadas a partir dos registros da oficina: orçamento sem resposta, revisão atrasada ou reavaliação pendente. Confira a origem e registre o contato. Valor potencial é uma estimativa; registre como recuperado apenas o valor confirmado. Isso não substitui controle de pagamentos.'],
  ['E se uma ação falhar?', 'Leia o aviso e confira se a operação já foi salva antes de tentar novamente. Em conflito, atualize os dados e revise o registro. Se a sessão expirar, entre novamente. Ao pedir ajuda, informe a tela, horário e identificador do erro, quando exibido. Não envie senhas, tokens ou links de clientes.'],
  ['Agenda, estoque e envio automático estão incluídos?', 'Não. Agenda operacional, gestão do pátio, catálogo/estoque, financeiro/fiscal e mensagens automáticas não fazem parte da oferta apresentada. Os itens Em breve não são funcionalidades contratadas.'],
];

function PublicHeader() {
  return <header className="commercial-header"><a href="/institucional" aria-label="Garagem, início"><Brand /></a><nav aria-label="Navegação institucional"><a href="/institucional#produto">Produto</a><a href="/institucional#planos">Plano</a><a href="/ajuda">Ajuda</a><a className="commercial-button compact" href="/">Entrar <Icon name="forward" size={16} /></a></nav></header>;
}
function Footer() {
  return <footer className="commercial-footer"><span>garagem / Gestão de oficinas</span><a href="/ajuda">Ajuda e primeiros passos</a><span>Envio de mensagens manual. Condições sob consulta.</span></footer>;
}
export default function Marketing({ helpPage = false }: { helpPage?: boolean }) {
  useEffect(() => {
    const previous = document.title;
    document.title = helpPage ? 'Ajuda e primeiros passos · Garagem' : 'Garagem · Sua oficina já tem clientes. Faça eles voltarem.';
    return () => { document.title = previous; };
  }, [helpPage]);
  return <div className="commercial"><a className="skip-link" href="#commercial-main">Pular para o conteúdo</a><PublicHeader />
    {helpPage ? <main id="commercial-main" className="commercial-help">
      <span className="commercial-eyebrow">PARA A ROTINA DA OFICINA</span><h1>Um começo bem organizado.</h1><p className="commercial-lead">Do primeiro acesso à aprovação do orçamento. Consulte as orientações sem precisar entrar no sistema.</p>
      <div className="commercial-faq">{help.map(([title, body]) => <details key={title}><summary>{title}</summary><p>{body}</p></details>)}</div>
      <a className="commercial-button" href="/">Acessar minha oficina <Icon name="forward" size={18} /></a>
    </main> : <main id="commercial-main">
      <section className="commercial-hero">
        <div><span className="commercial-eyebrow">GESTÃO PARA OFICINAS MECÂNICAS</span><h1>Sua oficina já tem clientes.<br /><em>Faça eles voltarem.</em></h1><p className="commercial-lead">Organize cada atendimento e encontre serviços que merecem uma nova conversa. Da ordem de serviço às oportunidades de retorno, em um só lugar.</p><div className="commercial-actions"><a className="commercial-button filled" href="#demonstracao">Conhecer o Garagem <Icon name="forward" size={18} /></a><a href="#planos">Ver a proposta</a></div><p className="commercial-caption">Clientes, veículos, OS e orçamentos. Sem prometer receita que ainda não aconteceu.</p></div>
        <aside className="commercial-example" aria-label="Exemplo ilustrativo de oportunidade"><div className="commercial-example-top"><Icon name="recovery" /><span>DINHEIRO ESQUECIDO</span></div><span className="commercial-caption">Exemplo ilustrativo, sem dados reais</span><h2>Um orçamento sem resposta.<br />Uma conversa a retomar.</h2><dl><div><dt>Origem</dt><dd>Versão do orçamento de uma OS</dd></div><div><dt>Próxima ação</dt><dd>Confirmar o interesse do cliente</dd></div><div><dt>Registro</dt><dd>Contato e resultado da conversa</dd></div></dl><p>O valor vem do registro de origem. A recuperação só entra quando você a confirma.</p></aside>
      </section>
      <section className="commercial-section" id="produto"><div className="commercial-section-title"><span className="commercial-eyebrow">ATENDIMENTO COM CONTEXTO</span><h2>Cada etapa tem seu lugar.</h2></div><div className="commercial-features">
        <article><Icon name="orders" size={28} /><h3>A operação à vista</h3><p>Cliente, veículo e histórico ligados à OS. Checklist, fotos e diagnóstico acompanham o serviço.</p></article>
        <article><Icon name="check" size={28} /><h3>Um orçamento claro</h3><p>Versões preservadas e decisão do cliente pelo link de acompanhamento. Você compartilha o link manualmente.</p></article>
        <article><Icon name="recovery" size={28} /><h3>Motivos para retomar contato</h3><p>Orçamentos sem resposta, revisões atrasadas e reavaliações pendentes, com origem e próxima ação visíveis.</p></article>
      </div></section>
      <section className="commercial-section commercial-demo" id="demonstracao">
        <div><span className="commercial-eyebrow">VEJA O FLUXO</span><h2>Conheça antes de começar.</h2><p>Uma demonstração curta, sem áudio, legendada, com dados fictícios. As telas mostram o fluxo do produto, sem resultados de clientes reais.</p><a href="/materiais/garagem-apresentacao.pptx" download>Baixar apresentação comercial <Icon name="forward" size={16} /></a></div>
        <video controls playsInline preload="none" poster="/materiais/garagem-demonstracao.png" aria-label="Demonstração do Garagem com dados fictícios">
          <source src="/materiais/garagem-demonstracao.webm" type="video/webm" />
          <track kind="captions" src="/materiais/garagem-demonstracao.vtt" srcLang="pt-BR" label="Português" />
          Seu navegador não reproduz este vídeo. <a href="/materiais/garagem-demonstracao.webm">Baixar demonstração</a>
        </video>
        <details className="commercial-transcript"><summary>Ler a descrição da demonstração</summary><p>A demonstração apresenta o painel de uma oficina fictícia, o guia de primeiros passos e a consulta de clientes. Em seguida, mostra a carteira de Dinheiro Esquecido, a origem de uma oportunidade e a ação de registrar contato. O vídeo encerra lembrando que estimativa não é receita e que preços e condições dependem da proposta.</p></details>
      </section>
      <section className="commercial-section commercial-plan" id="planos"><div><span className="commercial-eyebrow">UMA PROPOSTA OBJETIVA</span><h2>Garagem Oficina</h2><p className="commercial-lead">Para organizar o atendimento e acompanhar oportunidades de retorno.</p><p>Clientes e veículos, ordens de serviço, diagnóstico, orçamento com versões, acompanhamento público e Dinheiro Esquecido.</p></div><div><h3>Condições sob consulta</h3><p>Preço, quantidade de usuários, armazenamento, implantação e suporte serão definidos na proposta. Não há contratação ou cobrança automática nesta página.</p><a className="commercial-button filled" href="#contratacao">Como contratar <Icon name="forward" size={18} /></a><p className="commercial-caption">Fora desta oferta: estoque, financeiro/fiscal, agenda operacional, gestão do pátio e envio automático de mensagens.</p></div></section>
      <section className="commercial-section commercial-start" id="contratacao"><span className="commercial-eyebrow">PRÓXIMO PASSO</span><h2>Comece pela rotina da sua oficina.</h2><p>Converse com quem apresentou o Garagem à sua equipe para combinar uma demonstração e receber a proposta. Após o acordo, a equipe responsável prepara o acesso da oficina.</p><p>Já recebeu suas credenciais? Entre e abra “Primeiros passos” para conferir a preparação inicial.</p><div className="commercial-actions"><a className="commercial-button" href="/">Acessar minha oficina <Icon name="forward" size={18} /></a><a href="/ajuda">Consultar ajuda</a></div></section>
    </main>}
    <Footer />
  </div>;
}
