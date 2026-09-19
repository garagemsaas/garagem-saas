import { useState } from 'react';
import { api } from '../api';
import { Form } from '../forms';
import { Field } from '../ui';
import PrivatePhoto from '../PrivatePhoto';
import { Collection } from './shared';
export default function Photos({ context, id }: { context: 'ESTOQUE' | 'AVALIACAO'; id: string }) {
  const [version, setVersion] = useState(0); const [adding, setAdding] = useState(false); const path = `/revenda/fotos/${context}/${id}`;
  return <section><h3>Fotos privadas</h3><button onClick={() => setAdding(!adding)}>Adicionar foto</button>{adding && <Form close={() => setAdding(false)} save={async f => { const body = new FormData(); body.set('arquivo', f.get('arquivo')!); body.set('finalidade', String(f.get('finalidade'))); body.set('descricao', String(f.get('descricao') || '')); await api(path, 'POST', body); setAdding(false); setVersion(n => n + 1); }}>
    <Field label="Foto PNG ou JPEG *"><input name="arquivo" type="file" accept="image/png,image/jpeg" required /></Field><p>Até 10 MB e 20 megapixels.</p>
    <Field label="Finalidade"><select name="finalidade">{(context === 'AVALIACAO' ? ['AVALIACAO'] : ['ESTOQUE', 'PREPARACAO']).map(s => <option key={s}>{s}</option>)}</select></Field><Field label="Descrição"><input name="descricao" maxLength={500} /></Field>
  </Form>}
    <Collection<{ id: string; descricao: string; finalidade: string }> key={version} path={path} render={f => <><PrivatePhoto path={`${path}/${f.id}/conteudo`} alt={f.descricao || `Foto de ${f.finalidade}`} /><p>{f.descricao || f.finalidade}</p></>} />
  </section>;
}
