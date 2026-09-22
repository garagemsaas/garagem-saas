import { useEffect } from 'react';
import type { ReactNode } from 'react';
import { useImagemEmpresa } from './empresa-imagem';
import { luminancia } from './contraste';
import { BrandingContext } from './branding-context';
import type { Branding } from './branding-context';

function foreground(hex: string) {
  return luminancia(hex) > 0.179 ? '#111111' : '#ffffff';
}
export function BrandingProvider({ branding, base = '/empresa', children }: { branding: Branding; base?: string; children: ReactNode }) {
  const logo = useImagemEmpresa(branding.logoId ? `${base}/imagens/logo/${branding.logoId}` : undefined);
  const favicon = useImagemEmpresa(branding.faviconId ? `${base}/imagens/favicon/${branding.faviconId}` : undefined);
  useEffect(() => {
    const root = document.documentElement;
    const values = { '--accent': branding.corPrimaria, '--accent-hover': branding.corSecundaria,
      '--brand-on-primary': foreground(branding.corPrimaria), '--brand-on-secondary': foreground(branding.corSecundaria) };
    for (const [name, value] of Object.entries(values)) if (/^#[a-fA-F0-9]{6}$/.test(value)) root.style.setProperty(name, value);
    const title = document.title; document.title = branding.nomeExibicao;
    return () => { for (const name of Object.keys(values)) root.style.removeProperty(name); document.title = title; };
  }, [branding.nomeExibicao, branding.corPrimaria, branding.corSecundaria]);
  useEffect(() => {
    if (!favicon) return;
    const previous = Array.from(document.querySelectorAll<HTMLLinkElement>('link[rel~="icon"]'));
    previous.forEach(link => link.remove());
    const link = document.createElement('link'); link.rel = 'icon'; link.href = favicon; document.head.append(link);
    return () => { link.remove(); previous.forEach(item => document.head.append(item)); };
  }, [favicon]);
  return <BrandingContext.Provider value={{ branding, logo }}>{children}</BrandingContext.Provider>;
}
