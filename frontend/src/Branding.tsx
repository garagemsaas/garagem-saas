import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { photoBlob } from './api';
import { BrandingContext } from './branding-context';
import type { Branding } from './branding-context';

function useImage(path?: string) {
  const [state, setState] = useState<{ path: string; url: string }>();
  useEffect(() => {
    if (!path) return;
    let active = true; let url: string | undefined;
    photoBlob(path).then(blob => {
      if (active) { url = URL.createObjectURL(blob); setState({ path, url }); }
    }).catch(() => { /* Nome da empresa continua visível se a imagem estiver indisponível. */ });
    return () => { active = false; if (url) URL.revokeObjectURL(url); };
  }, [path]);
  return state?.path === path ? state?.url : undefined;
}
function foreground(hex: string) {
  const rgb = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16) / 255)
    .map(c => c <= .04045 ? c / 12.92 : ((c + .055) / 1.055) ** 2.4);
  return rgb[0] * .2126 + rgb[1] * .7152 + rgb[2] * .0722 > .179 ? '#111111' : '#ffffff';
}
export function BrandingProvider({ branding, base = '/empresa', children }: { branding: Branding; base?: string; children: ReactNode }) {
  const logo = useImage(branding.logoId ? `${base}/imagens/logo/${branding.logoId}` : undefined);
  const favicon = useImage(branding.faviconId ? `${base}/imagens/favicon/${branding.faviconId}` : undefined);
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
