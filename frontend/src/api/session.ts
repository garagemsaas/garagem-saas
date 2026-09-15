import type { Sessao } from "./types";

const accessTokenKey = "garagem.access-token";
let currentSession: Sessao | null = null;

export function setSession(session: Sessao): void {
  currentSession = session;
  sessionStorage.setItem(accessTokenKey, session.accessToken);
}

export function getSession(): Sessao | null {
  return currentSession;
}

export function getAccessToken(): string | null {
  return currentSession?.accessToken ?? sessionStorage.getItem(accessTokenKey);
}

export function clearSession(): void {
  currentSession = null;
  sessionStorage.removeItem(accessTokenKey);
}
