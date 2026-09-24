import { useState } from 'react';
import { api } from './api';
import { Form } from './forms';
import { Drawer, Field } from './ui';
import { Collection } from './revenda/shared';
import { roles, val } from './model';
import type { User } from './model';
export default function TeamPage({ userId, dealer = false }: { userId: string; dealer?: boolean }) {
  const [busca, setBusca] = useState(''); const [situacao, setSituacao] = useState('');
  const [editing, setEditing] = useState<User | 'novo'>(); const [changing, setChanging] = useState<User>(); const [version, setVersion] = useState(0);
  const close = () => { setEditing(undefined); setChanging(undefined); }; const done = () => { close(); setVersion(n => n + 1); };
  const item = editing && editing !== 'novo' ? editing : undefined;
  return <section><div className="page-heading"><div><h1>Usuários</h1><p>Escolha quem pode acessar a empresa e o que cada pessoa pode fazer.</p></div><button className="primary" onClick={() => setEditing('novo')}>Adicionar usuário</button></div>
    <p>Proprietário: administra a equipe e a operação. Atendimento: cuida dos clientes e das operações comerciais.{!dealer && ' Mecânico: consulta atendimentos e registra o trabalho técnico.'}</p>
    <div className="dealer-filters"><Field label="Buscar pessoa"><input type="search" value={busca} maxLength={160} onChange={e => setBusca(e.target.value)} /></Field><Field label="Acesso"><select value={situacao} onChange={e => setSituacao(e.target.value)}><option value="">Todos</option><option value="true">Ativos</option><option value="false">Inativos</option></select></Field></div>
    <Collection<User> key={`${version}-${busca}-${situacao}`} path={`/usuarios?ordenacao=nome,asc&busca=${encodeURIComponent(busca)}${situacao ? `&ativo=${situacao}` : ''}`} render={u => <><h2>{u.nome}</h2><p>{u.email}</p><p>{roles[u.papel]} · {u.ativo ? 'Ativo' : 'Inativo'}</p><div className="dealer-actions"><button onClick={() => setEditing(u)}>Editar usuário</button>{u.id !== userId && <button onClick={() => setChanging(u)}>{u.ativo ? 'Desativar acesso' : 'Ativar acesso'}</button>}</div></>} />
    {editing && <Drawer title={item ? 'Editar usuário' : 'Adicionar usuário'} close={close}><Form close={close} save={async f => {
      await api(`/usuarios${item ? `/${item.id}` : ''}`, item ? 'PUT' : 'POST', { nome: val(f, 'nome'), email: val(f, 'email'), papel: val(f, 'papel'), senha: val(f, 'senha') || null }); done();
    }}><Field label="Nome"><input name="nome" required maxLength={160} defaultValue={item?.nome} autoComplete="name" /></Field><Field label="E-mail"><input name="email" type="email" required maxLength={254} defaultValue={item?.email} autoComplete="email" /></Field>
      <Field label={item ? 'Nova senha (deixe em branco para manter)' : 'Senha inicial'}><input name="senha" type="password" minLength={12} maxLength={72} required={!item} autoComplete="new-password" /></Field>
      <Field label="Perfil de acesso"><select name="papel" defaultValue={item?.papel ?? 'ATENDENTE'}>{Object.entries(roles).filter(([key]) => (!dealer || key !== 'MECANICO' || item?.papel === key) && (item?.id !== userId || key === item.papel)).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></Field>
    </Form></Drawer>}
    {changing && <Drawer title={changing.ativo ? 'Desativar acesso' : 'Ativar acesso'} close={close}><Form close={close} confirmation={`Confirmar alteração de acesso de ${changing.nome}?`} save={async () => { await api(`/usuarios/${changing.id}/situacao`, 'PUT', { ativo: !changing.ativo }); done(); }}><p>{changing.ativo ? 'A pessoa perderá o acesso imediatamente. Seus registros serão preservados.' : 'A pessoa poderá entrar com o perfil já cadastrado.'}</p></Form></Drawer>}
  </section>;
}
