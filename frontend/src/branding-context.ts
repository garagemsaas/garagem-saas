import { createContext, useContext, useEffect, useState } from 'react';
import { api } from './api';
export interface Branding {
  nomeEmpresarial: string; nomeExibicao: string; telefone: string | null;
  email: string | null; contato: string | null; corPrimaria: string; corSecundaria: string;
  logoId: string | null; faviconId: string | null; revisao: number;
}
/** Conteúdo do site público. Só o OWNER edita, e publicar é uma decisão à parte da identidade. */
export interface Site {
  frase: string | null; sobre: string | null; servicos: string | null;
  endereco: string | null; horario: string | null; whatsapp: string | null;
  instagram: string | null; publicado: boolean; revisao: number;
}
export interface Empresa {
  branding: Branding; modulos: ('OFICINA' | 'REVENDA')[]; status: 'ATIVA' | 'INATIVA';
  site: Site; capaId: string | null; slug: string;
}
export const BrandingContext = createContext<{ branding?: Branding; logo?: string }>({});
export function useBranding() { return useContext(BrandingContext); }
export function useEmpresa(sessionId?: string) {
  const [state, setState] = useState<{ key: string; value: Empresa }>();
  const [error, setError] = useState('');
  const [attempt, retry] = useState(0);
  useEffect(() => {
    if (!sessionId) {
      // oxlint-disable-next-line react/set-state-in-effect -- clear tenant data when the session ends
      setState(undefined); setError(''); return;
    }
    let active = true;
    api<Empresa>('/empresa').then(value => { if (active) { setState({ key: sessionId, value }); setError(''); } })
      .catch(e => { if (active) setError(e.message); });
    return () => { active = false; };
  }, [sessionId, attempt]);
  return { empresa: sessionId && state?.key === sessionId ? state.value : undefined, error,
    retry: () => retry(n => n + 1), update: (value: Empresa) => { if (sessionId) setState({ key: sessionId, value }); } };
}
