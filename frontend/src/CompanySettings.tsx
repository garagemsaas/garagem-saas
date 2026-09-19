import { useState } from 'react';
import type { Empresa } from './branding-context';
import { api } from './api';

export default function CompanySettings({ empresa, update }: { empresa: Empresa; update: (value: Empresa) => void }) {
  const [draft, setDraft] = useState(empresa.branding);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  async function perform(action: () => Promise<Empresa>) {
    setBusy(true); setMessage('');
    try { const result = await action(); update(result); setDraft(result.branding); setMessage('Identidade da empresa atualizada.'); }
    catch (e) { setMessage(e instanceof Error ? e.message : 'Não foi possível salvar.'); }
    finally { setBusy(false); }
  }
  return <section className="company-settings">
    <p>{empresa.branding.nomeEmpresarial} · {empresa.status} · {empresa.modulos.join(' + ')}</p>
    <p>Módulos e situação são administrados pela plataforma. Entre em contato com o responsável pelo seu contrato para alterações.</p>
    <form onSubmit={event => { event.preventDefault(); void perform(() => api<Empresa>('/empresa', 'PUT', {
      nomeExibicao: draft.nomeExibicao, telefone: draft.telefone, email: draft.email, contato: draft.contato,
      corPrimaria: draft.corPrimaria, corSecundaria: draft.corSecundaria, revisao: draft.revisao,
    })); }}>
      <fieldset disabled={busy}>
        <label>Nome de exibição<input required maxLength={160} value={draft.nomeExibicao} onChange={e => setDraft({ ...draft, nomeExibicao: e.target.value })} /></label>
        <label>Telefone<input maxLength={40} value={draft.telefone ?? ''} onChange={e => setDraft({ ...draft, telefone: e.target.value })} /></label>
        <label>E-mail<input type="email" maxLength={254} value={draft.email ?? ''} onChange={e => setDraft({ ...draft, email: e.target.value })} /></label>
        <label>Contato e endereço<textarea maxLength={500} value={draft.contato ?? ''} onChange={e => setDraft({ ...draft, contato: e.target.value })} /></label>
        <label>Cor primária<input type="color" value={draft.corPrimaria} onChange={e => setDraft({ ...draft, corPrimaria: e.target.value })} /></label>
        <label>Cor secundária<input type="color" value={draft.corSecundaria} onChange={e => setDraft({ ...draft, corSecundaria: e.target.value })} /></label>
        <button className="primary" type="submit">Salvar identidade</button>
      </fieldset>
    </form>
    <p>Logo e favicon: PNG ou JPEG, até 2 MB e 4 megapixels.</p>
    {(['logo', 'favicon'] as const).map(tipo => <label key={tipo}>{tipo === 'logo' ? 'Logo' : 'Favicon'}<input type="file" accept="image/png,image/jpeg" disabled={busy}
      onChange={e => {
        const file = e.target.files?.[0]; e.target.value = ''; if (!file) return;
        if (file.size > 2097152) { setMessage('A imagem deve ter até 2 MB.'); return; }
        const form = new FormData(); form.set('arquivo', file); form.set('revisao', String(empresa.branding.revisao));
        void perform(() => api<Empresa>(`/empresa/imagens/${tipo}`, 'POST', form));
      }} /></label>)}
    {message && <p role="status">{message}</p>}
  </section>;
}
