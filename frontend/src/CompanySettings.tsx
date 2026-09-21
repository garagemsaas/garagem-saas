import { useState } from 'react';
import type { Empresa, Site } from './branding-context';
import { api } from './api';

export default function CompanySettings({ empresa, update }: { empresa: Empresa; update: (value: Empresa) => void }) {
  const [draft, setDraft] = useState(empresa.branding);
  // Frontend e API publicam separados: uma versão do site pode chegar ao navegador antes da API
  // que devolve este bloco. Sem o padrão, a tela inteira de configurações viraria tela branca.
  const vazio: Site = { frase: null, sobre: null, servicos: null, endereco: null, horario: null, whatsapp: null, instagram: null, publicado: false, revisao: empresa.branding.revisao };
  const [site, setSite] = useState<Site>(empresa.site ?? vazio);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  async function perform(action: () => Promise<Empresa>) {
    setBusy(true); setMessage('');
    try { const result = await action(); update(result); setDraft(result.branding); setSite(result.site ?? vazio); setMessage('Alterações salvas.'); }
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
    {(['logo', 'favicon', 'capa'] as const).map(tipo => <label key={tipo}>{tipo === 'logo' ? 'Logo' : tipo === 'favicon' ? 'Favicon' : 'Imagem principal do site'}<input type="file" accept="image/png,image/jpeg" disabled={busy}
      onChange={e => {
        const file = e.target.files?.[0]; e.target.value = ''; if (!file) return;
        if (file.size > 2097152) { setMessage('A imagem deve ter até 2 MB.'); return; }
        const form = new FormData(); form.set('arquivo', file); form.set('revisao', String(empresa.branding.revisao));
        void perform(() => api<Empresa>(`/empresa/imagens/${tipo}`, 'POST', form));
      }} /></label>)}
    <h3>Site da empresa</h3>
    <p>Endereço público: <code>/site/{empresa.slug ?? ''}</code>. Publicar exige frase, texto, endereço, horário e telefone preenchidos.</p>
    <form onSubmit={event => { event.preventDefault(); void perform(() => api<Empresa>('/empresa/site', 'PUT', { ...site, revisao: empresa.branding.revisao })); }}>
      <fieldset disabled={busy}>
        <label>Frase curta<input maxLength={160} value={site.frase ?? ''} onChange={e => setSite({ ...site, frase: e.target.value })} /></label>
        <label>Sobre a empresa<textarea maxLength={2000} rows={5} value={site.sobre ?? ''} onChange={e => setSite({ ...site, sobre: e.target.value })} /></label>
        <label>Serviços, um por linha<textarea maxLength={1000} rows={6} value={site.servicos ?? ''} onChange={e => setSite({ ...site, servicos: e.target.value })} /></label>
        <label>Endereço<input maxLength={300} value={site.endereco ?? ''} onChange={e => setSite({ ...site, endereco: e.target.value })} /></label>
        <label>Horário de atendimento<input maxLength={300} value={site.horario ?? ''} onChange={e => setSite({ ...site, horario: e.target.value })} /></label>
        <label>WhatsApp, só números com DDI e DDD<input inputMode="numeric" pattern="[0-9]{10,15}" maxLength={15} value={site.whatsapp ?? ''} onChange={e => setSite({ ...site, whatsapp: e.target.value.replace(/\D/g, '') })} /></label>
        <label>Instagram, sem o arroba<input maxLength={30} pattern="[A-Za-z0-9._]{1,30}" value={site.instagram ?? ''} onChange={e => setSite({ ...site, instagram: e.target.value })} /></label>
        <label className="company-switch"><input type="checkbox" checked={site.publicado} onChange={e => setSite({ ...site, publicado: e.target.checked })} /> Site visível na internet</label>
        <button className="primary" type="submit">Salvar site</button>
      </fieldset>
    </form>
    {message && <p role="status">{message}</p>}
  </section>;
}
