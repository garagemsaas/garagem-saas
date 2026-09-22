import { useEffect, useState } from 'react';
import SiteView from './SiteView';
import type { SiteConteudo } from './SiteView';

/**
 * Endereço público do site da empresa. Busca o conteúdo pelo slug e entrega ao template — que é o
 * mesmo usado na pré-visualização dentro de Configurações.
 */
interface Site extends SiteConteudo {
  logoId: string | null;
  capaId: string | null;
  revisao: number;
}

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

  useEffect(() => { if (site) document.title = site.nome; }, [site]);

  if (erro)
    return <main className="site-vazio"><h1>Página indisponível</h1><p>Este endereço não corresponde a uma empresa com site publicado.</p></main>;
  if (!site) return <main className="site-vazio"><p role="status">Carregando…</p></main>;

  const base = (import.meta.env?.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, '') ?? '';
  const imagem = (tipo: 'logo' | 'capa', id: string) => `${base}/api/v1/site/${encodeURIComponent(slug)}/imagens/${tipo}/${id}`;
  return <SiteView site={site}
    logo={site.logoId ? imagem('logo', site.logoId) : null}
    capa={site.capaId ? imagem('capa', site.capaId) : null} />;
}
