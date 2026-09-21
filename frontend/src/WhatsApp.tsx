import { useState } from 'react';
import { MessageCircle } from 'lucide-react';
import { Drawer, Field } from './ui';

/**
 * Abrir uma conversa no WhatsApp, com o usuário conferindo antes.
 *
 * <p>Abrir a conversa não é enviar mensagem: quem escreve e envia é a pessoa, no aplicativo dela.
 * Por isso nada aqui grava contato, histórico ou "enviado" — registrar o que não se sabe se
 * aconteceu produziria um histórico que mente. O registro do contato continua sendo uma ação
 * explícita, onde ela já existia.
 *
 * <p>O envio automático pela API oficial, quando existir, será do servidor: token de provedor não
 * tem por que passar pelo navegador.
 */
export function WhatsApp({ telefone, mensagem, rotulo = 'Chamar no WhatsApp', titulo = 'Chamar no WhatsApp' }: {
  telefone?: string | null;
  mensagem: string;
  rotulo?: string;
  titulo?: string;
}) {
  const [aberto, setAberto] = useState(false);
  const [numero, setNumero] = useState(digitos(telefone ?? ''));
  const [texto, setTexto] = useState(mensagem);
  const valido = numero.length >= 10 && numero.length <= 15;
  return <>
    <button type="button" className="whatsapp-action" onClick={() => setAberto(true)}>
      <MessageCircle size={16} aria-hidden="true" /> {rotulo}
    </button>
    {aberto && <Drawer title={titulo} close={() => setAberto(false)}>
      <p>Confira o número e o texto. A conversa abre no WhatsApp e a mensagem é enviada por você — o sistema não envia nem registra nada por conta própria.</p>
      <Field label="Telefone" hint="Com DDI e DDD, somente números.">
        <input inputMode="numeric" value={numero} maxLength={15} onChange={e => setNumero(digitos(e.target.value))} />
      </Field>
      <Field label="Mensagem">
        <textarea rows={5} maxLength={1000} value={texto} onChange={e => setTexto(e.target.value)} />
      </Field>
      {!valido && <p role="alert">Informe o número com DDI e DDD para abrir a conversa.</p>}
      <div className="drawer-actions">
        <button type="button" onClick={() => setAberto(false)}>Cancelar</button>
        <button type="button" className="primary" disabled={!valido} onClick={() => {
          window.open(`https://wa.me/${numero}?text=${encodeURIComponent(texto)}`, '_blank', 'noopener,noreferrer');
          setAberto(false);
        }}>Abrir conversa</button>
      </div>
    </Drawer>}
  </>;
}

/** O wa.me não aceita máscara: parênteses e traços quebram o link em silêncio. */
function digitos(valor: string) { return valor.replace(/\D/g, '').slice(0, 15); }
