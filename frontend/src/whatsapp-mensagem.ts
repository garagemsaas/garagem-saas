/**
 * Texto inicial de uma conversa de WhatsApp. Curto de propósito: quem fala com o cliente ajusta
 * antes de abrir, e mensagem longa pronta costuma soar automática justamente quando não deveria.
 */
export function convite(empresa: string, assunto: string) {
  return `Olá! Aqui é da ${empresa}. ${assunto}`;
}
