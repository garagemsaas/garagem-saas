import { useEffect } from 'react';
import type { ReactNode } from 'react';
import { useImagemEmpresa } from './empresa-imagem';
import { BrandingContext } from './branding-context';
import type { Branding } from './branding-context';

export function BrandingProvider({ branding, base = '/empresa', children }: { branding: Branding; base?: string; children: ReactNode }) {
  const logo = useImagemEmpresa(branding.logoId ? `${base}/imagens/logo/${branding.logoId}` : undefined);
  const favicon = useImagemEmpresa(branding.faviconId ? `${base}/imagens/favicon/${branding.faviconId}` : undefined);
  useEffect(() => {
    const title = document.title; document.title = branding.nomeExibicao;
    return () => { document.title = title; };
  }, [branding.nomeExibicao]);
  useEffect(() => {
    if (!favicon) return;
    const previous = Array.from(document.querySelectorAll<HTMLLinkElement>('link[rel~="icon"]'));
    previous.forEach(link => link.remove());
    const link = document.createElement('link'); link.rel = 'icon'; link.href = favicon; document.head.append(link);
    return () => { link.remove(); previous.forEach(item => document.head.append(item)); };
  }, [favicon]);
  return <BrandingContext.Provider value={{ branding, logo }}>{children}</BrandingContext.Provider>;
}
