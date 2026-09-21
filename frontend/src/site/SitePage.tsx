import { useEffect, useState } from 'react';
import { AtSign, Clock, MapPin, MessageCircle, Phone, Wrench } from 'lucide-react';
import './site.css';

/**
 * Site público da empresa. Um template só, pintado pelo que a API devolve para cada slug.
 *
 * É informativo por decisão: nada aqui captura contato, agenda ou cadastra ninguém. Quem quiser
 * falar liga, chama no WhatsApp ou vai até o endereço — que é exatamente o que alguém faz depois de
 * procurar uma oficina no telefone.
 */
interface Site {
  nome: string; frase: string | null; sobre: string | null; servicos: string[];
  endereco: string | null; horario: string | null; telefone: string | null;
  whatsapp: string | null; instagram: string | null;
  corPrimaria: string; corSecundaria: string;
  logoId: string | null; capaId: string | null; revisao: number;
}

const COR = /^#[0-9a-fA-F]{6}$/;

/** Preto ou branco sobre a cor da empresa, pelo que de fato contrasta — não pelo que parece bonito. */
function textoSobre(cor: string) {
  const [r, g, b] = [1, 3, 5].map(i => parseInt(cor.slice(i, i + 2), 16) / 255)
    .map(c => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b > 0.42 ? '#151a17' : '#ffffff';
}

function digitos(valor: string) { return valor.replace(/\D/g, ''); }

export default function SitePage({ slug }: { slug: string }) {
  const [site, setSite] = useState<Site>();
  const [erro, setErro] = useState(false);

  useEffect(() => {
    let ativo = true;
    const base = (import.meta.env?.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, '') ?? '';
    fetch(`${base}/api/v1/site/${encodeURIComponent(slug)}`, { headers: { Accept: 'application/json' } })
      .then(r => (r.ok ? r.json() : Promise.reject(new Error(String(r.status)))))
      .then(valor => { if (ativo) setSite(valor); })
      .catch(() => { if (ativo) setErro(true); });
    return () => { ativo = false; };
  }, [slug]);

  useEffect(() => {
    if (!site) return;
    document.title = site.nome;
    const raiz = document.documentElement;
    if (COR.test(site.corPrimaria)) {
      raiz.style.setProperty('--empresa', site.corPrimaria);
      raiz.style.setProperty('--empresa-texto', textoSobre(site.corPrimaria));
    }
    if (COR.test(site.corSecundaria)) raiz.style.setProperty('--empresa-escura', site.corSecundaria);
  }, [site]);

  if (erro)
    return <main className="site-vazio"><h1>Página indisponível</h1><p>Este endereço não corresponde a uma empresa com site publicado.</p></main>;
  if (!site) return <main className="site-vazio"><p role="status">Carregando…</p></main>;

  const base = (import.meta.env?.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, '') ?? '';
  const imagem = (tipo: 'logo' | 'capa', id: string) => `${base}/api/v1/site/${encodeURIComponent(slug)}/imagens/${tipo}/${id}`;
  const whats = site.whatsapp ? `https://wa.me/${digitos(site.whatsapp)}` : null;
  const secoes = [
    site.servicos.length ? { id: 'servicos', nome: 'Serviços' } : null,
    site.sobre ? { id: 'sobre', nome: 'Sobre' } : null,
    { id: 'contato', nome: 'Contato' },
  ].filter(Boolean) as { id: string; nome: string }[];

  return <div className="site">
    <header className="site-topo">
      <a className="site-marca" href="#inicio">
        {site.logoId ? <img src={imagem('logo', site.logoId)} alt="" /> : null}
        <span>{site.nome}</span>
      </a>
      <nav aria-label="Seções do site">
        {secoes.map(s => <a key={s.id} href={`#${s.id}`}>{s.nome}</a>)}
      </nav>
    </header>

    <main>
      <section className="site-inicio" id="inicio">
        {site.capaId ? <img className="site-capa" src={imagem('capa', site.capaId)} alt="" /> : null}
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
