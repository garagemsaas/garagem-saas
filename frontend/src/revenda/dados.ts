import { useEffect, useState } from 'react';
import { api } from '../api';
import { val } from '../model';

/**
 * O que não é componente mora aqui. Misturado ao `shared.tsx` isso quebrava o recarregamento rápido
 * do Vite: um arquivo que exporta componentes e mais alguma coisa perde a preservação de estado a
 * cada salvamento, e o lint avisava em cinco pontos.
 */

/** Leitura de um recurso da API, com recarga explícita. Ignora resposta de caminho já trocado. */
export function useResource<T>(path: string) {
  const [state, setState] = useState<{ path: string; data?: T; error?: string }>({ path: '' });
  const [attempt, setAttempt] = useState(0);
  useEffect(() => { let active = true;
    api<T>(path).then(data => { if (active) setState({ path, data }); }).catch(e => { if (active) setState({ path, error: e.message }); });
    return () => { active = false; };
  }, [path, attempt]);
  return { data: state.path === path ? state.data : undefined, error: state.path === path ? state.error : undefined,
    reload: () => { setState({ path: '' }); setAttempt(n => n + 1); } };
}

/** Data de hoje no formato aceito por `input[type=date]`, no fuso de quem opera. */
export const today = () => new Date().toLocaleDateString('en-CA');
/** Amanhã, no formato de `input[type=datetime-local]`: validade padrão de reserva e proposta. */
export const future = () => { const d = new Date(Date.now() + 86400000); d.setMinutes(d.getMinutes() - d.getTimezoneOffset()); return d.toISOString().slice(0, 16); };
/** Converte o campo local de data e hora para o instante em UTC que a API espera. */
export const instant = (f: FormData, name = 'validade') => new Date(val(f, name)).toISOString();
