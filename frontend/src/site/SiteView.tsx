import { AtSign, Clock, MapPin, MessageCircle, Phone, Wrench } from 'lucide-react';
import { legivel, luminancia } from '../contraste';
import './site.css';

/**
 * O template do site da empresa, e o único que existe.
 *
 * <p>Recebe conteúdo e identidade prontos e não busca nada: é o que permite a mesma página servir
 * ao endereço público e à pré-visualização dentro de Configurações, sem um segundo template para
 * manter em dia. Uma empresa nova se diferencia por dados — logo, cores, capa, textos —, nunca por
 * um componente, uma pasta ou um CSS próprio.
 *
 * <p>É informativo por decisão: nada aqui captura contato, agenda ou cadastra ninguém. Quem quiser
 * falar liga, chama no WhatsApp ou vai até o endereço — que é exatamente o que alguém faz depois de
 * procurar uma oficina no telefone.
 */
export interface SiteConteudo {
  nome: string;
  frase: string | null;
  sobre: string | null;
  servicos: string[];
  endereco: string | null;
  horario: string | null;
  telefone: string | null;
  whatsapp: string | null;
  instagram: string | null;
  corPrimaria: string;
  corSecundaria: string;
}

const COR = /^#[0-9a-fA-F]{6}$/;

/** Preto ou branco sobre a cor da empresa, pelo que de fato contrasta — não pelo que parece bonito. */
function textoSobre(cor: string) {
  return luminancia(cor) > 0.42 ? '#151a17' : '#ffffff';
}

function digitos(valor: string) { return valor.replace(/\D/g, ''); }

export default function SiteView({ site, logo, capa }: {
  site: SiteConteudo;
  logo?: string | null;
  capa?: string | null;
}) {
  const whats = site.whatsapp ? `https://wa.me/${digitos(site.whatsapp)}` : null;
  const secoes = [
    site.servicos.length ? { id: 'servicos', nome: 'Serviços' } : null,
    site.sobre ? { id: 'sobre', nome: 'Sobre' } : null,
    { id: 'contato', nome: 'Contato' },
  ].filter(Boolean) as { id: string; nome: string }[];

  // As cores vão no próprio elemento, e não em :root. A folha declara os padrões em `.site`, que
  // por ser o mesmo elemento venceria qualquer valor herdado do documento — era por isso que todo
  // site saía com a cor de exemplo, independentemente da que a empresa escolheu.
  const cores: Record<string, string> = {};
  if (COR.test(site.corPrimaria)) {
    cores['--empresa'] = site.corPrimaria;
    cores['--empresa-texto'] = textoSobre(site.corPrimaria);
    // A cor crua pinta áreas; como texto miúdo — links e ícones de contato — vai a versão legível.
    cores['--empresa-legivel'] = legivel(site.corPrimaria);
  }
  // A faixa de abertura leva texto branco por cima, então a cor secundária precisa suportá-lo.
  if (COR.test(site.corSecundaria)) cores['--empresa-escura'] = legivel(site.corSecundaria);

  return <div className="site" style={cores as React.CSSProperties}>
    <header className="site-topo">
      <a className="site-marca" href="#inicio">
        {logo ? <img src={logo} alt="" /> : null}
        <span>{site.nome}</span>
      </a>
      <nav aria-label="Seções do site">
        {secoes.map(s => <a key={s.id} href={`#${s.id}`}>{s.nome}</a>)}
      </nav>
    </header>

    <main>
      <section className="site-inicio" id="inicio">
        {capa ? <img className="site-capa" src={capa} alt="" /> : null}
        <div className="site-inicio-texto">
          <h1>{site.nome}</h1>
          {site.frase ? <p className="site-frase">{site.frase}</p> : null}
          {whats ? <a className="site-botao" href={whats} target="_blank" rel="noreferrer">
            <MessageCircle size={18} aria-hidden="true" /> Falar no WhatsApp
          </a> : null}
        </div>
      </section>

      {site.servicos.length ? <section className="site-secao" id="servicos">
        <h2>Serviços</h2>
        <ul className="site-servicos">
          {site.servicos.map(servico => <li key={servico}>
            <Wrench size={18} aria-hidden="true" /><span>{servico}</span>
          </li>)}
        </ul>
      </section> : null}

      {site.sobre ? <section className="site-secao site-sobre" id="sobre">
        <h2>Sobre</h2>
        {site.sobre.split(/\n{2,}/).map(paragrafo => <p key={paragrafo}>{paragrafo}</p>)}
      </section> : null}

      <section className="site-secao" id="contato">
        <h2>Contato</h2>
        <dl className="site-contato">
          {site.endereco ? <div>
            <dt><MapPin size={18} aria-hidden="true" /> Endereço</dt>
            <dd>
              {site.endereco}
              <a href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(site.endereco)}`} target="_blank" rel="noreferrer">Ver no mapa</a>
            </dd>
          </div> : null}
          {site.horario ? <div>
            <dt><Clock size={18} aria-hidden="true" /> Horário</dt>
            <dd>{site.horario}</dd>
          </div> : null}
          {site.telefone ? <div>
            <dt><Phone size={18} aria-hidden="true" /> Telefone</dt>
            <dd><a href={`tel:${digitos(site.telefone)}`}>{site.telefone}</a></dd>
          </div> : null}
          {whats ? <div>
            <dt><MessageCircle size={18} aria-hidden="true" /> WhatsApp</dt>
            <dd><a href={whats} target="_blank" rel="noreferrer">Abrir conversa</a></dd>
          </div> : null}
          {site.instagram ? <div>
            <dt><AtSign size={18} aria-hidden="true" /> Instagram</dt>
            <dd><a href={`https://instagram.com/${site.instagram}`} target="_blank" rel="noreferrer">@{site.instagram}</a></dd>
          </div> : null}
        </dl>
      </section>
    </main>

    <footer className="site-rodape">
      <span>{site.nome}</span>
      <span className="site-assinatura">Site desenvolvido por Plataforma Automotiva</span>
    </footer>
  </div>;
}
