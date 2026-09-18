import { useEffect, useState } from 'react';
import { currentSession } from './api';
import type { Session } from './api';

/**
 * Estados que o App mantinha soltos entre outros vinte e poucos `useState`. Cada hook aqui cuida de
 * um assunto só, com o próprio efeito ao lado do estado que ele governa — antes era preciso ler o
 * componente inteiro para descobrir quem mexia em quê.
 *
 * Nada de biblioteca de estado: o problema não era falta de ferramenta, era responsabilidade
 * amontoada em um componente.
 */

/** Sessão autenticada da oficina, incluindo renovação e expiração vindas da camada de API. */
export type SessaoAtiva = Session & { role: Session['papel']; oficina: string };

export function useSession(aoExpirar: () => void) {
  const [session, setSession] = useState<SessaoAtiva | null>(null);
  useEffect(() => {
    const expired = () => {
      setSession(null);
      aoExpirar();
    };
    // A renovação preserva a oficina digitada no login: ela não volta no refresh do token.
    const renewed = () => {
      const value = currentSession();
      if (value)
        setSession(previous => (previous ? { ...value, role: value.papel, oficina: previous.oficina } : null));
    };
    window.addEventListener('session-expired', expired);
    window.addEventListener('session-updated', renewed);
    return () => {
      window.removeEventListener('session-expired', expired);
      window.removeEventListener('session-updated', renewed);
    };
  }, [aoExpirar]);
  return [session, setSession] as const;
}

/** Data corrente, atualizada de minuto em minuto para os cálculos de atraso não congelarem. */
export function useToday() {
  const [today, setToday] = useState(() => new Date());
  useEffect(() => {
    const timer = window.setInterval(() => setToday(new Date()), 60_000);
    return () => window.clearInterval(timer);
  }, []);
  return today;
}

/** Aviso temporário de sucesso. Some sozinho; quem chama só precisa definir o texto. */
export function useToast(duracaoMs = 4500) {
  const [toast, setToast] = useState('');
  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(''), duracaoMs);
    return () => clearTimeout(timer);
  }, [toast, duracaoMs]);
  return [toast, setToast] as const;
}
